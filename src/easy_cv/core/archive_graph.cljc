(ns easy-cv.core.archive-graph
  "Pure .cljc sorting, tag faceting, network graph layout, and inspection."
  (:require #?(:clj [babashka.json :as json])
            #?(:clj [clojure.pprint :as pp]
               :cljs [cljs.pprint :as pp])
            [clojure.set :as set]
            [clojure.string :as str]
            [easy-cv.core.archive-graph-layout :as layout]
            [malli.core :as m]))

(def graph-width
  "SVG viewBox width for the archive network graph."
  760.0)

(def graph-height
  "SVG viewBox height for the archive network graph."
  380.0)

(def graph-viewbox
  "SVG viewBox attribute string."
  "0 0 760 380")

(def kind-colors
  "Deterministic Material-inspired palette per archive entity kind."
  {:person "#6366f1" :cv/config "#ec4899" :work "#2563eb"
   :education "#0ea5e9" :volunteer "#14b8a6" :org "#f59e0b"
   :project "#8b5cf6" :skill "#10b981" :document "#f97316"
   :hobby "#84cc16" :other "#64748b"})

(def SortBy
  "Malli schema for supported archive sort keys."
  [:enum :type :title :date :tags-count])

(def SortDir
  "Malli schema for sort direction."
  [:enum :asc :desc])

(def TagFacet
  "Malli schema for a tag facet entry with count."
  [:map
   [:tag :keyword]
   [:count pos-int?]])

(def ^:private NumSchema
  [:or :double :int])

(def GraphNode
  "Malli schema for a positioned network graph node."
  [:map
   [:node/id :string]
   [:node/uuid {:optional true} [:maybe :uuid]]
   [:node/kind :keyword]
   [:node/label :string]
   [:node/sublabel {:optional true} :string]
   [:node/x NumSchema]
   [:node/y NumSchema]
   [:node/r NumSchema]
   [:node/color :string]
   [:node/selected? {:optional true} :boolean]
   [:node/tags {:optional true} [:vector :keyword]]
   [:node/entity :map]])

(def GraphEdge
  "Malli schema for a positioned relationship edge."
  [:map
   [:edge/id :string]
   [:edge/from :string]
   [:edge/to :string]
   [:edge/rel :keyword]
   [:edge/label :string]
   [:edge/x1 NumSchema]
   [:edge/y1 NumSchema]
   [:edge/x2 NumSchema]
   [:edge/y2 NumSchema]
   [:edge/highlighted? {:optional true} :boolean]])

(def NetworkGraph
  "Malli schema for archive->network-graph output."
  [:map
   [:graph/nodes [:vector GraphNode]]
   [:graph/edges [:vector GraphEdge]]
   [:graph/viewbox :string]
   [:graph/width NumSchema]
   [:graph/height NumSchema]
   [:graph/layout {:optional true} :keyword]])

(def InspectedFormats
  "Malli schema for pretty-printed EDN and JSON inspection strings."
  [:map
   [:edn :string]
   [:json :string]])

(defn fact-kind
  "Infer primary display category keyword for any archive entity map."
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
  "Return concise primary label string for an archive entity."
  [fact]
  (or (:history/title fact)
      (:project/name fact)
      (:skill/name fact)
      (:document/name fact)
      (:hobby/name fact)
      (:person/name fact)
      (:org/name fact)
      (:cv/title fact)
      (some-> (:cv/id fact) str)
      (str (:item/uuid fact))))

