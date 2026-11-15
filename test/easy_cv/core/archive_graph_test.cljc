(ns easy-cv.core.archive-graph-test
  "Malli schema and property-based tests for easy-cv.core.archive-graph."
  (:require #?(:clj [babashka.json :as json])
            [clojure.edn :as edn]
            [clojure.string :as str]
            [clojure.test :refer [deftest is testing]]
            [clojure.test.check.clojure-test :refer [defspec]]
            [clojure.test.check.generators :as gen]
            [clojure.test.check.properties :as prop]
            [easy-cv.core.archive-graph :as ag]
            [easy-cv.core.archive-graph-layout :as layout]
            [easy-cv.core.generators :as gens]
            [malli.core :as m]))

(defn- sort-val
  "Extract comparable sort key matching archive-graph sort semantics."
  [f k]
  (case k
    :type (str/lower-case (name (ag/fact-kind f)))
    :date (ag/fact-date f)
    :tags-count (count (:item/tags f))
    (str/lower-case (str (ag/fact-label f)))))

(defn- monotonic-pair?
  "True when consecutive items [a b] respect sort-by and sort-dir."
  [a b sort-by sort-dir]
  (let [ka (sort-val a sort-by)
        kb (sort-val b sort-by)
        c (compare ka kb)]
    (if (= :desc sort-dir) (>= c 0) (<= c 0))))

(defspec sort-facts-preserves-items-and-orders-pbt 40
  (prop/for-all
   [archive (gens/gen-archive :junior)
    sort-by (gen/elements [:type :title :date :tags-count])
    sort-dir (gen/elements [:asc :desc])]
   (let [facts (:archive/tx-data archive)
         s1 (ag/sort-facts facts sort-by sort-dir)
         s2 (ag/sort-facts facts sort-by sort-dir)
         pairs (partition 2 1 s1)]
     (and (m/validate ag/SortBy sort-by)
          (m/validate ag/SortDir sort-dir)
          (= s1 s2)
          (= (frequencies facts) (frequencies s1))
          (every? (fn [[a b]] (monotonic-pair? a b sort-by sort-dir))
                  pairs)))))

(defspec extract-tag-facets-counts-and-order-pbt 40
  (prop/for-all
   [archive (gens/gen-archive :mid)]
   (let [facts (:archive/tx-data archive)
         facets (ag/extract-tag-facets facts)
         total-from-facets (reduce + 0 (map :count facets))
         total-raw (reduce + 0 (map #(count (ag/fact-tags %)) facts))
         order-keys (map (fn [{:keys [tag count]}] [(- count) (name tag)])
                         facets)]
     (and (every? #(m/validate ag/TagFacet %) facets)
          (= total-from-facets total-raw)
          (= order-keys (sort order-keys))))))

(defn- valid-graph-bounds?
  "True when all graph nodes stay inside 760x380 and edges are non-dangling."
  [graph]
  (let [nodes (:graph/nodes graph)
        edges (:graph/edges graph)
        nids (set (map :node/id nodes))]
    (and (m/validate ag/NetworkGraph graph)
         (every? #(and (<= 0.0 (:node/x %) 760.0)
                       (<= 0.0 (:node/y %) 380.0))
                 nodes)
         (every? #(and (contains? nids (:edge/from %))
                       (contains? nids (:edge/to %)))
                 edges))))

(defspec network-graph-layout-and-filter-invariants-pbt 30
  (prop/for-all
   [tier (gen/elements [:student :junior :mid])
    layout (gen/elements [:clustered :radial])
    fkind (gen/elements [:all :work :skill :project])]
   (let [arch (gens/generate-archive tier {:seed 31 :size 16})
         facts (:archive/tx-data arch)
         cfgs (:archive/configs arch)
         sel (:item/uuid (first facts))
         g1 (ag/archive->network-graph
             facts cfgs {:filter-kind fkind :selected-id sel :layout layout})
         g2 (ag/archive->network-graph
             facts cfgs {:filter-kind fkind :selected-id sel :layout layout})]
     (and (= g1 g2)
          (valid-graph-bounds? g1)
          (every? #(or (= :all fkind) (= fkind (:node/kind %)))
                  (:graph/nodes g1))))))

(defn- parse-json-ok?
  "Return true when s is valid non-blank JSON object text."
  [s]
  (let [parsed #?(:clj (json/read-str s)
                  :cljs (js->clj (js/JSON.parse s)))]
    (map? parsed)))

(defspec inspect-entity-formats-roundtrip-pbt 30
  (prop/for-all
   [archive (gens/gen-archive :junior)]
   (let [facts (:archive/tx-data archive)]
     (every? (fn [entity]
               (let [res (ag/inspect-entity->formats entity)
                     edn-back (edn/read-string (:edn res))]
                 (and (m/validate ag/InspectedFormats res)
                      (= (dissoc entity :db/id) edn-back)
                      (parse-json-ok? (:json res)))))
             (take 6 facts)))))

(deftest network-graph-links-and-selection-test
  (testing "Graph links person, history, org, project, skill, cfg, and tags"
    (let [arch (gens/generate-archive :junior {:seed 19 :size 15})
          facts (:archive/tx-data arch)
          cfgs (:archive/configs arch)
          sel-id (:item/uuid (:archive/person arch))
          graph (ag/archive->network-graph
                 facts cfgs {:filter-kind :all :selected-id sel-id})
          rels (set (map :edge/rel (:graph/edges graph)))
          selected (filter :node/selected? (:graph/nodes graph))]
      (is (valid-graph-bounds? graph))
      (is (= 1 (count selected)))
      (is (= (str sel-id) (:node/id (first selected))))
      (is (contains? rels :person-history))
      (is (contains? rels :at-org))
      (is (contains? rels :uses-skill))
      (is (contains? rels :cv-person))
      (is (contains? rels :shared-tag))))
  (testing "UI sort aliases :kind and :label match :type and :title"
    (let [facts (:archive/tx-data (gens/generate-archive :junior {:seed 9}))]
      (is (= (ag/sort-facts facts :type :asc)
             (ag/sort-facts facts :kind :asc)))
      (is (= (ag/sort-facts facts :title :desc)
             (ag/sort-facts facts :label :desc)))
      (is (= 18.0 (layout/node-radius :person))))))
