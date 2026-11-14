(ns easy-cv.ui.views.preview
  "Style Customizer, live CV preview, and multi-format export view."
  (:require [clojure.string :as str]
            [easy-cv.core.db :as db]
            [easy-cv.core.latex-wasm :as lw]
            [easy-cv.core.render :as render]
            [easy-cv.core.solver :as solver]
            [easy-cv.fx.datascript :as fx-ds]
            [easy-cv.fx.latex-wasm]
            [easy-cv.fx.react-pdf]
            [easy-cv.ui.components.common :as common]
            [easy-cv.ui.components.cv-blocks :as cv-blocks]
            [easy-cv.ui.components.layout :as layout]
            [easy-cv.ui.styles :as styles]
            [easy-cv.ui.theme :as t]
            [re-frame.core :as rf]))

(def default-style
  "Default visual style selection for live CV preview."
  {:design :academic
   :layout :single-col
   :font :sans
   :density :balanced})

(def source-tabs
  "Output format tabs displayed in the source inspector."
  [{:fmt/key :html :fmt/label "HTML"}
   {:fmt/key :latex :fmt/label "LaTeX / Bib"}
   {:fmt/key :markdown :fmt/label "Markdown"}
   {:fmt/key :json-ld :fmt/label "JSON-LD"}
   {:fmt/key :site :fmt/label "Static Site"}])

(defn- cls
  [& parts]
  (str/join " " (remove str/blank? parts)))

(defn- font-preset->cv-font
  [font-key]
  (let [fam (get-in styles/font-presets [font-key :font-family] "Inter")]
    {:heading fam :body fam :mono "JetBrains Mono"}))

(defn apply-style-to-canonical
  "Merges [:render :style] selections onto a solved CanonicalCV."
  [canonical style-cfg]
  (when canonical
    (let [{:keys [design layout font]} (merge default-style style-cfg)]
      (update canonical :cv/style merge
              {:theme design
               :layout layout
               :font (font-preset->cv-font font)}))))

(defn solve-active-cv
  "Purely resolves the active (or first) CVConfig from DataScript db."
  [dval active-cv-id style-cfg]
  (when dval
    (let [cv-id (or active-cv-id (:cv/id (first (db/list-cv-configs dval))))]
      (when cv-id
        (try
          (-> (solver/solve-cv dval cv-id)
              (apply-style-to-canonical style-cfg))
          (catch :default _ nil))))))

(defn render-all-formats
  "Purely renders CanonicalCV into all supported export payloads."
  [canonical]
  (when canonical
    {:html (render/render-cv :html canonical {})
     :latex (render/render-cv :latex canonical {})
     :markdown (render/render-cv :markdown canonical {})
     :json-ld (render/render-cv :json-ld canonical {})
     :site (render/render-cv :site canonical {})}))

(defn- format-source-text
  [outputs tab]
  (let [v (get outputs tab)]
    (cond
      (string? v) v
      (= :latex tab) (str (:tex v) "\n\n% --- BibTeX ---\n" (:bib v))
      (= :site tab)
      (str/join "\n"
                (for [[fname content] (sort-by key v)]
                  (str "=== " fname " (" (count content) " chars) ===")))
      :else "")))

(rf/reg-fx
 :render/print!
 (fn [_]
   (when (exists? js/window)
     (.print js/window))))

(rf/reg-event-db
 ::set-style-opt
 (fn [db [_ opt-key val-kw]]
   (assoc-in db [:render :style opt-key] val-kw)))

(rf/reg-event-db
 ::apply-bundle
 (fn [db [_ bundle-kw]]
   (let [bundle (get styles/style-bundles bundle-kw default-style)]
     (update-in db [:render :style] merge bundle))))

(rf/reg-event-db
 ::set-source-tab
 (fn [db [_ tab]]
   (assoc-in db [:render :source-tab] tab)))

(rf/reg-event-db
 ::set-preview-mode
 (fn [db [_ mode]]
   (assoc-in db [:render :preview-mode] mode)))

(rf/reg-event-fx
 ::download-export
 (fn [_ [_ filename mime-type content]]
   {:io/download-file {:io/content content
                       :io/filename filename
                       :io/mime-type mime-type}}))

