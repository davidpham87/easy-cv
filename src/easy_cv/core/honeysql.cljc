(ns easy-cv.core.honeysql
  "Pure HoneySQL v2 DDL, parameterized queries, and SQL-JSON export."
  (:require [honey.sql :as sql]
            [malli.core :as m]))

(def schema-version
  "Current SQLite projection schema version."
  1)

(def SqlExportMap
  "Malli schema for SQL tables JSON export payload."
  [:map
   [:schema-version pos-int?]
   [:exported-at :string]
   [:tables
    [:map
     [:facts [:vector :map]]
     [:cv_configs [:vector :map]]
     [:snapshots [:vector :map]]]]])

(def ^:private ddl-asts
  [{:create-table [:facts :if-not-exists]
    :with-columns [[:uuid :text [:primary-key]]
                   [:entity_type :text [:not nil]]
                   [:attrs_transit :text [:not nil]]
                   [:updated_at :integer [:not nil]]
                   [:archived :integer [:default 0]]]}
   {:create-table [:cv_configs :if-not-exists]
    :with-columns [[:uuid :text [:primary-key]]
                   [:title :text [:not nil]]
                   [:config_transit :text [:not nil]]
                   [:updated_at :integer [:not nil]]]}
   {:create-table [:snapshots :if-not-exists]
    :with-columns [[:id :text [:primary-key]]
                   [:parent_id :text]
                   [:label :text]
                   [:schema_version :integer [:not nil]]
                   [:db_transit :text [:not nil]]
                   [:created_at :integer [:not nil]]]}])

(defn ddl-statements
  "Returns formatted DDL SQL vectors for SQLite tables."
  []
  (mapv sql/format ddl-asts))

(defn upsert-fact-query
  "Builds parameterized HoneySQL upsert for a fact row."
  [{:fact/keys [uuid entity-type attrs-transit updated-at archived]
    :or {archived 0}}]
  (sql/format
   {:insert-into :facts
    :columns [:uuid :entity_type :attrs_transit :updated_at :archived]
    :values [[(str uuid) (name entity-type) attrs-transit updated-at archived]]
    :on-conflict :uuid
    :do-update-set [:entity_type :attrs_transit :updated_at :archived]}))

(defn delete-fact-query
  "Builds parameterized HoneySQL soft-delete or delete by uuid."
  [uuid]
  (sql/format {:delete-from :facts
               :where [:= :uuid (str uuid)]}))

(defn select-facts-query
  "Builds parameterized HoneySQL select for stored facts."
  ([] (select-facts-query {}))
  ([{:keys [include-archived?]}]
   (sql/format
    (cond-> {:select [:uuid :entity_type :attrs_transit :updated_at :archived]
             :from [:facts]
             :order-by [[:updated_at :asc] [:uuid :asc]]}
      (not include-archived?)
      (assoc :where [:= :archived 0])))))

(defn upsert-cv-config-query
  "Builds parameterized HoneySQL upsert for a CV config row."
  [{:cv/keys [uuid title config-transit updated-at]}]
  (sql/format
   {:insert-into :cv_configs
    :columns [:uuid :title :config_transit :updated_at]
    :values [[(str uuid) title config-transit updated-at]]
    :on-conflict :uuid
    :do-update-set [:title :config_transit :updated_at]}))

(defn select-cv-configs-query
  "Builds HoneySQL select for stored CV configs."
  []
  (sql/format
   {:select [:uuid :title :config_transit :updated_at]
    :from [:cv_configs]
    :order-by [[:updated_at :asc] [:uuid :asc]]}))

(defn insert-snapshot-query
  "Builds parameterized HoneySQL upsert for a snapshot row."
  [{:snapshot/keys [id parent-id label schema-ver db-transit created-at]
    :or {schema-ver schema-version}}]
  (sql/format
   {:insert-into :snapshots
    :columns [:id :parent_id :label :schema_version
              :db_transit :created_at]
    :values [[(str id) parent-id label schema-ver db-transit created-at]]
    :on-conflict :id
    :do-update-set [:parent_id :label :schema_version
                    :db_transit :created_at]}))

(defn select-snapshots-query
  "Builds HoneySQL select for stored epochal snapshots."
  []
  (sql/format
   {:select [:id :parent_id :label :schema_version
             :db_transit :created_at]
    :from [:snapshots]
    :order-by [[:created_at :asc] [:id :asc]]}))

(defn latest-snapshot-query
  "Builds HoneySQL select for the most recent snapshot."
  []
  (sql/format
   {:select [:id :parent_id :label :schema_version
             :db_transit :created_at]
    :from [:snapshots]
    :order-by [[:created_at :desc]]
    :limit 1}))

(def export-select-queries
  "Map of table keyword to formatted SELECT vector for JSON export."
  {:facts (select-facts-query {:include-archived? true})
   :cv_configs (select-cv-configs-query)
   :snapshots (select-snapshots-query)})

(defn sql-tables->json-map
  "Wraps queried table row vectors into a versioned export map."
  ([tables-map exported-at]
   (sql-tables->json-map schema-version tables-map exported-at))
  ([ver {:keys [facts cv_configs snapshots]} exported-at]
   {:schema-version ver
    :exported-at exported-at
    :tables {:facts (vec (or facts []))
             :cv_configs (vec (or cv_configs []))
             :snapshots (vec (or snapshots []))}}))

(defn db->sql-export
  "Builds export payload from row collections using query-fn [sql]."
  [query-fn exported-at]
  (let [tables (reduce-kv (fn [acc tbl q]
                            (assoc acc tbl (query-fn q)))
                          {}
                          export-select-queries)]
    (sql-tables->json-map tables exported-at)))

(defn valid-export?
  "Validates a SQL tables export map against SqlExportMap."
  [export-map]
  (m/validate SqlExportMap export-map))
