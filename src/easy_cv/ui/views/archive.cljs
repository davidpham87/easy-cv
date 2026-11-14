(ns easy-cv.ui.views.archive
  "Fact archive view: filtering, quick entry, seeded tiers, snapshots, SQL."
  (:require [clojure.string :as str]
            [easy-cv.core.db :as db]
            [easy-cv.core.generators :as gen]
            [easy-cv.core.honeysql :as honeysql]
            [easy-cv.fx.datascript :as fx-ds]
            [easy-cv.ui.components.common :as common]
            [easy-cv.ui.components.layout :as layout]
            [easy-cv.ui.theme :as t]
            [re-frame.core :as rf]))

(def fact-type-options
  "Supported fact category filters for the archive toolbar."
  [:all :work :education :volunteer :project :skill
   :document :hobby :person :org :cv/config])

(defn- now-ms
  "Return current epoch milliseconds in ClojureScript."
  []
  (.now js/Date))

(defn- iso-now
  "Return current ISO-8601 timestamp string."
  []
  (.toISOString (js/Date.)))

(defn- parse-tags
  "Parse comma-separated tags string into a set of keywords."
  [s]
  (->> (str/split (str s) #",")
       (map (comp str/lower-case str/trim))
       (remove str/blank?)
       (map keyword)
       set))

(defn fact-kind
  "Infer primary display category keyword for any DataScript fact map."
  [fact]
  (cond
    (:cv/id fact) :cv/config
    (:person/email fact) :person
    (:org/name fact) :org
    (:history/type fact) (:history/type fact)
    (:project/name fact) :project
    (:skill/name fact) :skill
    (:document/name fact) :document
    (:hobby/name fact) :hobby
    :else :other))

(defn fact-label
  "Return concise primary label string for a fact row."
  [fact]
  (or (:history/title fact)
      (:project/name fact)
      (:skill/name fact)
      (:document/name fact)
      (:hobby/name fact)
      (:person/name fact)
      (:org/name fact)
      (:cv/title fact)
      (str (:item/uuid fact))))

(defn fact-summary
  "Return secondary summary text for a fact row."
  [fact]
  (or (:history/subtitle fact)
      (:history/description fact)
      (:project/description fact)
      (:skill/category fact)
      (:document/venue fact)
      (:hobby/description fact)
      (:person/email fact)
      (:org/location fact)
      (when (:cv/id fact) (str "Target: " (:cv/id fact)))
      ""))

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
  "Project DataScript archive facts into HoneySQL :facts row maps."
  [facts ts]
  (mapv (fn [f]
          {:uuid (str (:item/uuid f))
           :entity_type (name (fact-kind f))
           :attrs_transit (pr-str f)
           :updated_at ts
           :archived (if (:item/archived? f) 1 0)})
        (remove :cv/id facts)))

(defn- configs->sql-rows
  "Project CVConfig entities into HoneySQL :cv_configs row maps."
  [configs ts]
  (mapv (fn [c]
          {:uuid (str (:item/uuid c))
           :title (or (:cv/title c) (str (:cv/id c)))
           :config_transit (pr-str c)
           :updated_at ts})
        configs))

(defn- snapshots->sql-rows
  "Project captured archive snapshots into HoneySQL :snapshots row maps."
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
              (cond
                (str/includes? sql-str "FROM facts")
                (facts->sql-rows facts ts)
                (str/includes? sql-str "FROM cv_configs")
                (configs->sql-rows configs ts)
                (str/includes? sql-str "FROM snapshots")
                (snapshots->sql-rows snapshots ts)
                :else []))]
    (honeysql/db->sql-export qfn exported-at)))

(rf/reg-cofx
 ::ds-db
 (fn [cofx _]
   (assoc cofx :ds-db @fx-ds/conn)))

(rf/reg-event-db
 ::init
 (fn [app-db _]
   (update app-db :archive
           #(merge {:filter-type :all
                    :filter-tag ""
                    :search-text ""
                    :selected-tier :junior
                    :snapshot-label ""
                    :snapshots []
                    :quick-fact {:fact/kind :skill
                                 :fact/title ""
                                 :fact/detail ""
                                 :fact/tags-text ""}}
                   %))))

(rf/reg-event-db
 ::set-filter-type
 (fn [app-db [_ type-k]]
   (assoc-in app-db [:archive :filter-type] (keyword type-k))))

(rf/reg-event-db
 ::set-filter-tag
 (fn [app-db [_ tag-q]]
   (assoc-in app-db [:archive :filter-tag] tag-q)))

(rf/reg-event-db
 ::set-search-text
 (fn [app-db [_ text-q]]
   (assoc-in app-db [:archive :search-text] text-q)))

