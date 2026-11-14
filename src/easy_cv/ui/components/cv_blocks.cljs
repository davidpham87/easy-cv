(ns easy-cv.ui.components.cv-blocks
  "Polymorphic Reagent CV renderer supporting layouts, themes, and sections."
  (:require [clojure.string :as str]
            [easy-cv.ui.styles :as styles]))

(def ^:private design-map
  {:modern-minimal :modern
   :swiss-grid :modern
   :academic-classic :academic
   :executive-serif :classic
   :compact-ats :minimal
   :academic :academic
   :minimal :minimal
   :modern :modern
   :classic :classic})

(def ^:private layout-map
  {:single-column :single-col
   :two-column :two-col
   :two-column-sidebar :sidebar
   :single-col :single-col
   :two-col :two-col
   :sidebar :sidebar})

(def ^:private section-titles
  {:work "Work Experience"
   :education "Education"
   :volunteer "Volunteer & Service"
   :projects "Projects"
   :skills "Skills"
   :documents "Publications & Documents"
   :hobbies "Interests & Hobbies"})

(def ^:private sidebar-sections
  #{:skills :hobbies :documents})

(defn- infer-font-preset
  "Map font family keyword or map to a styles/font-presets key."
  [font]
  (if (keyword? font)
    font
    (let [h (str/lower-case (str (:heading font "")))]
      (cond
        (str/includes? h "computer modern") :academic
        (str/includes? h "merriweather") :serif
        (str/includes? h "serif") :serif
        :else :sans))))

(defn resolve-preview-classes
  "Resolve CanonicalCV :cv/style and opts into static Tailwind classes."
  [canonical-cv opts]
  (let [st (merge (:cv/style canonical-cv) (:cv/style opts))
        design (get design-map (or (:design st) (:theme st)) :modern)
        layout (get layout-map (:layout st) :single-col)
        font (infer-font-preset (:font st))
        density (or (:density opts) (:density st) :balanced)
        overrides (or (:class-overrides opts) (:class-overrides st))]
    {:layout layout
     :classes (styles/resolve-cv-classes
               {:design design
                :layout layout
                :font font
                :density density
                :class-overrides overrides})}))

(defn- date-span
  "Format ISO start/end dates into a readable range string."
  [start-date end-date]
  (when (or start-date end-date)
    (str (or start-date "")
         (when start-date " — ")
         (or end-date (when start-date "Present")))))

(defn- org-line
  "Format organization map or location string for history entries."
  [{:history/keys [subtitle organization location]}]
  (let [org-name (when (map? organization) (:org/name organization))
        org-loc (or location (when (map? organization)
                               (:org/location organization)))]
    (str/join " · " (remove str/blank? [subtitle org-name org-loc]))))

(defn- render-header
  "Render CV person header with contact details and optional summary."
  [person cv-title classes]
  (let [full-name (or (:person/name person) cv-title "Curriculum Vitae")
        contacts (remove str/blank?
                         [(:person/email person)
                          (:person/phone person)
                          (:person/location person)
                          (:person/website person)])]
    [:header {:class (:header classes)}
     [:h1 {:class (:title classes)} full-name]
     (when (seq contacts)
       [:p {:class (:subtitle classes)} (str/join " · " contacts)])
     (when-not (str/blank? (:person/summary person))
       [:p {:class (:muted classes)} (:person/summary person)])]))

(defn- render-history-item
  "Render a single work, education, or volunteer item."
  [item classes]
  (let [span (date-span (:history/start-date item) (:history/end-date item))
        sub (org-line item)
        highlights (:history/highlights item)]
    [:article {:key (str (:item/uuid item) "-" (:history/title item))
               :class (:item classes)}
     [:div {:class "flex items-baseline justify-between gap-2"}
      [:h3 {:class (:item-title classes)} (:history/title item)]
      (when span [:span {:class (:muted classes)} span])]
     (when-not (str/blank? sub)
       [:div {:class (:muted classes)} sub])
     (when-not (str/blank? (:history/description item))
       [:p {:class "mt-1"} (:history/description item)])
     (when (seq highlights)
       [:ul {:class "list-disc list-inside mt-1 space-y-0.5"}
        (for [[idx hl] (map-indexed vector highlights)]
          [:li {:key idx} hl])])]))

(defn- render-history-section
  "Render a history section (:work, :education, or :volunteer)."
  [{:section/keys [id items]} classes]
  (when (seq items)
    [:section {:key (name id) :class (:section classes)}
     [:h2 {:class (:section-title classes)} (get section-titles id "History")]
     (for [item items]
       (render-history-item item classes))]))

(defn- render-project-item
  "Render a single project entry."
  [item classes]
  (let [span (date-span (:project/start-date item) (:project/end-date item))
        skills (keep #(if (map? %) (:skill/name %) (when (string? %) %))
                     (:project/skills item))]
    [:article {:key (str (:item/uuid item) "-" (:project/name item))
               :class (:item classes)}
     [:div {:class "flex items-baseline justify-between gap-2"}
      [:h3 {:class (:item-title classes)} (:project/name item)]
      (when span [:span {:class (:muted classes)} span])]
     (when-not (str/blank? (:project/description item))
       [:p {:class "mt-1"} (:project/description item)])
     (when (seq skills)
       [:div {:class "flex flex-wrap gap-1 mt-1"}
        (for [s skills]
          [:span {:key s :class (:badge classes)} s])])]))

(defn- render-doc-item
  "Render a single publication, talk, diploma, or reference."
  [item classes]
  (let [authors (str/join ", " (:document/authors item))
        meta-line (str/join " · "
                            (remove str/blank?
                                    [(:document/venue item)
                                     (:document/date item)
                                     (:document/doi item)]))]
    [:article {:key (str (:item/uuid item) "-" (:document/name item))
               :class (:item classes)}
     [:div {:class "flex items-baseline justify-between gap-2"}
      [:h3 {:class (:item-title classes)} (:document/name item)]
      (when-let [dt (:document/type item)]
        [:span {:class (:badge classes)} (name dt)])]
     (when-not (str/blank? authors)
       [:div {:class (:muted classes)} authors])
     (when-not (str/blank? meta-line)
       [:div {:class (:muted classes)} meta-line])]))

(defmulti render-section
  "Polymorphic Reagent renderer dispatching on :section/id."
  (fn [section _classes] (:section/id section)))

(defmethod render-section :work
  [section classes]
  (render-history-section section classes))

(defmethod render-section :education
  [section classes]
  (render-history-section section classes))

(defmethod render-section :volunteer
  [section classes]
  (render-history-section section classes))

(defmethod render-section :projects
  [{:section/keys [items]} classes]
  (when (seq items)
    [:section {:key "projects" :class (:section classes)}
     [:h2 {:class (:section-title classes)} (:projects section-titles)]
     (for [item items] (render-project-item item classes))]))

(defmethod render-section :skills
  [{:section/keys [items]} classes]
  (when (seq items)
    [:section {:key "skills" :class (:section classes)}
     [:h2 {:class (:section-title classes)} (:skills section-titles)]
     [:div {:class "flex flex-wrap gap-1.5 mt-1"}
      (for [item items]
        [:span {:key (str (:item/uuid item) "-" (:skill/name item))
                :class (:badge classes)}
         (str (:skill/name item)
              (when (:skill/level item)
                (str " · " (:skill/level item))))])]]))

(defmethod render-section :documents
  [{:section/keys [items]} classes]
  (when (seq items)
    [:section {:key "documents" :class (:section classes)}
     [:h2 {:class (:section-title classes)} (:documents section-titles)]
     (for [item items] (render-doc-item item classes))]))

(defmethod render-section :hobbies
  [{:section/keys [items]} classes]
  (when (seq items)
    [:section {:key "hobbies" :class (:section classes)}
     [:h2 {:class (:section-title classes)} (:hobbies section-titles)]
     [:ul {:class "space-y-1"}
      (for [item items]
        [:li {:key (str (:item/uuid item) "-" (:hobby/name item))
              :class (:item classes)}
         [:span {:class (:item-title classes)} (:hobby/name item)]
         (when-not (str/blank? (:hobby/description item))
           [:span {:class (:muted classes)}
            (str " — " (:hobby/description item))])])]]))

(defmethod render-section :default
  [{:section/keys [id items]} classes]
  (when (seq items)
    [:section {:key (str id) :class (:section classes)}
     [:h2 {:class (:section-title classes)} (if id (name id) "Section")]
     (for [[idx item] (map-indexed vector items)]
       [:div {:key idx :class (:item classes)} (pr-str item)])]))

(defn- partition-sections
  "Split sections into {:main [...] :side [...]} according to layout."
  [layout sections]
  (case layout
    :sidebar
    {:main (remove #(contains? sidebar-sections (:section/id %)) sections)
     :side (filter #(contains? sidebar-sections (:section/id %)) sections)}
    :two-col
    (let [indexed (map-indexed vector (filter #(seq (:section/items %))
                                              sections))]
      {:main (map second (filter (comp even? first) indexed))
       :side (map second (filter (comp odd? first) indexed))})
    {:main sections :side nil}))

(defn- render-body
  "Render main and optional secondary columns for the selected layout."
  [layout sections classes]
  (let [{:keys [main side]} (partition-sections layout sections)]
    [:div {:class (:body classes)}
     [:div {:class (:main-col classes)}
      (for [sec main] (render-section sec classes))]
     (when (seq side)
       [:aside {:class (:side-col classes)}
        (for [sec side] (render-section sec classes))])]))

(defn cv-preview
  "Render a CanonicalCV map into styled Hiccup using resolved layout classes."
  ([canonical-cv] (cv-preview canonical-cv {}))
  ([canonical-cv opts]
   (if-not (map? canonical-cv)
     [:div {:class "p-6 text-sm text-[var(--text-muted)]"}
      "Select or configure a CV target to preview."]
     (let [{:keys [layout classes]} (resolve-preview-classes canonical-cv opts)]
       [:article {:class (:sheet classes)}
        (render-header (:cv/person canonical-cv)
                       (:cv/title canonical-cv)
                       classes)
        (render-body layout (:cv/sections canonical-cv) classes)]))))
