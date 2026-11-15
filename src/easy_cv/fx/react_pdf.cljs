(ns easy-cv.fx.react-pdf
  "Re-frame effect handler rendering CanonicalCV via @react-pdf/renderer."
  (:require ["@react-pdf/renderer"
             :refer [Document Page StyleSheet Text View pdf]]
            ["react" :as react]
            [easy-cv.core.latex-wasm :as lw]
            [easy-cv.core.react-pdf :as rp]
            [re-frame.core :as rf]))

(defn- chip-radius
  [chip-style]
  (case chip-style
    :pill 10
    :tonal 6
    :outlined 4
    :squared 0
    :zenburn 3
    10))

(defn- header-style-map
  [{:pdf/keys [header-bg accent-hex]}]
  (if header-bg
    #js {"marginBottom" 12 "padding" 14 "borderRadius" 6
         "backgroundColor" header-bg}
    #js {"marginBottom" 12 "paddingBottom" 8
         "borderBottomWidth" 1.5 "borderBottomColor" accent-hex}))

(defn- chip-style-map
  [{:pdf/keys [chip-bg chip-border chip-style]}]
  #js {"backgroundColor" (or chip-bg "#f1f5f9")
       "borderWidth" 0.75
       "borderColor" (or chip-border "#cbd5e1")
       "borderRadius" (chip-radius chip-style)
       "paddingHorizontal" 6
       "paddingVertical" 2
       "marginRight" 4
       "marginBottom" 4})

(defn- page-and-header-styles
  [{:pdf/keys [accent-hex header-bg header-fg sheet-bg font-family] :as doc}]
  #js {"page" #js {"padding" 36 "fontFamily" font-family "fontSize" 10
                   "color" "#0f172a" "backgroundColor" (or sheet-bg "#ffffff")}
       "header" (header-style-map doc)
       "name" #js {"fontSize" 18 "fontWeight" "bold"
                   "color" (or header-fg accent-hex)}
       "contact" #js {"fontSize" 9 "marginTop" 3
                      "color" (if header-bg header-fg "#475569")}
       "summary" #js {"marginBottom" 10 "lineHeight" 1.4}
       "secHead" #js {"fontSize" 10 "fontWeight" "bold" "color" accent-hex
                      "marginTop" 10 "marginBottom" 4 "paddingBottom" 2
                      "borderBottomWidth" 0.75 "borderBottomColor" accent-hex}})

(defn- item-and-chip-styles
  [{:pdf/keys [chip-fg] :as doc}]
  #js {"item" #js {"marginBottom" 6}
       "row" #js {"flexDirection" "row" "justifyContent" "space-between"}
       "title" #js {"fontWeight" "bold"}
       "meta" #js {"fontSize" 9 "color" "#64748b"}
       "detail" #js {"marginTop" 2 "color" "#334155"}
       "chipWrap" #js {"flexDirection" "row" "flexWrap" "wrap" "marginTop" 3}
       "chip" (chip-style-map doc)
       "chipText" #js {"fontSize" 8.5 "color" (or chip-fg "#1e293b")}})

(defn- make-styles
  "Build @react-pdf/renderer StyleSheet from ReactPdfDoc styling values."
  [doc]
  (.create StyleSheet
           (js/Object.assign #js {}
                             (page-and-header-styles doc)
                             (item-and-chip-styles doc))))

(defn- el
  "Create a React element with JS props and variadic children."
  [comp-type props & children]
  (apply react/createElement comp-type props children))

(defn- render-chip
  [^js styles key-str label]
  (el View #js {"key" key-str "style" (.-chip styles)}
      (el Text #js {"style" (.-chipText styles)} label)))

(defn- render-item-el
  "Render one PdfItemLine map into a @react-pdf View element."
  [^js styles idx {:pdf/keys [title subtitle meta detail tags]}]
  (let [head (if subtitle (str title " - " subtitle) title)]
    (el View #js {"key" (str "it-" idx) "style" (.-item styles)}
        (el View #js {"style" (.-row styles)}
            (el Text #js {"style" (.-title styles)} head)
            (when meta (el Text #js {"style" (.-meta styles)} meta)))
        (when detail
          (el Text #js {"style" (.-detail styles)} detail))
        (when (seq tags)
          (el View #js {"style" (.-chipWrap styles)}
              (clj->js
               (map-indexed #(render-chip styles (str "t-" idx "-" %1) %2)
                            tags)))))))

(defn- render-skill-chips
  [^js styles items]
  (el View #js {"style" (.-chipWrap styles)}
      (clj->js
       (map-indexed
        (fn [idx {:pdf/keys [title meta]}]
          (render-chip styles (str "sk-" idx)
                       (if meta (str title " · " meta) title)))
        items))))

(defn- render-section-el
  "Render one PdfSection map into a @react-pdf View block."
  [^js styles {:pdf/keys [section-id heading items]}]
  (el View #js {"key" (name section-id)}
      (el Text #js {"style" (.-secHead styles)} heading)
      (if (= section-id :skills)
        (render-skill-chips styles items)
        (clj->js (map-indexed #(render-item-el styles %1 %2) items)))))

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