(rf/reg-event-db
 ::set-selected-tier
 (fn [app-db [_ tier]]
   (assoc-in app-db [:archive :selected-tier] (keyword tier))))

(rf/reg-event-db
 ::update-quick-fact
 (fn [app-db [_ field val]]
   (assoc-in app-db [:archive :quick-fact field] val)))

(rf/reg-event-db
 ::set-snapshot-label
 (fn [app-db [_ label]]
   (assoc-in app-db [:archive :snapshot-label] label)))

(rf/reg-event-fx
 ::add-quick-fact
 (fn [{:keys [db]} _]
   (let [qf (get-in db [:archive :quick-fact])]
     (if (str/blank? (:fact/title qf))
       {}
       {:db (assoc-in db [:archive :quick-fact]
                      {:fact/kind (:fact/kind qf :skill)
                       :fact/title ""
                       :fact/detail ""
                       :fact/tags-text ""})
        :ds/transact [(build-quick-fact qf)]}))))

(rf/reg-event-fx
 ::load-tier-archive
 (fn [{:keys [db]} [_ tier-override]]
   (let [tier (or tier-override
                  (get-in db [:archive :selected-tier])
                  :junior)
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
               (str "Snapshot #" (inc (count snaps)))
               raw-lbl)
         snap (db/capture-snapshot
               ds-db {:snapshot/parent parent
                      :snapshot/label lbl
                      :snapshot/created-at (now-ms)})]
     {:db (-> db
              (update-in [:archive :snapshots] (fnil conj []) snap)
              (assoc-in [:archive :snapshot-label] ""))})))

