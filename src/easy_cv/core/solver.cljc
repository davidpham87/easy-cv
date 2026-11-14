(ns easy-cv.core.solver
  "Pure multi-CV solver turning DataScript facts + CVConfig into CanonicalCV."
  (:require [clojure.set :as set]
            [clojure.walk :as walk]
            [datascript.core :as d]
            [easy-cv.core.db :as db]
            [easy-cv.core.schema :as schema]))

(def default-section-order
  "Default section ordering when :cv/section-order is omitted."
  [:work :education :projects :skills :documents :hobbies])

(def default-style
  "Fallback CVStyle when config omits style properties."
  {:theme :modern-minimal
   :layout :single-column
   :font {:heading "Inter" :body "Inter" :mono "IBM Plex Mono"}})

(defn- as-db
  "Return immutable DataScript DB value from either a DB or conn."
  [db-or-conn]
  (if (d/conn? db-or-conn) @db-or-conn db-or-conn))

(defn- strip-db-ids
  "Recursively remove DataScript :db/id keys from pulled entity trees."
  [form]
  (walk/postwalk #(if (map? %) (dissoc % :db/id) %) form))

(defn- resolve-style
  "Build a valid CVStyle map from config :cv/style or legacy keys."
  [cfg]
  (let [s (:cv/style cfg)
        font-src (or (:font s) (:cv/font cfg))]
    {:theme (or (:theme s) (:cv/theme cfg) (:theme default-style))
     :layout (or (:layout s) (:cv/layout cfg) (:layout default-style))
     :font (merge (:font default-style)
                  (when (map? font-src) font-src))}))

(defn- pad-iso-start
  "Pad partial ISO date string to YYYY-MM-DD lower bound."
  [s]
  (case (count s)
    4 (str s "-01-01")
    7 (str s "-01")
    s))

(defn- pad-iso-end
  "Pad partial ISO date string to YYYY-MM-DD upper bound."
  [s]
  (case (count s)
    4 (str s "-12-31")
    7 (str s "-31")
    s))

(defn- item-types
  "Return set of type keywords identifying a candidate fact."
  [item]
  (let [base (cond-> #{}
               (:item/type item) (conj (:item/type item))
               (:history/type item) (conj :history (:history/type item))
               (:document/type item) (conj :document (:document/type item))
               (:project/name item) (conj :project :projects)
               (:skill/name item) (conj :skill :skills)
               (:hobby/name item) (conj :hobby :hobbies))]
    (cond-> base
      (= :work (:history/type item)) (conj :work)
      (= :education (:history/type item)) (conj :education)
      (= :volunteer (:history/type item)) (conj :volunteer))))

(defn- item-start-date
  "Return primary start or publication date string if present."
  [item]
  (or (:history/start-date item)
      (:project/start-date item)
      (:document/date item)))

(defn- item-end-date
  "Return effective end date string, using 9999-12-31 for ongoing items."
  [item]
  (or (:history/end-date item)
      (:project/end-date item)
      (:document/date item)
      (when (or (:history/start-date item) (:project/start-date item))
        "9999-12-31")))

(defn- matches-tags?
  "Check :tags-any and :tags-all predicates against item tags."
  [tags {:keys [tags-any tags-all]}]
  (and (or (empty? tags-any) (boolean (some (set tags-any) tags)))
       (or (empty? tags-all) (set/subset? (set tags-all) tags))))

(defn- matches-types?
  "Check :include-types and :exclude-types against item type set."
  [types {:keys [include-types exclude-types]}]
  (and (or (empty? include-types) (boolean (some (set include-types) types)))
       (or (empty? exclude-types) (not-any? (set exclude-types) types))))

(defn- matches-dates?
  "Check :after-date and :before-date bounds against item dates."
  [item {:keys [after-date before-date]}]
  (let [start-d (item-start-date item)
        end-d (item-end-date item)]
    (and (or (nil? after-date)
             (nil? end-d)
             (>= (compare (pad-iso-end end-d) (pad-iso-start after-date)) 0))
         (or (nil? before-date)
             (nil? start-d)
             (<= (compare (pad-iso-start start-d) (pad-iso-end before-date))
                 0)))))

(defn- matches-rules?
  "Return true when non-archived item satisfies all :cv/rules predicates."
  [item rules]
  (let [tags (set (:item/tags item))
        types (item-types item)]
    (and (matches-tags? tags rules)
         (matches-types? types rules)
         (matches-dates? item rules))))

(defn- gather-candidates
  "Pull all non-person, non-org, non-config facts with resolved refs."
  [dval]
  (->> (d/q '[:find [(pull ?e [* {:history/organization [*]
                                  :project/skills [*]}]) ...]
              :where [?e :item/uuid _]]
            dval)
       (mapv strip-db-ids)
       (remove #(or (contains? % :cv/id)
                    (contains? % :person/email)
                    (contains? % :org/name)))
       vec))

