(ns easy-cv.core.latex-wasm
  "Pure .cljc schemas and payload helpers for BusyTeX LaTeX WASM compilation."
  (:require [clojure.string :as str]
            [malli.core :as m]))

(def EngineKey
  "Malli schema for supported BusyTeX TeX engines."
  [:enum :pdflatex :xelatex :lualatex])

(def engine-labels
  "Ordered vector of engine choices for UI selectors."
  [{:engine/key :pdflatex :engine/label "PdfLaTeX"}
   {:engine/key :xelatex :engine/label "XeLaTeX"}
   {:engine/key :lualatex :engine/label "LuaLaTeX"}])

(def AdditionalFile
  "Malli schema for extra virtual filesystem files passed to BusyTeX."
  [:map
   [:path :string]
   [:content :string]])

(def CompileRequest
  "Malli schema for an incoming LaTeX compilation request."
  [:map
   [:tex :string]
   [:engine {:optional true} EngineKey]
   [:bib {:optional true} [:maybe :string]]
   [:bibtex? {:optional true} :boolean]
   [:main-tex-path {:optional true} :string]])

(def BusyTexPayload
  "Malli schema for normalized arguments passed to BusyTeX runner."
  [:map
   [:driver EngineKey]
   [:input :string]
   [:main-tex-path :string]
   [:bibtex :boolean]
   [:additional-files [:vector AdditionalFile]]])

(def CompileOutcome
  "Malli schema for normalized compilation status reported back to Re-frame."
  [:map
   [:success :boolean]
   [:exit-code :int]
   [:log :string]
   [:pdf-bytes {:optional true} :int]
   [:error {:optional true} [:maybe :string]]])

(defn initial-state
  "Pure initial Re-frame state map stored at [:render :latex-wasm]."
  []
  {:engine :pdflatex
   :latex/status :idle
   :log nil
   :error nil
   :pdf-url nil
   :pdf-bytes nil})

(defn normalize-engine
  "Normalize engine keyword to a valid EngineKey, defaulting to :pdflatex."
  [engine]
  (if (m/validate EngineKey engine) engine :pdflatex))

(defn has-bibliography?
  "Return true when TeX source invokes \\bibliography or \\addbibresource."
  [tex]
  (boolean
   (re-find #"\\(bibliography|addbibresource)\{" (or tex ""))))

(defn- bib-additional-files
  "Attach references.bib virtual file when non-blank BibTeX text exists."
  [bib]
  (if (str/blank? bib)
    []
    [{:path "references.bib" :content bib}]))

(defn build-compile-payload
  "Build a BusyTexPayload map from {:tex :bib :engine :main-tex-path}."
  [req]
  (let [{:keys [tex bib engine bibtex? main-tex-path]} req
        clean-tex (str (or tex ""))
        files (bib-additional-files bib)
        run-bib? (boolean (or bibtex?
                              (and (seq files)
                                   (has-bibliography? clean-tex))))]
    {:driver (normalize-engine engine)
     :input clean-tex
     :main-tex-path (or main-tex-path "main.tex")
     :bibtex run-bib?
     :additional-files files}))

(def ^:private pdf-magic
  "Leading ASCII byte values for %PDF- header validation."
  [37 80 68 70 45])

(defn pdf-header?
  "Return true if sequential/indexed byte collection begins with %PDF-."
  [bs]
  (and (>= (count bs) 5)
       (= pdf-magic (mapv #(bit-and (int %) 0xff) (take 5 bs)))))

(defn summarize-result
  "Normalize raw compile outcome map into a CompileOutcome value."
  [{:keys [success exit-code log pdf-bytes error]}]
  (cond-> {:success (boolean success)
           :exit-code (int (or exit-code (if success 0 1)))
           :log (str (or log ""))}
    (some? pdf-bytes) (assoc :pdf-bytes (int pdf-bytes))
    (some? error) (assoc :error (str error))))