(rf/reg-event-fx
 ::export-sql-json
 (fn [_ _]
   {:worker/send {:worker/op :persistence/export-sql-json
                  :worker/payload {:exported-at (.toISOString (js/Date.))}
                  :worker/on-success [::sql-export-ready]
                  :worker/on-error [::sql-export-failed]}}))

(rf/reg-event-fx
 ::sql-export-ready
 (fn [{:keys [db]} [_ payload]]
   {:db (assoc-in db [:render :export-msg] "SQL JSON exported")
    :io/download-file {:io/content (js/JSON.stringify (clj->js payload) nil 2)
                       :io/filename "easy-cv-sql-dump.json"
                       :io/mime-type "application/json"}}))

(rf/reg-event-db
 ::sql-export-failed
 (fn [db [_ err]]
   (assoc-in db [:render :export-msg]
             (str "SQL export unavailable: "
                  (or (:msg/op err) "worker not ready")))))

(rf/reg-event-fx
 ::print-pdf
 (fn [_ _]
   {:render/print! true}))

(rf/reg-event-db
 ::set-latex-engine
 (fn [db [_ engine-kw]]
   (assoc-in db [:render :latex-wasm :engine]
             (lw/normalize-engine engine-kw))))

(rf/reg-event-fx
 ::compile-latex-pdf
 (fn [{:keys [db]} [_ outputs]]
   (let [latex-out (:latex outputs)
         wasm-state (get-in db [:render :latex-wasm])
         engine (lw/normalize-engine (:engine wasm-state))]
     {:db (update-in db [:render :latex-wasm] merge
                     {:engine engine :latex/status :compiling :error nil})
      :latex/compile {:tex (:tex latex-out)
                      :bib (:bib latex-out)
                      :engine engine
                      :prev-url (:pdf-url wasm-state)
                      :on-success [::latex-compile-ok]
                      :on-error [::latex-compile-err]}})))

(rf/reg-event-fx
 ::compile-react-pdf
 (fn [{:keys [db]} [_ canonical style-cfg]]
   (let [prev-url (get-in db [:render :latex-wasm :pdf-url])]
     {:db (update-in db [:render :latex-wasm] merge
                     {:latex/status :compiling :pdf/backend :react-pdf
                      :error nil})
      :react-pdf/compile {:canonical canonical
                          :style-cfg style-cfg
                          :prev-url prev-url
                          :on-success [::latex-compile-ok]
                          :on-error [::latex-compile-err]}})))

(rf/reg-event-db
 ::latex-compile-ok
 (fn [db [_ {:keys [outcome pdf-url pdf-uint8]}]]
   (-> db
       (update-in [:render :latex-wasm] merge
                  {:latex/status :ready
                   :pdf-url pdf-url
                   :pdf-uint8 pdf-uint8
                   :pdf-bytes (:pdf-bytes outcome)
                   :log (:log outcome)
                   :error nil})
       (assoc-in [:render :preview-mode] :pdf))))

(rf/reg-event-db
 ::latex-compile-err
 (fn [db [_ outcome]]
   (update-in db [:render :latex-wasm] merge
              {:latex/status :error
               :log (:log outcome)
               :error (or (:error outcome)
                          (not-empty (:log outcome))
                          (str "Exit " (:exit-code outcome)))})))

(rf/reg-sub
 ::latex-wasm
 (fn [db _]
   (merge (lw/initial-state) (get-in db [:render :latex-wasm]))))

(rf/reg-sub
 ::style-config
 (fn [db _]
   (merge default-style (get-in db [:render :style]))))

(rf/reg-sub ::source-tab
            (fn [db _] (get-in db [:render :source-tab] :html)))
(rf/reg-sub ::preview-mode
            (fn [db _] (get-in db [:render :preview-mode] :sheet)))
(rf/reg-sub ::export-msg (fn [db _] (get-in db [:render :export-msg])))
(rf/reg-sub ::active-cv-id (fn [db _] (get-in db [:solver :active-cv-id])))

(rf/reg-sub
 ::resolved-cv
 :<- [::fx-ds/db]
 :<- [::active-cv-id]
 :<- [::style-config]
 (fn [[dval active-id style-cfg] _]
   (solve-active-cv dval active-id style-cfg)))

(rf/reg-sub
 ::rendered-outputs
 :<- [::resolved-cv]
 (fn [canonical _]
   (render-all-formats canonical)))