(defn- resolve-person
  "Pull target :cv/person entity and apply overrides and summary override."
  [dval cfg]
  (let [pref (:cv/person cfg)
        eid (cond
              (map? pref) (:db/id pref)
              (uuid? pref) [:item/uuid pref]
              :else (ffirst (d/q '[:find ?e :where [?e :person/email _]] dval)))
        raw (when eid (strip-db-ids (d/pull dval '[*] eid)))
        ov (when raw (get-in cfg [:cv/overrides (:item/uuid raw)]))
        merged (cond-> (merge raw ov)
                 (:cv/summary-override cfg)
                 (assoc :person/summary (:cv/summary-override cfg)))]
    (when-not (map? merged)
      (throw (ex-info "No person entity found for CVConfig"
                      {:cv/id (:cv/id cfg)})))
    merged))

(defn- legacy-included-uuids
  "Collect item UUIDs from legacy :cv/history..:cv/hobbies ref lists."
  [dval cfg]
  (let [ref-keys [:cv/history :cv/skills :cv/projects
                  :cv/documents :cv/hobbies]]
    (->> ref-keys
         (mapcat #(get cfg %))
         (keep (fn [r]
                 (cond
                   (uuid? r) r
                   (:item/uuid r) (:item/uuid r)
                   (:db/id r) (:item/uuid (d/pull dval [:item/uuid]
                                                  (:db/id r))))))
         set)))

(defn- keep-item?
  "Apply exclusion supremacy, explicit includes, and rule filters."
  [item inc-set exc-set rules]
  (let [uid (:item/uuid item)]
    (cond
      (contains? exc-set uid) false
      (contains? inc-set uid) true
      (:item/archived? item) false
      :else (matches-rules? item rules))))

(defn- item-section
  "Assign a fact item to its target CanonicalCV section keyword."
  [item sec-order]
  (cond
    (contains? item :history/title)
    (let [ht (:history/type item)]
      (if (and (= :volunteer ht) (some #{:volunteer} sec-order))
        :volunteer
        (if (= :education ht) :education :work)))
    (contains? item :project/name) :projects
    (contains? item :skill/name) :skills
    (contains? item :document/name) :documents
    (contains? item :hobby/name) :hobbies
    :else :other))

(defn- item-label
  "Return human label string for deterministic tie-break sorting."
  [item]
  (or (:history/title item)
      (:project/name item)
      (:skill/name item)
      (:document/name item)
      (:hobby/name item)
      ""))

(defn- item-sort-key
  "Build deterministic sort key vector for an item within a section."
  [rank-map item]
  (let [uid (:item/uuid item)
        rank (get rank-map uid 999999999)
        active? (and (or (:history/start-date item) (:project/start-date item))
                     (nil? (:history/end-date item))
                     (nil? (:project/end-date item)))
        date-str (or (item-start-date item) "0000-00")]
    [rank (if active? 0 1) date-str (item-label item) (str uid)]))

(defn- compare-sort-keys
  "Compare two item sort keys (date descending, rank/label/uuid ascending)."
  [[r1 a1 d1 l1 u1] [r2 a2 d2 l2 u2]]
  (let [c1 (compare r1 r2)]
    (if-not (zero? c1)
      c1
      (let [c2 (compare a1 a2)]
        (if-not (zero? c2)
          c2
          (let [c3 (compare d2 d1)]
            (if-not (zero? c3)
              c3
              (let [c4 (compare l1 l2)]
                (if-not (zero? c4) c4 (compare u1 u2))))))))))

(defn- order-and-limit-section
  "Apply explicit :cv/order, deterministic sort, and :max-items budget."
  [sec-id items cfg]
  (let [order-vec (get-in cfg [:cv/order sec-id] [])
        rank-map (zipmap order-vec (range))
        sorted (vec (sort #(compare-sort-keys (item-sort-key rank-map %1)
                                              (item-sort-key rank-map %2))
                          items))
        limit (get-in cfg [:cv/rules :max-items sec-id])]
    (if (pos-int? limit)
      (vec (take limit sorted))
      sorted)))

(defn- apply-overrides
  "Merge per-CV overrides onto an item without mutating source facts."
  [overrides item]
  (if-let [ov (get overrides (:item/uuid item))]
    (merge item ov)
    item))

(defn- build-sections
  "Group, sort, and budget kept items according to :cv/section-order."
  [kept sec-order cfg]
  (let [grouped (group-by #(item-section % sec-order) kept)]
    (mapv (fn [sid]
            {:section/id sid
             :section/items (order-and-limit-section
                             sid (get grouped sid []) cfg)})
          sec-order)))

(defn solve-cv
  "Purely solve cv-id against immutable DataScript db into a CanonicalCV."
  [db cv-id]
  (let [dval (as-db db)
        cfg (db/find-cv-config dval cv-id)]
    (when-not cfg
      (throw (ex-info "CVConfig not found" {:cv/id cv-id})))
    (let [sec-order (or (seq (:cv/section-order cfg)) default-section-order)
          inc-set (into (set (:cv/include cfg))
                        (legacy-included-uuids dval cfg))
          exc-set (set (:cv/exclude cfg))
          kept (->> (gather-candidates dval)
                    (filter #(keep-item? % inc-set exc-set (:cv/rules cfg)))
                    (mapv #(apply-overrides (:cv/overrides cfg) %)))]
      {:cv/id (:cv/id cfg)
       :cv/title (:cv/title cfg)
       :cv/schema-version schema/schema-version
       :cv/style (resolve-style cfg)
       :cv/person (resolve-person dval cfg)
       :cv/sections (build-sections kept sec-order cfg)})))

(defn solve-all
  "Purely solve every CVConfig present in db."
  [db]
  (let [dval (as-db db)]
    (mapv #(solve-cv dval (:cv/id %)) (db/list-cv-configs dval))))

(defn diff-cvs
  "Compare two solved CanonicalCVs by :item/uuid and overridden fields."
  [cv-a cv-b]
  (let [items-a (into {} (map (juxt :item/uuid identity))
                      (mapcat :section/items (:cv/sections cv-a)))
        items-b (into {} (map (juxt :item/uuid identity))
                      (mapcat :section/items (:cv/sections cv-b)))
        ids-a (set (keys items-a))
        ids-b (set (keys items-b))
        shared (set/intersection ids-a ids-b)]
    {:diff/only-a (set/difference ids-a ids-b)
     :diff/only-b (set/difference ids-b ids-a)
     :diff/overridden (set (filter #(not= (get items-a %) (get items-b %))
                                   shared))}))
