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
   :nordic :nordic-slate
   :solarized :academic
   :academic :academic
   :minimal :minimal
   :modern :modern
   :classic :classic
   :material-tonal :material-tonal
   :zenburn-cv :zenburn-cv
   :nordic-slate :nordic-slate
   :swiss-editorial :swiss-editorial})

(def ^:private layout-map
  {:single-column :single-col
   :two-column :two-col
   :two-column-sidebar :sidebar
   :single-col :single-col
   :two-col :two-col
   :sidebar :sidebar
   :header-banner :header-banner
   :compact-grid :compact-grid})

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
        (str/includes? h "jakarta") :humanist
        (str/includes? h "jetbrains") :mono-tech
        (str/includes? h "mono") :mono-tech
        (str/includes? h "merriweather") :serif
        (str/includes? h "serif") :serif
        :else :sans))))

(defn resolve-preview-classes
  "Resolve CanonicalCV :cv/style and opts into classes, layout, and colors."
  [canonical-cv opts]
  (let [st (merge (:cv/style canonical-cv) (:cv/style opts))
        design (get design-map (or (:design st) (:theme st)) :modern)
        layout (get layout-map (:layout st) :single-col)
        font (infer-font-preset (:font st))
        density (or (:density opts) (:density st) :balanced)
        chip-style (or (:chip-style opts) (:chip-style st))
        colors (or (:colors opts) (:colors st))
        overrides (or (:class-overrides opts) (:class-overrides st))]
    {:layout layout
     :colors (when (seq colors) colors)
     :classes (styles/resolve-cv-classes
               {:design design :layout layout :font font
                :density density :chip-style chip-style
                :colors colors :class-overrides overrides})}))

(defn- compact-style
  "Remove nil values from an inline React style map, returning nil if empty."
  [m]
  (let [clean (into {} (filter (comp some? val)) m)]
    (when (seq clean) clean)))

(defn- sheet-inline-style
  [colors]
  (compact-style
   {:backgroundColor (:sheet-bg colors)
    :--cv-accent (:accent colors)
    :--cv-header-bg (:header-bg colors)
    :--cv-header-fg (:header-fg colors)
    :--cv-chip-bg (:chip-bg colors)
    :--cv-chip-fg (:chip-fg colors)}))

(defn- header-inline-style
  [colors]
  (compact-style
   {:backgroundColor (:header-bg colors)
    :color (:header-fg colors)
    :borderColor (:accent colors)}))

(defn- chip-inline-style
  [colors]
  (compact-style
   {:backgroundColor (:chip-bg colors)
    :color (:chip-fg colors)
    :borderColor (:chip-border colors)}))

(defn- accent-inline-style
  [colors]
  (compact-style
   {:color (:accent colors)
    :borderColor (:accent colors)}))

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

(defn- contact-line
  [person]
  (let [parts (remove str/blank?
                      [(:person/email person)
                       (:person/phone person)
                       (:person/location person)
                       (:person/website person)])]
    (when (seq parts)
      (str/join " · " parts))))

(defn- render-header
  "Render CV person header with contact details and optional summary."
  [person cv-title classes colors layout]
  (let [full-name (or (:person/name person) cv-title "Curriculum Vitae")
        contacts (contact-line person)
        banner? (or (= layout :header-banner) (some? (:header-bg colors)))
        hdr-cls (if banner?
                  (str (:header classes) " px-6 py-5 rounded-lg")
                  (:header classes))
        fg-style (compact-style {:color (:header-fg colors)})
        sub-style (compact-style {:color (:header-fg colors) :opacity 0.9})
        sum-style (compact-style {:color (:header-fg colors) :opacity 0.85})]
    [:header {:class hdr-cls :style (header-inline-style colors)}
     [:h1 {:class (:title classes) :style fg-style} full-name]
     (when contacts [:p {:class (:subtitle classes) :style sub-style} contacts])
     (when-not (str/blank? (:person/summary person))
       [:p {:class (:muted classes) :style sum-style}
        (:person/summary person)])]))

(defn- render-section-heading
  "Render styled section heading and optional divider line."
  [title classes colors]
  [:<>
   [:h2 {:class (:section-title classes)
         :style (accent-inline-style colors)}
    title]
   [:hr {:class (str "border-t mb-2 " (:divider classes))
         :style (compact-style {:borderColor (:accent colors)})}]])

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
  [{:section/keys [id items]} classes colors]
  (when (seq items)
    [:section {:key (name id) :class (:section classes)}
     (render-section-heading (get section-titles id "History") classes colors)
     (for [item items]
       (render-history-item item classes))]))

(defn- render-project-item
  "Render a single project entry."
  [item classes colors]
  (let [span (date-span (:project/start-date item) (:project/end-date item))
        skills (keep #(if (map? %) (:skill/name %) (when (string? %) %))
                     (:project/skills item))
        c-style (chip-inline-style colors)]
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
          [:span {:key s :class (:badge classes) :style c-style} s])])]))