(defn fact-summary
  "Return secondary summary text for an archive entity."
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

(defn fact-date
  "Return primary ISO date string for sorting and display, or empty string."
  [fact]
  (or (:history/start-date fact)
      (:project/start-date fact)
      (:document/date fact)
      (:history/end-date fact)
      (:project/end-date fact)
      ""))

(defn- normalize-tag
  "Coerce tag value to a lowercase keyword."
  [t]
  (if (keyword? t) t (keyword (str/lower-case (str t)))))

(defn fact-tags
  "Return sorted vector of keyword tags attached to fact."
  [fact]
  (->> (:item/tags fact)
       (map normalize-tag)
       distinct
       (sort-by name)
       vec))

(defn- sort-primary-val
  "Extract primary sort key for fact given sort-by keyword."
  [fact sort-by]
  (case sort-by
    :type (str/lower-case (name (fact-kind fact)))
    :date (str (fact-date fact))
    :tags-count (count (:item/tags fact))
    (str/lower-case (str (fact-label fact)))))

(defn- normalize-sort-by
  "Map UI sort key aliases (:kind, :label) to canonical SortBy keys."
  [sort-by]
  (case sort-by
    :kind :type
    :label :title
    sort-by))

(defn sort-facts
  "Deterministically sort facts by :type/:kind, :title/:label, :date, tags."
  [facts sort-by sort-dir]
  (let [norm-by (normalize-sort-by sort-by)
        by (if (m/validate SortBy norm-by) norm-by :title)
        dir (if (m/validate SortDir sort-dir) sort-dir :asc)
        cmp (fn [a b]
              (let [ka (sort-primary-val a by)
                    kb (sort-primary-val b by)
                    c (if (= :desc dir) (compare kb ka) (compare ka kb))]
                (if (zero? c)
                  (compare (str (:item/uuid a) (fact-label a))
                           (str (:item/uuid b) (fact-label b)))
                  c)))]
    (vec (sort cmp facts))))

(defn extract-tag-facets
  "Compute deterministic tag frequency facets sorted by count desc, tag asc."
  [facts]
  (->> facts
       (mapcat fact-tags)
       frequencies
       (map (fn [[tag cnt]] {:tag tag :count cnt}))
       (sort-by (fn [{:keys [tag count]}] [(- count) (name tag)]))
       vec))

(defn- entity-id
  "Return canonical string identifier for an entity map."
  [e]
  (or (some-> (:item/uuid e) str)
      (some-> (:cv/id e) str)
      (str (hash e))))

(defn- collect-entities
  "Merge facts and cv-configs into deduplicated [entities lookups] pair."
  [facts cv-configs]
  (let [all (concat facts cv-configs)
        by-id (reduce (fn [acc e]
                        (update acc (entity-id e) merge e))
                      {}
                      all)
        ents (vec (vals by-id))
        db-map (into {} (keep (fn [e]
                                (when-let [did (:db/id e)]
                                  [did (entity-id e)]))
                              ents))
        sk-map (into {} (keep (fn [e]
                                (when-let [sn (:skill/name e)]
                                  [sn (entity-id e)]))
                              ents))]
    [ents {:by-id by-id :db-map db-map :sk-map sk-map}]))

(defn- resolve-ref-id
  "Resolve UUID, entity map, :db/id map, or skill name to canonical node id."
  [ref {:keys [by-id db-map sk-map]}]
  (cond
    (uuid? ref) (let [s (str ref)] (when (contains? by-id s) s))
    (string? ref) (or (when (contains? by-id ref) ref) (get sk-map ref))
    (map? ref) (or (some-> (:item/uuid ref) str)
                   (get db-map (:db/id ref)))
    :else nil))

(defn- entity->node
  "Project an entity map into an unpositioned GraphNode map."
  [e sel-str]
  (let [nid (entity-id e)
        kind (fact-kind e)]
    {:node/id nid
     :node/uuid (:item/uuid e)
     :node/kind kind
     :node/label (fact-label e)
     :node/sublabel (fact-summary e)
     :node/x 0.0
     :node/y 0.0
     :node/r (layout/node-radius kind)
     :node/color (get kind-colors kind "#64748b")
     :node/selected? (= nid sel-str)
     :node/tags (fact-tags e)
     :node/entity (dissoc e :db/id)}))

(defn- make-edge
  "Construct an unpositioned GraphEdge map."
  [from to rel label]
  {:edge/id (str from "--" (name rel) "--" to)
   :edge/from from
   :edge/to to
   :edge/rel rel
   :edge/label label})

(defn- person-history-edges
  "Link person nodes to history items (:work, :education, :volunteer)."
  [nodes]
  (let [persons (filter #(= :person (:node/kind %)) nodes)
        hist (filter #(contains? #{:work :education :volunteer}
                                 (:node/kind %))
                     nodes)]
    (for [p persons h hist]
      (make-edge (:node/id p) (:node/id h) :person-history "career"))))

(defn- history-org-edges
  "Link history items to their referenced organizations."
  [nodes lookups]
  (for [n nodes
        :let [org-ref (get-in n [:node/entity :history/organization])
              org-id (when org-ref (resolve-ref-id org-ref lookups))]
        :when (and org-id (not= org-id (:node/id n)))]
    (make-edge (:node/id n) org-id :at-org "at")))

(defn- project-skill-edges
  "Link projects to their referenced skills."
  [nodes lookups]
  (for [n nodes
        sref (get-in n [:node/entity :project/skills])
        :let [sid (resolve-ref-id sref lookups)]
        :when (and sid (not= sid (:node/id n)))]
    (make-edge (:node/id n) sid :uses-skill "uses")))

(defn- cfg-matches-fact?
  "True when fact passes CVConfig :cv/rules when :cv/include is empty."
  [cfg fact]
  (let [rules (:cv/rules cfg)
        tags (set (:item/tags fact))
        t-any (set (:tags-any rules))
        t-all (set (:tags-all rules))]
    (and (not (:item/archived? fact))
         (or (empty? t-any) (boolean (some t-any tags)))
         (or (empty? t-all) (set/subset? t-all tags)))))

(defn- cfg-target-ids
  "Collect fact node ids targeted by a CVConfig node."
  [cfg-e nodes lookups]
  (let [exc (set (keep #(resolve-ref-id % lookups) (:cv/exclude cfg-e)))
        direct-refs (concat (:cv/include cfg-e) (:cv/history cfg-e)
                            (:cv/skills cfg-e) (:cv/projects cfg-e)
                            (:cv/documents cfg-e) (:cv/hobbies cfg-e))
        direct-ids (set (keep #(resolve-ref-id % lookups) direct-refs))
        rule-ids (when (and (empty? direct-ids) (seq (:cv/rules cfg-e)))
                   (->> nodes
                        (remove #(contains? #{:person :org :cv/config}
                                            (:node/kind %)))
                        (filter #(cfg-matches-fact? cfg-e (:node/entity %)))
                        (map :node/id)
                        (take 6)
                        set))]
    (set/difference (if (seq direct-ids) direct-ids (or rule-ids #{})) exc)))

(defn- cv-config-edges
  "Link CVConfig nodes to their person and included facts."
  [nodes lookups]
  (for [n nodes
        :when (= :cv/config (:node/kind n))
        :let [cfg (:node/entity n)
              pid (some-> (:cv/person cfg) (resolve-ref-id lookups))
              tids (cfg-target-ids cfg nodes lookups)]
        edge (concat
              (when pid [(make-edge (:node/id n) pid :cv-person "profile")])
              (map #(make-edge (:node/id n) % :cv-includes "includes") tids))]
    edge))

(defn- shared-tag-edges
  "Link consecutive nodes sharing a tag, merging multi-tag pairs."
  [nodes]
  (let [eligible (remove #(= :cv/config (:node/kind %)) nodes)
        by-tag (reduce (fn [acc n]
                         (reduce #(update %1 %2 (fnil conj []) (:node/id n))
                                 acc (:node/tags n)))
                       {} eligible)
        pairs (for [[tag ids] by-tag
                    :let [sids (vec (sort ids))]
                    :when (>= (count sids) 2)
                    [a b] (partition 2 1 sids)]
                [[a b] tag])
        grouped (reduce (fn [acc [pair tag]]
                          (update acc pair (fnil conj #{}) tag))
                        {} pairs)]
    (map (fn [[[a b] tags]]
           (let [lbl (str/join ", " (map #(str "#" (name %)) (sort tags)))]
             (make-edge a b :shared-tag lbl)))
         grouped)))

(defn- build-edges
  "Assemble deduplicated relationship edges across all nodes."
  [nodes lookups]
  (let [raw (concat (person-history-edges nodes)
                    (history-org-edges nodes lookups)
                    (project-skill-edges nodes lookups)
                    (cv-config-edges nodes lookups)
                    (shared-tag-edges nodes))]
    (->> raw
         (reduce (fn [acc e]
                   (if (contains? acc (:edge/id e))
                     acc
                     (assoc acc (:edge/id e) e)))
                 {})
         vals
         (sort-by :edge/id)
         vec)))

(defn archive->network-graph
  "Build deterministic SVG network graph {:graph/nodes :graph/edges ...}."
  [facts cv-configs {:keys [filter-kind selected-id layout]}]
  (let [sel-str (some-> selected-id str)
        [ents lookups] (collect-entities facts cv-configs)
        raw-nodes (mapv #(entity->node % sel-str) ents)
        vis-nodes (if (or (nil? filter-kind) (= :all filter-kind))
                    raw-nodes
                    (filterv #(= filter-kind (:node/kind %)) raw-nodes))
        raw-edges (layout/filter-visible-edges
                   (build-edges raw-nodes lookups) vis-nodes)
        coords (layout/layout-coords vis-nodes (or layout :clustered))
        nodes (layout/apply-node-coords vis-nodes coords)
        node-map (into {} (map (juxt :node/id identity)) nodes)]
    {:graph/nodes nodes
     :graph/edges (layout/attach-edge-coords raw-edges node-map sel-str)
     :graph/viewbox graph-viewbox
     :graph/width graph-width
     :graph/height graph-height
     :graph/layout (or layout :clustered)}))

(defn- json-normalize
  "Normalize Clojure types (UUID, keyword, set) for clean JSON encoding."
  [v]
  (cond
    (uuid? v) (str v)
    (keyword? v) (subs (str v) 1)
    (set? v) (->> v (map json-normalize) (sort-by str) vec)
    (map? v) (into (sorted-map)
                   (map (fn [[k val]]
                          [(if (keyword? k) (subs (str k) 1) (str k))
                           (json-normalize val)]))
                   v)
    (sequential? v) (mapv json-normalize v)
    :else v))

#?(:clj
   (defn- clj-pretty-json
     "Pretty-print a normalized JSON value with 2-space indentation."
     [v indent]
     (let [pad (apply str (repeat indent "  "))
           npad (apply str (repeat (inc indent) "  "))]
       (cond
         (map? v)
         (if (empty? v)
           "{}"
           (let [rows (map (fn [[k val]]
                             (str npad (json/write-str k) ": "
                                  (clj-pretty-json val (inc indent))))
                           v)]
             (str "{\n" (str/join ",\n" rows) "\n" pad "}")))
         (vector? v)
         (if (empty? v)
           "[]"
           (let [rows (map #(str npad (clj-pretty-json % (inc indent))) v)]
             (str "[\n" (str/join ",\n" rows) "\n" pad "]")))
         :else (json/write-str v)))))

(defn inspect-entity->formats
  "Return {:edn ... :json ...} pretty-printed strings for an entity map."
  [entity]
  (let [clean (if (map? entity) (dissoc entity :db/id) entity)
        edn-str (str/trim (with-out-str (pp/pprint clean)))
        norm (json-normalize clean)
        json-str #?(:clj (clj-pretty-json norm 0)
                    :cljs (js/JSON.stringify (clj->js norm) nil 2))]
    {:edn edn-str
     :json json-str}))