(defn- style-select
  [label opt-key current-val options]
  [:div {:class "flex flex-col gap-1"}
   [:label {:class (cls "text-xs font-semibold uppercase" t/text-muted)} label]
   [common/select
    (assoc {:on-change #(rf/dispatch [::set-style-opt
                                      opt-key
                                      (keyword (.. % -target -value))])}
           :value (name current-val))
    (for [k options]
      ^{:key k}
      [:option (assoc {} :value (name k)) (name k)])]])

(defn- style-customizer-panel
  [style-cfg]
  [common/card {:class "p-4 space-y-3"}
   [:h3 {:class "text-sm font-bold"} "Style Customizer"]
   [:div {:class "flex flex-wrap gap-1.5"}
    (for [bk (keys styles/style-bundles)]
      ^{:key bk}
      [common/button
       {:size :xs :on-click #(rf/dispatch [::apply-bundle bk])}
       (name bk)])]
   [style-select "Design" :design (:design style-cfg)
    (keys styles/design-presets)]
   [style-select "Layout" :layout (:layout style-cfg)
    (keys styles/layout-presets)]
   [style-select "Font" :font (:font style-cfg) (keys styles/font-presets)]
   [style-select "Density" :density (:density style-cfg)
    (keys styles/density-presets)]])

(defn- export-specs
  [outputs]
  (let [tex-bib (:latex outputs)]
    [{:export/label ".html" :export/file "cv.html"
      :export/mime "text/html" :export/content (:html outputs)}
     {:export/label ".tex" :export/file "cv.tex"
      :export/mime "application/x-tex" :export/content (:tex tex-bib)}
     {:export/label ".bib" :export/file "cv.bib"
      :export/mime "text/plain" :export/content (:bib tex-bib)}
     {:export/label ".md" :export/file "cv.md"
      :export/mime "text/markdown" :export/content (:markdown outputs)}
     {:export/label ".jsonld" :export/file "cv.jsonld"
      :export/mime "application/ld+json" :export/content (:json-ld outputs)}]))

(defn- latex-compile-status-bar
  [{:keys [pdf-bytes error pdf-uint8] status :latex/status}]
  [:div {:class "space-y-1"}
   (when (= status :ready)
     [:div {:class "flex flex-wrap items-center gap-2"}
      [:span {:data-testid "latex-compile-status"
              :class "text-xs font-medium text-emerald-500"}
       (str "Compiled PDF (" pdf-bytes " bytes)")]
      (when pdf-uint8
        [common/button
         {:size :xs :data-testid "download-compiled-pdf-btn"
          :on-click #(rf/dispatch [::download-export "cv.pdf"
                                   "application/pdf" pdf-uint8])}
         "Download cv.pdf"])])
   (when (= status :error)
     [:p {:data-testid "latex-compile-error"
          :class "text-xs text-rose-500"}
      error])])

(defn- busytex-controls
  [engine outputs busy?]
  [:<>
   [common/select
    (assoc {:data-testid "latex-engine-select"
            :on-change #(rf/dispatch [::set-latex-engine
                                      (keyword (.. % -target -value))])}
           :value (name engine))
    (for [{:engine/keys [key label]} lw/engine-labels]
      ^{:key key}
      [:option (assoc {} :value (name key)) label])]
   [common/button
    {:size :xs :disabled busy? :data-testid "compile-latex-pdf-btn"
     :on-click #(rf/dispatch [::compile-latex-pdf outputs])}
    (if busy? "Compiling..." "Compile PDF (BusyTeX)")]])