(defn- render-doc-item
  "Render a single publication, talk, diploma, or reference."
  [item classes colors]
  (let [authors (str/join ", " (:document/authors item))
        meta-line (str/join " · "
                            (remove str/blank?
                                    [(:document/venue item)
                                     (:document/date item)
                                     (:document/doi item)]))
        c-style (chip-inline-style colors)]
    [:article {:key (str (:item/uuid item) "-" (:document/name item))
               :class (:item classes)}
     [:div {:class "flex items-baseline justify-between gap-2"}
      [:h3 {:class (:item-title classes)} (:document/name item)]
      (when-let [dt (:document/type item)]
        [:span {:class (:badge classes) :style c-style} (name dt)])]
     (when-not (str/blank? authors)
       [:div {:class (:muted classes)} authors])
     (when-not (str/blank? meta-line)
       [:div {:class (:muted classes)} meta-line])]))

(defmulti render-section
  "Polymorphic Reagent renderer dispatching on :section/id."
  (fn [section _classes _colors] (:section/id section)))

(defmethod render-section :work
  [section classes colors]
  (render-history-section section classes colors))

(defmethod render-section :education
  [section classes colors]
  (render-history-section section classes colors))

(defmethod render-section :volunteer
  [section classes colors]
  (render-history-section section classes colors))

(defmethod render-section :projects
  [{:section/keys [items]} classes colors]
  (when (seq items)
    [:section {:key "projects" :class (:section classes)}
     (render-section-heading (:projects section-titles) classes colors)
     (for [item items] (render-project-item item classes colors))]))

(defmethod render-section :skills
  [{:section/keys [items]} classes colors]
  (when (seq items)
    (let [c-style (chip-inline-style colors)]
      [:section {:key "skills" :class (:section classes)}
       (render-section-heading (:skills section-titles) classes colors)
       [:div {:class "flex flex-wrap gap-1.5 mt-1"}
        (for [item items]
          [:span {:key (str (:item/uuid item) "-" (:skill/name item))
                  :class (:badge classes)
                  :style c-style}
           (str (:skill/name item)
                (when (:skill/level item)
                  (str " · " (:skill/level item))))])]])))

(defmethod render-section :documents
  [{:section/keys [items]} classes colors]
  (when (seq items)
    [:section {:key "documents" :class (:section classes)}
     (render-section-heading (:documents section-titles) classes colors)
     (for [item items] (render-doc-item item classes colors))]))

(defmethod render-section :hobbies
  [{:section/keys [items]} classes colors]
  (when (seq items)
    [:section {:key "hobbies" :class (:section classes)}
     (render-section-heading (:hobbies section-titles) classes colors)
     [:ul {:class "space-y-1"}
      (for [item items]
        [:li {:key (str (:item/uuid item) "-" (:hobby/name item))
              :class (:item classes)}
         [:span {:class (:item-title classes)} (:hobby/name item)]
         (when-not (str/blank? (:hobby/description item))
           [:span {:class (:muted classes)}
            (str " — " (:hobby/description item))])])]]))

(defmethod render-section :default
  [{:section/keys [id items]} classes colors]
  (when (seq items)
    [:section {:key (str id) :class (:section classes)}
     (render-section-heading (if id (name id) "Section") classes colors)
     (for [[idx item] (map-indexed vector items)]
       [:div {:key idx :class (:item classes)} (pr-str item)])]))

(defn- partition-sections
  "Split sections into {:main [...] :side [...]} according to layout."
  [layout sections]
  (case layout
    (:sidebar :compact-grid)
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
  [layout sections classes colors]
  (let [{:keys [main side]} (partition-sections layout sections)
        side-style (compact-style {:borderColor (:accent colors)})]
    [:div {:class (:body classes)}
     [:div {:class (:main-col classes)}
      (for [sec main] (render-section sec classes colors))]
     (when (seq side)
       [:aside {:class (:side-col classes) :style side-style}
        (for [sec side] (render-section sec classes colors))])]))

(defn cv-preview
  "Render a CanonicalCV map into styled Hiccup using resolved layout classes."
  ([canonical-cv] (cv-preview canonical-cv {}))
  ([canonical-cv opts]
   (if-not (map? canonical-cv)
     [:div {:class "p-6 text-sm text-[var(--text-muted)]"}
      "Select or configure a CV target to preview."]
     (let [{:keys [layout classes colors]}
           (resolve-preview-classes canonical-cv opts)]
       [:article {:class (:sheet classes)
                  :style (sheet-inline-style colors)}
        (render-header (:cv/person canonical-cv)
                       (:cv/title canonical-cv)
                       classes colors layout)
        (render-body layout (:cv/sections canonical-cv) classes colors)]))))
