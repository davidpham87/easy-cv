(ns easy-cv.fx.latex-wasm
  "Re-frame effect handler wrapping texlyre-busytex for in-browser LaTeX WASM."
  (:require ["texlyre-busytex" :refer [BusyTexRunner LuaLatex PdfLatex XeLatex]]
            [easy-cv.core.latex-wasm :as lw]
            [re-frame.core :as rf]))

(defonce ^:private runner-atom (atom nil))

(def ^:private default-base-path "/core/busytex")

(def ^:private default-preload-packages
  #js ["/core/busytex/texlive-recommended.js"])

(defn- make-runner
  "Instantiate BusyTexRunner configured to load assets under /core/busytex."
  [base-path preload-pkgs]
  (BusyTexRunner.
   #js {"busytexBasePath" (or base-path default-base-path)
        "verbose" false
        "preloadDataPackages" (or preload-pkgs default-preload-packages)}))

(defn ensure-runner!
  "Lazily initialize and cache a Web Worker BusyTexRunner Promise."
  ([] (ensure-runner! default-base-path default-preload-packages))
  ([base-path preload-pkgs]
   (if-let [p @runner-atom]
     p
     (let [^js runner (make-runner base-path preload-pkgs)
           p (-> (.initialize runner true)
                 (.then (fn [_] runner))
                 (.catch (fn [^js err]
                           (reset! runner-atom nil)
                           (throw err))))]
       (reset! runner-atom p)
       p))))

(defn- make-engine
  "Instantiate PdfLatex, XeLatex, or LuaLatex wrapper for driver keyword."
  [runner driver]
  (case driver
    :xelatex (XeLatex. runner)
    :lualatex (LuaLatex. runner)
    (PdfLatex. runner)))

(defn- clj->additional-files
  "Convert Clojure {:path :content} maps into BusyTeX JS file entries."
  [files]
  (clj->js
   (mapv (fn [{:keys [path content]}]
           #js {"path" path "content" content})
         files)))

(defn- replace-pdf-url!
  "Revoke previous blob URL if present and return a fresh PDF blob URL."
  [pdf-bytes prev-url]
  (when (and prev-url (exists? js/URL))
    (js/URL.revokeObjectURL prev-url))
  (when (and pdf-bytes (exists? js/Blob) (exists? js/URL))
    (let [blob (js/Blob. #js [pdf-bytes] #js {"type" "application/pdf"})]
      (js/URL.createObjectURL blob))))

(defn- normalize-js-result
  "Convert BusyTeX JS CompileResult into a CLJS map with valid PDF check."
  [^js res prev-url]
  (let [success? (boolean (.-success res))
        ^js pdf (.-pdf res)
        valid-pdf? (and success? pdf (pos? (.-length pdf))
                        (lw/pdf-header? (js/Array.from (.slice pdf 0 5))))
        pdf-url (when valid-pdf? (replace-pdf-url! pdf prev-url))
        raw-log (or (not-empty (.-log res))
                    (str (js/JSON.stringify (.-logs res))))
        outcome (lw/summarize-result
                 {:success valid-pdf?
                  :exit-code (or (.-exitCode res) (if valid-pdf? 0 1))
                  :log raw-log
                  :pdf-bytes (when pdf (.-length pdf))})]
    {:outcome outcome
     :pdf-url pdf-url
     :pdf-uint8 (when valid-pdf? pdf)}))

(defn compile-latex!
  "Run BusyTeX compilation and resolve with {:outcome :pdf-url :pdf-uint8}."
  [req]
  (let [payload (lw/build-compile-payload req)
        {:keys [driver input main-tex-path bibtex additional-files]} payload
        opts #js {"input" input
                  "mainTexPath" main-tex-path
                  "bibtex" bibtex
                  "rerun" bibtex
                  "additionalFiles" (clj->additional-files additional-files)}]
    (-> (ensure-runner! (:base-path req) (:preload-pkgs req))
        (.then (fn [^js runner]
                 (let [^js eng (make-engine runner driver)]
                   (.compile eng opts))))
        (.then (fn [res]
                 (normalize-js-result res (:prev-url req)))))))

(defn- dispatch-compile-fx!
  "Effect handler for :latex/compile dispatches."
  [{:keys [on-success on-error] :as req}]
  (-> (compile-latex! req)
      (.then (fn [{:keys [outcome] :as res}]
               (if (:success outcome)
                 (when on-success (rf/dispatch (conj on-success res)))
                 (when on-error (rf/dispatch (conj on-error outcome))))))
      (.catch (fn [^js err]
                (when on-error
                  (rf/dispatch
                   (conj on-error
                         (lw/summarize-result
                          {:success false
                           :exit-code 1
                           :log (or (.-message err) (str err))
                           :error (or (.-message err) (str err))}))))))))

(defn terminate-runner!
  "Terminate cached BusyTeX worker instance if active."
  [_]
  (when-let [p @runner-atom]
    (reset! runner-atom nil)
    (.then p (fn [^js runner] (when runner (.terminate runner))))))

(rf/reg-fx :latex/compile dispatch-compile-fx!)
(rf/reg-fx :latex/terminate terminate-runner!)
