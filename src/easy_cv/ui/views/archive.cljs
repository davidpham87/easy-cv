(ns easy-cv.ui.views.archive
  "Fact archive view: filtering, sorting, graph, live inspector, snapshots."
  (:require [clojure.string :as str]
            [easy-cv.core.archive-graph :as ag]
            [easy-cv.core.db :as db]
            [easy-cv.core.generators :as gen]
            [easy-cv.core.honeysql :as honeysql]
            [easy-cv.fx.datascript :as fx-ds]
            [easy-cv.ui.components.archive-graph :as archive-graph-ui]
            [easy-cv.ui.components.common :as common]
            [easy-cv.ui.components.layout :as layout]
            [easy-cv.ui.theme :as t]
            [re-frame.core :as rf]))

(def fact-type-options
  "Supported fact category filters for the archive toolbar."
  [:all :work :education :volunteer :project :skill
   :document :hobby :person :org :cv/config])

(defn- now-ms [] (.now js/Date))
(defn- iso-now [] (.toISOString (js/Date.)))

(defn- parse-tags
  "Parse comma-separated tags string into a set of keywords."
  [s]
  (->> (str/split (str s) #",")
       (map (comp str/lower-case str/trim))
       (remove str/blank?)
       (map keyword)
       set))

(defn fact-kind [fact] (ag/fact-kind fact))
(defn fact-label [fact] (ag/fact-label fact))
(defn fact-summary [fact] (ag/fact-summary fact))

(defn- entity-id-str
  "Return canonical string identifier matching archive-graph node ids."
  [e]
  (or (some-> (:item/uuid e) str)
      (some-> (:cv/id e) str)))

(defn- matches-filter?
  "True when fact satisfies type-k, tag-q, and text-q filters."
  [fact type-k tag-q text-q]
  (let [kind-ok? (or (= :all type-k) (= type-k (fact-kind fact)))
        tags (set (map name (:item/tags fact)))
        tq (str/lower-case (str/trim (str tag-q)))
        sq (str/lower-case (str/trim (str text-q)))
        tag-ok? (or (str/blank? tq)
                    (boolean (some #(str/includes? (str/lower-case %) tq)
                                   tags)))
        haystack (str/lower-case (str (fact-label fact) " "
                                      (fact-summary fact)))
        text-ok? (or (str/blank? sq) (str/includes? haystack sq))]
    (and kind-ok? tag-ok? text-ok?)))

(defn build-quick-fact
  "Build a schema-compatible DataScript fact map from quick-entry fields."
  [{:fact/keys [kind title detail tags-text]}]
  (let [uid (random-uuid)
        tags (parse-tags tags-text)
        base (cond-> {:item/uuid uid}
               (seq tags) (assoc :item/tags tags))]
    (case kind
      :skill (assoc base :skill/name title :skill/category
                    (if (str/blank? detail) "General" detail))
      :project (assoc base :project/name title :project/description detail)
      :hobby (assoc base :hobby/name title :hobby/description detail)
      :document (assoc base :document/name title :document/type :publication
                       :document/venue detail)
      :education (assoc base :history/type :education :history/title title
                        :history/start-date "2024-01"
                        :history/description detail)
      (assoc base :history/type :work :history/title title
             :history/start-date "2024-01" :history/description detail))))

(defn- facts->sql-rows
  [facts ts]
  (mapv (fn [f] {:uuid (str (:item/uuid f)) :entity_type (name (fact-kind f))
                 :attrs_transit (pr-str f) :updated_at ts
                 :archived (if (:item/archived? f) 1 0)})
        (remove :cv/id facts)))

(defn- configs->sql-rows
  [configs ts]
  (mapv (fn [c] {:uuid (str (:item/uuid c))
                 :title (or (:cv/title c) (str (:cv/id c)))
                 :config_transit (pr-str c) :updated_at ts})
        configs))

(defn- snapshots->sql-rows
  [snapshots ts]
  (mapv (fn [s]
          (-> {:parent_id (some-> (:snapshot/parent s) str)
               :label (or (:snapshot/label s) "")
               :schema_version (or (:snapshot/schema-version s) 1)
               :db_transit (pr-str (:snapshot/db s))
               :created_at (or (:snapshot/created-at s) ts)}
              (assoc (keyword "id") (str (:snapshot/id s)))))
        snapshots))

(defn build-sql-export-payload
  "Build and validate SQL tables JSON export map via honeysql/db->sql-export."
  [ds-db snapshots ts exported-at]
  (let [facts (db/all-facts ds-db)
        configs (db/list-cv-configs ds-db)
        qfn (fn [[sql-str]]
              (cond (str/includes? sql-str "FROM facts")
                    (facts->sql-rows facts ts)
                    (str/includes? sql-str "FROM cv_configs")
                    (configs->sql-rows configs ts)
                    (str/includes? sql-str "FROM snapshots")
                    (snapshots->sql-rows snapshots ts)
                    :else []))]
    (honeysql/db->sql-export qfn exported-at)))

(rf/reg-cofx ::ds-db (fn [cofx _] (assoc cofx :ds-db @fx-ds/conn)))

(rf/reg-fx
 ::copy-clipboard!
 (fn [text]
   (when (and (exists? js/navigator) (.-clipboard js/navigator))
     (.writeText (.-clipboard js/navigator) (str text)))))

(def ^:private default-slice
  {:filter-type :all :filter-tag "" :search-text ""
   :sort-by :label :sort-dir :asc
   :selected-id nil :hovered-id nil :graph-layout :clustered
   :inspector-format :edn :inspector-copied? false
   :selected-tier :junior :snapshot-label "" :snapshots []
   :quick-fact {:fact/kind :skill :fact/title ""
                :fact/detail "" :fact/tags-text ""}})

(rf/reg-event-db ::init
                 (fn [db _] (update db :archive #(merge default-slice %))))

(doseq [[ev-k field xform]
        [[::set-filter-type :filter-type keyword]
         [::set-filter-tag :filter-tag identity]
         [::set-search-text :search-text identity]
         [::set-hovered-node :hovered-id identity]
         [::set-graph-layout :graph-layout keyword]
         [::set-selected-tier :selected-tier keyword]
         [::set-snapshot-label :snapshot-label identity]]]
  (rf/reg-event-db ev-k
                   (fn [db [_ v]] (assoc-in db [:archive field] (xform v)))))

(rf/reg-event-db
 ::set-sort-by
 (fn [db [_ col]]
   (let [cur (get-in db [:archive :sort-by] :label)]
     (if (= cur col)
       (update-in db [:archive :sort-dir] #(if (= :desc %) :asc :desc))
       (-> db (assoc-in [:archive :sort-by] col)
           (assoc-in [:archive :sort-dir] :asc))))))

(rf/reg-event-db
 ::toggle-sort-dir
 (fn [db _]
   (update-in db [:archive :sort-dir] #(if (= :desc %) :asc :desc))))

(rf/reg-event-db
 ::select-node
 (fn [db [_ nid]]
   (-> db (assoc-in [:archive :selected-id] (some-> nid str))
       (assoc-in [:archive :inspector-copied?] false))))

(rf/reg-event-db
 ::reset-selection
 (fn [db _]
   (-> db (assoc-in [:archive :selected-id] nil)
       (assoc-in [:archive :inspector-copied?] false))))

(rf/reg-event-db
 ::set-inspector-format
 (fn [db [_ fmt]]
   (-> db (assoc-in [:archive :inspector-format] (keyword fmt))
       (assoc-in [:archive :inspector-copied?] false))))

(rf/reg-event-fx
 ::copy-inspector
 (fn [{:keys [db]} [_ text]]
   {:db (assoc-in db [:archive :inspector-copied?] true)
    ::copy-clipboard! text}))

(rf/reg-event-db
 ::update-quick-fact
 (fn [db [_ f v]] (assoc-in db [:archive :quick-fact f] v)))

(rf/reg-event-fx
 ::add-quick-fact
 (fn [{:keys [db]} _]
   (let [qf (get-in db [:archive :quick-fact])]
     (if (str/blank? (:fact/title qf))
       {}
       {:db (assoc-in db [:archive :quick-fact]
                      {:fact/kind (:fact/kind qf :skill)
                       :fact/title "" :fact/detail "" :fact/tags-text ""})
        :ds/transact [(build-quick-fact qf)]}))))

(rf/reg-event-fx
 ::load-tier-archive
 (fn [{:keys [db]} [_ tier-override]]
   (let [tier (or tier-override (get-in db [:archive :selected-tier]) :junior)
         archive (gen/generate-archive tier {:seed (mod (now-ms) 100000)})]
     {:db (assoc-in db [:archive :selected-tier] tier)
      :ds/transact (:archive/tx-data archive)})))

(rf/reg-event-fx
 ::capture-snapshot
 [(rf/inject-cofx ::ds-db)]
 (fn [{:keys [db ds-db]} _]
   (let [snaps (get-in db [:archive :snapshots] [])
         parent (:snapshot/id (last snaps))
         raw-lbl (get-in db [:archive :snapshot-label])
         lbl (if (str/blank? raw-lbl)
               (str "Snapshot #" (inc (count snaps))) raw-lbl)
         snap (db/capture-snapshot
               ds-db {:snapshot/parent parent :snapshot/label lbl
                      :snapshot/created-at (now-ms)})]
     {:db (-> db (update-in [:archive :snapshots] (fnil conj []) snap)
              (assoc-in [:archive :snapshot-label] ""))})))

(rf/reg-event-fx
 ::restore-snapshot
 (fn [{:keys [db]} [_ snap-id]]
   (let [snaps (get-in db [:archive :snapshots] [])
         snap (first (filter #(= snap-id (:snapshot/id %)) snaps))]
     (if snap {:ds/restore-snapshot snap} {}))))

(rf/reg-event-fx
 ::export-sql-json
 [(rf/inject-cofx ::ds-db)]
 (fn [{:keys [db ds-db]} _]
   (let [snaps (get-in db [:archive :snapshots] [])
         payload (build-sql-export-payload ds-db snaps (now-ms) (iso-now))
         json-str (js/JSON.stringify (clj->js payload) nil 2)]
     {:io/download-file {:io/content json-str
                         :io/filename "easy-cv-sql-export.json"
                         :io/mime-type "application/json"}})))

(rf/reg-sub ::slice (fn [app-db _] (:archive app-db)))

(doseq [[sub-k field default-v]
        [[::filter-type :filter-type :all]
         [::filter-tag :filter-tag ""]
         [::search-text :search-text ""]
         [::sort-by :sort-by :label]
         [::sort-dir :sort-dir :asc]
         [::selected-id :selected-id nil]
         [::hovered-id :hovered-id nil]
         [::graph-layout :graph-layout :clustered]
         [::inspector-format :inspector-format :edn]
         [::inspector-copied? :inspector-copied? false]
         [::selected-tier :selected-tier :junior]
         [::snapshots :snapshots []]
         [::snapshot-label :snapshot-label ""]
         [::quick-fact :quick-fact
          {:fact/kind :skill :fact/title ""
           :fact/detail "" :fact/tags-text ""}]]]
  (rf/reg-sub sub-k :<- [::slice]
              (fn [s _] (let [v (get s field)] (if (some? v) v default-v)))))
(rf/reg-sub ::all-facts :<- [:easy-cv.fx.datascript/db]
            (fn [ds-db _] (db/all-facts ds-db)))
(rf/reg-sub ::cv-configs :<- [:easy-cv.fx.datascript/db]
            (fn [ds-db _] (db/list-cv-configs ds-db)))
(rf/reg-sub ::fact-counts :<- [::all-facts]
            (fn [facts _] (frequencies (map fact-kind facts))))
(rf/reg-sub ::tag-facets :<- [::all-facts]
            (fn [facts _] (ag/extract-tag-facets facts)))

(rf/reg-sub ::filtered-facts
            :<- [::all-facts] :<- [::filter-type]
            :<- [::filter-tag] :<- [::search-text]
            :<- [::sort-by] :<- [::sort-dir]
            (fn [[facts type-k tag-q text-q sort-k sort-d] _]
              (-> (filterv #(matches-filter? % type-k tag-q text-q) facts)
                  (ag/sort-facts sort-k sort-d))))

(rf/reg-sub ::network-graph
            :<- [::all-facts] :<- [::cv-configs]
            :<- [::filter-type] :<- [::selected-id] :<- [::graph-layout]
            (fn [[facts cfgs type-k sel-id layout] _]
              (ag/archive->network-graph
               facts cfgs
               {:filter-kind type-k :selected-id sel-id :layout layout})))

(rf/reg-sub ::selected-entity
            :<- [::all-facts] :<- [::cv-configs] :<- [::selected-id]
            (fn [[facts cfgs sel-id] _]
              (when sel-id
                (first (filter #(= sel-id (entity-id-str %))
                               (concat facts cfgs))))))

(defn- with-val [props v] (assoc props (keyword "value") v))
(defn- select-opt [v lbl] [:option (with-val {:key v} v) lbl])

(defn- labeled-input
  [lbl placeholder v evt]
  [:div
   [common/label lbl]
   [common/input (with-val {:placeholder placeholder :on-change-event evt} v)]])

(defn- filter-type-select
  "Render Fact Type dropdown (first <select> in view)."
  [ft]
  (let [on-type #(rf/dispatch [::set-filter-type
                               (keyword (.. % -target -value))])]
    [:div
     [common/label "Fact Type"]
     [common/select
      (with-val {:class "w-full text-sm" :on-change on-type} (subs (str ft) 1))
      (for [k fact-type-options]
        (let [s (subs (str k) 1)] (select-opt s s)))]]))

(defn- filter-toolbar
  "Render fact category, tag input, search, and clickable tag facet chips."
  []
  (let [ft @(rf/subscribe [::filter-type])
        ftag @(rf/subscribe [::filter-tag])
        fq @(rf/subscribe [::search-text])
        facets @(rf/subscribe [::tag-facets])]
    [common/card {:class "mb-4"}
     [:div {:class "grid grid-cols-1 md:grid-cols-3 gap-3"}
      [filter-type-select ft]
      [labeled-input "Tag Filter" "clojure, sql..." ftag [::set-filter-tag]]
      [labeled-input "Search" "Search title or summary..." fq
       [::set-search-text]]]
     [archive-graph-ui/tag-facet-chips
      {:facets facets :active-tag ftag
       :on-select-tag #(rf/dispatch [::set-filter-tag %])}]]))

(defn- quick-kind-select
  [kind]
  (let [on-k #(rf/dispatch [::update-quick-fact
                            :fact/kind (keyword (.. % -target -value))])]
    [:div
     [common/label "Kind"]
     [common/select
      (with-val {:class "w-full text-sm" :on-change on-k}
        (name (or kind :skill)))
      (for [k [:skill :work :education :project :document :hobby]]
        (select-opt (name k) (name k)))]]))

(defn- quick-add-card
  "Render inline form for quickly adding a fact to DataScript."
  []
  (let [qf @(rf/subscribe [::quick-fact])]
    [common/card {:class "mb-4"}
     [common/section-header "Quick Add Fact"]
     [:div {:class "grid grid-cols-1 md:grid-cols-4 gap-2 items-end"}
      [quick-kind-select (:fact/kind qf)]
      [labeled-input "Title / Name" "Clojure, Dev" (:fact/title qf)
       [::update-quick-fact :fact/title]]
      [labeled-input "Details / Category" "Category/summary" (:fact/detail qf)
       [::update-quick-fact :fact/detail]]
      [:div {:class "flex gap-2 items-end"}
       [:div {:class "flex-grow"}
        [labeled-input "Tags" "clojure, sql" (:fact/tags-text qf)
         [::update-quick-fact :fact/tags-text]]]
       [common/button {:variant :primary :size :sm
                       :on-click #(rf/dispatch [::add-quick-fact])} "Add"]]]]))

(defn- tier-loader-card
  []
  (let [tier @(rf/subscribe [::selected-tier])
        on-t #(rf/dispatch [::set-selected-tier
                            (keyword (.. % -target -value))])]
    [common/card {}
     [common/section-header "Seeded Fake Archives"]
     [:div {:class "flex gap-2 items-center"}
      [common/select
       (with-val {:class "flex-grow text-sm" :on-change on-t} (name tier))
       (for [t gen/tiers] (select-opt (name t) (name t)))]
      [common/button {:variant :primary :size :sm
                      :on-click #(rf/dispatch [::load-tier-archive])}
       "Load Tier"]]]))

(defn- snapshot-row
  [snap]
  [:div {:key (str (:snapshot/id snap))
         :class "flex items-center justify-between py-1 text-xs"}
   [:span (:snapshot/label snap)]
   [common/button {:variant :default :size :xs
                   :on-click #(rf/dispatch [::restore-snapshot
                                            (:snapshot/id snap)])}
    "Restore"]])

(defn- snapshots-card
  []
  (let [snaps @(rf/subscribe [::snapshots])
        lbl @(rf/subscribe [::snapshot-label])]
    [common/card {}
     [common/section-header "Epochal Snapshots"]
     [:div {:class "flex gap-2 mb-3"}
      [common/input (with-val {:placeholder "Snapshot label..."
                               :on-change-event [::set-snapshot-label]} lbl)]
      [common/button {:variant :default :size :sm
                      :on-click #(rf/dispatch [::capture-snapshot])}
       "Capture"]]
     (for [snap (reverse snaps)] (snapshot-row snap))]))

(defn- sql-export-card []
  [common/card {}
   [common/section-header "SQLite / JSON Export"]
   [common/button {:variant :primary :size :sm :class "w-full"
                   :on-click #(rf/dispatch [::export-sql-json])}
    "Export SQL Tables JSON"]])

(defn- sidebar-controls
  "Render Live Inspector and archive management sidebar cards."
  []
  (let [entity @(rf/subscribe [::selected-entity])
        fmt @(rf/subscribe [::inspector-format])
        copied? @(rf/subscribe [::inspector-copied?])]
    [:div {:class "p-4 space-y-4"}
     [archive-graph-ui/inspector-card
      {:entity entity :format fmt :copied? copied?
       :on-format-change #(rf/dispatch [::set-inspector-format %])
       :on-copy #(rf/dispatch [::copy-inspector %])}]
     [tier-loader-card] [snapshots-card] [sql-export-card]]))

(defn- network-graph-section
  "Subscribe to graph slice and render network-graph-panel."
  []
  (let [graph @(rf/subscribe [::network-graph])
        layout @(rf/subscribe [::graph-layout])
        sel-id @(rf/subscribe [::selected-id])
        hov-id @(rf/subscribe [::hovered-id])]
    [archive-graph-ui/network-graph-panel
     {:graph graph :layout-mode layout :selected-id sel-id :hovered-id hov-id
      :on-select #(rf/dispatch [::select-node %])
      :on-hover #(rf/dispatch [::set-hovered-node %])
      :on-toggle-layout #(rf/dispatch [::set-graph-layout %])
      :on-reset #(rf/dispatch [::reset-selection])}]))

(defn- sort-and-table-section
  "Render sort controls, network graph, quick add, and facts table."
  [shown sort-k sort-d sel-id]
  [:<>
   [archive-graph-ui/sort-controls-bar
    {:sort-by sort-k :sort-dir sort-d
     :on-sort-by #(rf/dispatch [::set-sort-by %])
     :on-toggle-dir #(rf/dispatch [::toggle-sort-dir])}]
   [network-graph-section]
   [quick-add-card]
   [archive-graph-ui/facts-table
    {:facts shown :sort-by sort-k :sort-dir sort-d :selected-id sel-id
     :id-fn entity-id-str :on-sort #(rf/dispatch [::set-sort-by %])
     :on-select #(rf/dispatch [::select-node %])}]])

(defn- main-archive-pane
  "Render header, filters, sort controls, network graph, quick add, table."
  []
  (let [all-facts @(rf/subscribe [::all-facts])
        shown @(rf/subscribe [::filtered-facts])
        sort-k @(rf/subscribe [::sort-by])
        sort-d @(rf/subscribe [::sort-dir])
        sel-id @(rf/subscribe [::selected-id])]
    [:div {:class "p-4"}
     [common/page-header "Fact Archive"
      [:span {:class (str "text-sm " t/text-muted)}
       (str (count shown) " shown of " (count all-facts) " facts")]]
     [filter-toolbar]
     [sort-and-table-section shown sort-k sort-d sel-id]]))

(defn view
  "Main Fact Archive Reagent view component."
  []
  [layout/split-view {:ratio :2-1}
   [main-archive-pane]
   [sidebar-controls]])