(rf/reg-event-fx
 ::restore-snapshot
 (fn [{:keys [db]} [_ snap-id]]
   (let [snaps (get-in db [:archive :snapshots] [])
         snap (first (filter #(= snap-id (:snapshot/id %)) snaps))]
     (if snap
       {:ds/restore-snapshot snap}
       {}))))

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
(rf/reg-sub ::filter-type :<- [::slice] (fn [s _] (or (:filter-type s) :all)))
(rf/reg-sub ::filter-tag :<- [::slice] (fn [s _] (or (:filter-tag s) "")))
(rf/reg-sub ::search-text :<- [::slice] (fn [s _] (or (:search-text s) "")))
(rf/reg-sub ::selected-tier :<- [::slice]
            (fn [s _] (or (:selected-tier s) :junior)))
(rf/reg-sub ::snapshots :<- [::slice] (fn [s _] (or (:snapshots s) [])))
(rf/reg-sub ::snapshot-label :<- [::slice]
            (fn [s _] (or (:snapshot-label s) "")))
(rf/reg-sub ::quick-fact :<- [::slice]
            (fn [s _]
              (or (:quick-fact s)
                  {:fact/kind :skill :fact/title ""
                   :fact/detail "" :fact/tags-text ""})))
(rf/reg-sub ::all-facts :<- [:easy-cv.fx.datascript/db]
            (fn [ds-db _] (db/all-facts ds-db)))
(rf/reg-sub ::fact-counts :<- [::all-facts]
            (fn [facts _] (frequencies (map fact-kind facts))))
(rf/reg-sub ::filtered-facts
            :<- [::all-facts] :<- [::filter-type]
            :<- [::filter-tag] :<- [::search-text]
            (fn [[facts type-k tag-q text-q] _]
              (filterv #(matches-filter? % type-k tag-q text-q) facts)))

(defn- with-val
  "Attach HTML :value prop without an unqualified-key map literal."
  [props v]
  (assoc props (keyword "value") v))

(defn- select-opt
  "Render an HTML <option> element for select controls."
  [v lbl]
  [:option (with-val {:key v} v) lbl])

(defn- labeled-input
  "Render a labeled text input bound to an event prefix."
  [lbl placeholder v evt]
  [:div
   [common/label {} lbl]
   [common/input (with-val {:placeholder placeholder :on-change-event evt} v)]])

(defn- filter-toolbar
  "Render fact category, tag, and search filters."
  []
  (let [ft @(rf/subscribe [::filter-type])
        ftag @(rf/subscribe [::filter-tag])
        fq @(rf/subscribe [::search-text])]
    [common/card {:class "mb-4"}
     [:div {:class "grid grid-cols-1 md:grid-cols-3 gap-3"}
      [:div
       [common/label {} "Fact Type"]
       [common/select
        (with-val {:class "w-full text-sm"
                   :on-change #(rf/dispatch [::set-filter-type (keyword %)])}
          (subs (str ft) 1))
        (for [k fact-type-options]
          (let [s (subs (str k) 1)] (select-opt s s)))]]
      [labeled-input "Tag Filter" "clojure, sql..." ftag [::set-filter-tag]]
      [labeled-input "Search" "Search title or summary..." fq
       [::set-search-text]]]]))

(defn- quick-kind-select
  "Render quick-entry fact kind dropdown."
  [kind]
  [:div
   [common/label {} "Kind"]
   [common/select
    (with-val {:class "w-full text-sm"
               :on-change #(rf/dispatch [::update-quick-fact
                                         :fact/kind (keyword %)])}
      (name (or kind :skill)))
    (for [k [:skill :work :education :project :document :hobby]]
      (select-opt (name k) (name k)))]])

(defn- quick-add-card
  "Render inline form for quickly adding a fact to DataScript."
  []
  (let [qf @(rf/subscribe [::quick-fact])]
    [common/card {:class "mb-4"}
     [common/section-header {:title "Quick Add Fact"}]
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

(defn- fact-row
  "Render a single fact row inside the archive table."
  [fact]
  [common/tr {:key (str (:item/uuid fact))}
   [common/td {} [common/badge {} (name (fact-kind fact))]]
   [common/td {:class "font-medium"} (fact-label fact)]
   [common/td {:class (str "text-xs " t/text-muted)} (fact-summary fact)]
   [common/td {}
    [:div {:class "flex flex-wrap gap-1"}
     (for [tag (:item/tags fact)]
       [common/badge {:key (name tag)} (name tag)])]]])

(defn- facts-table
  "Render filtered archive facts table."
  []
  (let [facts @(rf/subscribe [::filtered-facts])]
    [common/table-container {}
     [common/table {}
      [common/thead {}
       [common/tr {}
        [common/th {} "Type"]
        [common/th {} "Title"]
        [common/th {} "Details"]
        [common/th {} "Tags"]]]
      [common/tbody {}
       (if (empty? facts)
         [common/tr {}
          [common/td {:col-span 4 :class "text-center py-6"}
           "No matching facts. Load a seeded tier or add a quick fact."]]
         (for [fact facts] (fact-row fact)))]]]))

(defn- tier-loader-card
  "Render career tier dropdown and fake archive loader button."
  []
  (let [tier @(rf/subscribe [::selected-tier])]
    [common/card {}
     [common/section-header {:title "Seeded Fake Archives"}]
     [:div {:class "flex gap-2 items-center"}
      [common/select
       (with-val {:class "flex-grow text-sm"
                  :on-change #(rf/dispatch [::set-selected-tier (keyword %)])}
         (name tier))
       (for [t gen/tiers] (select-opt (name t) (name t)))]
      [common/button {:variant :primary :size :sm
                      :on-click #(rf/dispatch [::load-tier-archive])}
       "Load Tier"]]]))

(defn- snapshot-row
  "Render a single snapshot entry with a restore button."
  [snap]
  [:div {:key (str (:snapshot/id snap))
         :class "flex items-center justify-between py-1 text-xs"}
   [:span (:snapshot/label snap)]
   [common/button {:variant :default :size :xs
                   :on-click #(rf/dispatch [::restore-snapshot
                                            (:snapshot/id snap)])}
    "Restore"]])

(defn- snapshots-card
  "Render epochal snapshot capture input and restore list."
  []
  (let [snaps @(rf/subscribe [::snapshots])
        lbl @(rf/subscribe [::snapshot-label])]
    [common/card {}
     [common/section-header {:title "Epochal Snapshots"}]
     [:div {:class "flex gap-2 mb-3"}
      [common/input (with-val {:placeholder "Snapshot label..."
                               :on-change-event [::set-snapshot-label]} lbl)]
      [common/button {:variant :default :size :sm
                      :on-click #(rf/dispatch [::capture-snapshot])}
       "Capture"]]
     (for [snap (reverse snaps)] (snapshot-row snap))]))

(defn- sql-export-card
  "Render SQLite/JSON export card."
  []
  [common/card {}
   [common/section-header {:title "SQLite / JSON Export"}]
   [common/button {:variant :primary :size :sm :class "w-full"
                   :on-click #(rf/dispatch [::export-sql-json])}
    "Export SQL Tables JSON"]])

(defn- sidebar-controls
  "Render archive management sidebar cards."
  []
  [:div {:class "p-4 space-y-4"}
   [tier-loader-card]
   [snapshots-card]
   [sql-export-card]])

(defn view
  "Main Fact Archive Reagent view component."
  []
  (let [all-facts @(rf/subscribe [::all-facts])
        shown @(rf/subscribe [::filtered-facts])]
    [layout/split-view {:ratio :2-1}
     [:div {:class "p-4"}
      [common/page-header
       {:title "Fact Archive"
        :description (str (count shown) " shown of "
                          (count all-facts) " facts")}]
      [filter-toolbar]
      [quick-add-card]
      [facts-table]]
     [sidebar-controls]]))