(defn- pdf-engine-panel
  [canonical style-cfg outputs latex-state]
  (let [busy? (= :compiling (:latex/status latex-state))]
    [common/card {:class "p-4 space-y-2"}
     [:h3 {:class "text-sm font-bold"} "PDF Build (React-PDF / BusyTeX)"]
     [:div {:class "flex flex-wrap items-center gap-2"}
      [common/button
       {:size :xs :variant :primary :disabled busy?
        :data-testid "compile-react-pdf-btn"
        :on-click #(rf/dispatch [::compile-react-pdf canonical style-cfg])}
       (if busy? "Building..." "Instant PDF (React-PDF)")]
      [busytex-controls (:engine latex-state) outputs busy?]]
     [latex-compile-status-bar latex-state]]))

(defn- export-buttons-panel
  [outputs export-msg]
  [common/card {:class "p-4 space-y-2"}
   [:h3 {:class "text-sm font-bold"} "Export & Print"]
   [:div {:class "flex flex-wrap gap-2"}
    [common/button {:size :xs :variant :primary
                    :on-click #(rf/dispatch [::print-pdf])}
     "Print / PDF"]
    (for [{:export/keys [label file mime content]} (export-specs outputs)]
      ^{:key label}
      [common/button
       {:size :xs
        :on-click #(rf/dispatch [::download-export file mime content])}
       label])
    [common/button
     {:size :xs :on-click #(rf/dispatch [::export-sql-json])}
     "SQL JSON"]]
   (when export-msg
     [:p {:class (cls "text-xs" t/text-muted)} export-msg])])

(defn- live-sheet-preview
  [canonical style-cfg]
  [:div (assoc {} :id "cv-sheet" :class "print:shadow-none")
   [cv-blocks/cv-preview
    canonical
    {:density (:density style-cfg)
     :class-overrides {:sheet "print:shadow-none print:border-0"}}]])

(defn- compiled-pdf-pane
  [{:keys [pdf-url log error] status :latex/status}]
  [:div {:class "space-y-3" :data-testid "compiled-pdf-pane"}
   (cond
     pdf-url
     [:iframe {:src pdf-url :title "Compiled LaTeX PDF"
               :data-testid "compiled-pdf-frame"
               :class "w-full h-[640px] rounded border"}]
     (= status :compiling)
     [common/card {} "Compiling LaTeX document via BusyTeX WASM..."]
     error
     [common/card {} (str "Compilation failed: " error)]
     :else
     [common/card {} "Click Compile PDF (BusyTeX) to build the PDF."])
   (when (seq log)
     [:details {:class "text-xs"}
      [:summary {:class "cursor-pointer font-semibold"} "LaTeX Log"]
      [common/pre-block {:content log}]])])

(defn- preview-toolbar
  [mode tab]
  [:div {:class (cls "flex flex-wrap items-center justify-between gap-2"
                     "pb-3 mb-4 border-b print:hidden" t/border-subtle)}
   [:div {:class "flex gap-2"}
    [common/button {:size :sm :variant (when (= mode :sheet) :primary)
                    :on-click #(rf/dispatch [::set-preview-mode :sheet])}
     "Live Sheet"]
    [common/button {:size :sm :variant (when (= mode :source) :primary)
                    :on-click #(rf/dispatch [::set-preview-mode :source])}
     "Source Inspector"]
    [common/button {:size :sm :variant (when (= mode :pdf) :primary)
                    :on-click #(rf/dispatch [::set-preview-mode :pdf])}
     "Compiled PDF"]]
   (when (= mode :source)
     [:div {:class "flex flex-wrap gap-1.5"}
      (for [{:fmt/keys [key label]} source-tabs]
        ^{:key key}
        [common/button {:size :xs :variant (when (= tab key) :primary)
                        :on-click #(rf/dispatch [::set-source-tab key])}
         label])])])

(defn- preview-pane
  [canonical style-cfg outputs mode tab latex-state]
  [:div {:class "p-4 overflow-auto h-full"}
   [preview-toolbar mode tab]
   (if canonical
     (case mode
       :source [common/pre-block {:content (format-source-text outputs tab)}]
       :pdf [compiled-pdf-pane latex-state]
       [live-sheet-preview canonical style-cfg])
     [common/card {} "No CVConfig found in DataScript archive."])])

(defn view
  "Style Customizer, live preview, and multi-format export view."
  []
  (let [style-cfg @(rf/subscribe [::style-config])
        mode @(rf/subscribe [::preview-mode])
        tab @(rf/subscribe [::source-tab])
        canonical @(rf/subscribe [::resolved-cv])
        outputs @(rf/subscribe [::rendered-outputs])
        export-msg @(rf/subscribe [::export-msg])
        latex-state @(rf/subscribe [::latex-wasm])]
    [layout/split-view {:ratio :1-2}
     [:div {:class "p-4 space-y-4 print:hidden"}
      [style-customizer-panel style-cfg]
      (when outputs
        [:<>
         [export-buttons-panel outputs export-msg]
         [pdf-engine-panel canonical style-cfg outputs latex-state]])]
     [preview-pane canonical style-cfg outputs mode tab latex-state]]))
