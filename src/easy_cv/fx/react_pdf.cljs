(ns easy-cv.fx.react-pdf
  "Re-frame effect handler rendering CanonicalCV via @react-pdf/renderer."
  (:require ["@react-pdf/renderer"
             :refer [Document Page StyleSheet Text View pdf]]
            ["react" :as react]
            [easy-cv.core.latex-wasm :as lw]
            [easy-cv.core.react-pdf :as rp]
            [re-frame.core :as rf]))

(defn- make-styles
  "Build @react-pdf/renderer StyleSheet from ReactPdfDoc styling values."
  [{:pdf/keys [accent-hex font-family]}]
  (.create StyleSheet
           #js {"page" #js {"padding" 36 "fontFamily" font-family
                            "fontSize" 10 "color" "#0f172a"}
                "header" #js {"marginBottom" 12 "paddingBottom" 8
                              "borderBottomWidth" 1.5
                              "borderBottomColor" accent-hex}
                "name" #js {"fontSize" 18 "fontWeight" "bold"
                            "color" accent-hex}
                "contact" #js {"fontSize" 9 "marginTop" 3 "color" "#475569"}
                "summary" #js {"marginBottom" 10 "lineHeight" 1.4}
                "secHead" #js {"fontSize" 10 "fontWeight" "bold"
                               "color" accent-hex "marginTop" 10
                               "marginBottom" 4}
                "item" #js {"marginBottom" 6}
                "row" #js {"flexDirection" "row"
                           "justifyContent" "space-between"}
                "title" #js {"fontWeight" "bold"}
                "meta" #js {"fontSize" 9 "color" "#64748b"}
                "detail" #js {"marginTop" 2 "color" "#334155"}}))

(defn- el
  "Create a React element with JS props and variadic children."
  [comp-type props & children]
  (apply react/createElement comp-type props children))

(defn- render-item-el
  "Render one PdfItemLine map into a @react-pdf View element."
  [^js styles idx {:pdf/keys [title subtitle meta detail]}]
  (let [head (if subtitle (str title " - " subtitle) title)]
    (el View #js {"key" (str "it-" idx) "style" (.-item styles)}
        (el View #js {"style" (.-row styles)}
            (el Text #js {"style" (.-title styles)} head)
            (when meta (el Text #js {"style" (.-meta styles)} meta)))
        (when detail
          (el Text #js {"style" (.-detail styles)} detail)))))

(defn- render-section-el
  "Render one PdfSection map into a @react-pdf View block."
  [^js styles {:pdf/keys [section-id heading items]}]
  (el View #js {"key" (name section-id)}
      (el Text #js {"style" (.-secHead styles)} heading)
      (clj->js
       (map-indexed #(render-item-el styles %1 %2) items))))

(defn build-pdf-element
  "Construct root @react-pdf <Document> React element from ReactPdfDoc."
  [{:pdf/keys [page-size person-name contact-line summary sections] :as doc}]
  (let [^js styles (make-styles doc)
        sec-els (clj->js (mapv #(render-section-el styles %) sections))]
    (el Document #js {"title" person-name}
        (el Page #js {"size" page-size "style" (.-page styles)}
            (el View #js {"style" (.-header styles)}
                (el Text #js {"style" (.-name styles)} person-name)
                (when (seq contact-line)
                  (el Text #js {"style" (.-contact styles)} contact-line)))
            (when (seq summary)
              (el Text #js {"style" (.-summary styles)} summary))
            sec-els))))

(defn- replace-pdf-url!
  "Revoke any previous object URL and mint a fresh PDF blob URL."
  [^js blob prev-url]
  (when (and prev-url (exists? js/URL))
    (js/URL.revokeObjectURL prev-url))
  (when (and blob (exists? js/URL))
    (js/URL.createObjectURL blob)))

(defn- blob->pdf-result
  "Read Uint8Array from PDF Blob and wrap validated result map."
  [^js blob prev-url]
  (-> (.arrayBuffer blob)
      (.then (fn [buf]
               (let [u8 (js/Uint8Array. buf)
                     valid? (lw/pdf-header?
                             (js/Array.from (.slice u8 0 5)))]
                 {:outcome (lw/summarize-result
                            {:success valid?
                             :pdf-bytes (.-length u8)
                             :log "React-PDF compiled"})
                  :pdf-url (when valid?
                             (replace-pdf-url! blob prev-url))
                  :pdf-uint8 (when valid? u8)})))))

(defn generate-react-pdf!
  "Generate PDF Blob + Uint8Array from CanonicalCV via @react-pdf/renderer."
  [{:keys [canonical style-cfg prev-url]}]
  (if-let [doc (rp/canonical->pdf-model canonical style-cfg)]
    (let [^js inst (pdf (build-pdf-element doc))]
      (-> (.toBlob inst)
          (.then #(blob->pdf-result % prev-url))))
    (js/Promise.reject (js/Error. "No CanonicalCV available"))))

(defn- dispatch-react-pdf-fx!
  "Effect handler for :react-pdf/compile dispatches."
  [{:keys [on-success on-error] :as req}]
  (-> (generate-react-pdf! req)
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

(rf/reg-fx :react-pdf/compile dispatch-react-pdf-fx!)
