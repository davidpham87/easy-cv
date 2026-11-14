(ns easy-cv.core.db
  "Pure DataScript fact store, Datalog queries, and snapshot versioning."
  (:require [datascript.core :as d]
            [easy-cv.core.schema :as schema]))

(def ^:private ref-attrs
  "DataScript attributes whose :db/valueType is :db.type/ref."
  #{:history/organization :project/skills :cv/person :cv/history
    :cv/skills :cv/projects :cv/documents :cv/hobbies})

(defn- as-db
  "Return immutable DataScript DB value from either a DB or conn."
  [db-or-conn]
  (if (d/conn? db-or-conn) @db-or-conn db-or-conn))

(defn- uuid->lookup-ref
  "Convert raw UUIDs to DataScript [:item/uuid uuid] lookup refs."
  [v]
  (cond
    (uuid? v) [:item/uuid v]
    (sequential? v) (mapv #(if (uuid? %) [:item/uuid %] %) v)
    :else v))

(defn- normalize-fact-map
  "Normalize ref-typed UUID fields in a fact map for DataScript tx."
  [fact]
  (if (map? fact)
    (reduce-kv (fn [acc k v]
                 (assoc acc k (if (contains? ref-attrs k)
                                (uuid->lookup-ref v)
                                v)))
               {}
               fact)
    fact))

(defn create-db
  "Create an immutable DataScript DB value, optionally seeded with facts."
  ([] (d/empty-db schema/ds-schema))
  ([facts]
   (d/db-with (create-db) (mapv normalize-fact-map facts))))

(defn empty-conn
  "Create a mutable DataScript connection initialized with ds-schema."
  []
  (d/create-conn schema/ds-schema))

(defn transact-facts
  "Purely apply facts to an immutable DataScript db, returning new db."
  [db facts]
  (d/db-with (as-db db) (mapv normalize-fact-map facts)))

(defn capture-snapshot
  "Capture an immutable, serializable snapshot map from a DataScript db."
  ([db] (capture-snapshot db {}))
  ([db {:snapshot/keys [id parent label created-at]}]
   (let [snap-db (d/serializable (as-db db))]
     (cond-> {:snapshot/id (or id (random-uuid))
              :snapshot/schema-version schema/schema-version
              :snapshot/db snap-db}
       parent (assoc :snapshot/parent parent)
       label (assoc :snapshot/label label)
       created-at (assoc :snapshot/created-at created-at)))))

(defn migrate-snapshot
  "Apply additive migrations when loading a snapshot from older versions."
  [snap-db from-version to-version]
  (if (< (or from-version 1) to-version)
    snap-db
    snap-db))

(defn restore-snapshot
  "Restore an immutable DataScript db value from a captured snapshot."
  [snapshot]
  (let [from-v (:snapshot/schema-version snapshot)
        payload (or (:snapshot/db snapshot) snapshot)
        migrated (migrate-snapshot payload from-v schema/schema-version)]
    (d/from-serializable migrated)))

(defn all-facts
  "Query all entities with :item/uuid from db as clean maps."
  [db]
  (->> (d/q '[:find [(pull ?e [*]) ...]
              :where [?e :item/uuid _]]
            (as-db db))
       (mapv #(dissoc % :db/id))
       (sort-by (comp str :item/uuid))
       vec))

(def ^:private type-marker-attr
  "Fallback defining attribute per canonical entity type."
  {:person :person/email
   :org :org/name
   :history :history/title
   :skill :skill/name
   :project :project/name
   :document :document/name
   :hobby :hobby/name
   :cv/config :cv/id})

(defn- matches-fact-type?
  "Return true when pulled entity matches requested item-type."
  [fact item-type]
  (or (= item-type (:item/type fact))
      (= item-type (:history/type fact))
      (= item-type (:document/type fact))
      (when-let [attr (get type-marker-attr item-type)]
        (contains? fact attr))))

(defn facts-by-type
  "Query facts matching item-type (e.g. :history, :work, :skill, :cv/config)."
  [db item-type]
  (->> (all-facts db)
       (filterv #(matches-fact-type? % item-type))))

(defn list-cv-configs
  "Query all CVConfig entities sorted deterministically by :cv/id."
  [db]
  (->> (d/q '[:find [(pull ?e [*]) ...]
              :where [?e :cv/id _]]
            (as-db db))
       (mapv #(dissoc % :db/id))
       (sort-by (comp str :cv/id))
       vec))

(defn find-cv-config
  "Lookup a CVConfig entity by :cv/id or :item/uuid, returning nil if absent."
  [db cv-id]
  (let [dval (as-db db)
        by-id (d/pull dval '[*] [:cv/id cv-id])
        found (if (some? (:cv/id by-id))
                by-id
                (when (uuid? cv-id)
                  (d/pull dval '[*] [:item/uuid cv-id])))]
    (when (some? (:cv/id found))
      (dissoc found :db/id))))
