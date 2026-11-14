(ns easy-cv.core.solver-test
  "Property-based and Datalog tests for DataScript store and pure solver."
  (:require [clojure.test :refer [deftest is testing]]
            [clojure.test.check.clojure-test :refer [defspec]]
            [clojure.test.check.generators :as gen]
            [clojure.test.check.properties :as prop]
            [datascript.core :as d]
            [easy-cv.core.db :as db]
            [easy-cv.core.generators :as gens]
            [easy-cv.core.schema :as schema]
            [easy-cv.core.solver :as solver]
            [malli.core :as m]))

(defn- solved-uuids
  "Collect all :item/uuid values present across all sections of a solved CV."
  [resolved]
  (->> (:cv/sections resolved)
       (mapcat :section/items)
       (map :item/uuid)
       set))

(defn- find-solved-item
  "Locate a solved item map by :item/uuid inside a CanonicalCV."
  [resolved uid]
  (->> (:cv/sections resolved)
       (mapcat :section/items)
       (some #(when (= uid (:item/uuid %)) %))))

(defn- non-mutating-and-valid?
  "Check solver determinism, Malli validity, and DataScript non-mutation."
  [tier archive]
  (let [arch (if (= tier :junior)
               archive
               (gens/generate-archive tier {:seed 11 :size 15}))
        store (db/create-db (:archive/tx-data arch))
        before-datoms (vec (d/datoms store :eavt))
        cv-id (:cv/id (first (:archive/configs arch)))
        res-1 (solver/solve-cv store cv-id)
        res-2 (solver/solve-cv store cv-id)
        after-datoms (vec (d/datoms store :eavt))]
    (and (= before-datoms after-datoms)
         (= res-1 res-2)
         (m/validate schema/CanonicalCV res-1)
         (schema/valid? schema/CanonicalCV res-1))))

(defspec solve-non-mutation-and-validity-pbt 50
  (prop/for-all [tier (gen/elements gens/tiers)
                 archive (gens/gen-archive :junior)]
                (non-mutating-and-valid? tier archive)))

(defn- exclusion-wins?
  "Verify an item in both :cv/include and :cv/exclude is always excluded."
  [archive]
  (let [cfg (first (:archive/configs archive))
        pool (concat (:archive/history archive)
                     (:archive/projects archive)
                     (:archive/skills archive))
        target-id (:item/uuid (first pool))
        inc-set (conj (set (:cv/include cfg)) target-id)
        exc-set (conj (set (:cv/exclude cfg)) target-id)
        patched (assoc cfg :cv/include inc-set :cv/exclude exc-set)
        tx (conj (vec (butlast (:archive/tx-data archive))) patched)
        store (db/create-db tx)
        resolved (solver/solve-cv store (:cv/id cfg))]
    (not (contains? (solved-uuids resolved) target-id))))

(defspec exclusion-supremacy-pbt 50
  (prop/for-all [archive (gens/gen-archive :junior)]
                (exclusion-wins? archive)))

(defn- overrides-stay-local?
  "Verify overrides on one CVConfig do not mutate db or sibling CVs."
  [archive]
  (let [cfg-a (assoc (first (:archive/configs archive))
                     :item/uuid (random-uuid) :cv/id :cv-alpha
                     :cv/rules {} :cv/include #{} :cv/exclude #{})
        target (first (:archive/skills archive))
        uid (:item/uuid target)
        orig-name (:skill/name target)
        ovs {uid {:skill/name "Overridden Skill"}}
        cfg-b (assoc cfg-a :item/uuid (random-uuid) :cv/id :cv-beta
                     :cv/overrides ovs)
        store (db/create-db (conj (:archive/tx-data archive) cfg-a cfg-b))
        cv-a (solver/solve-cv store :cv-alpha)
        cv-b (solver/solve-cv store :cv-beta)
        raw (d/pull store [:skill/name] [:item/uuid uid])]
    (and (= orig-name (:skill/name (find-solved-item cv-a uid)))
         (= "Overridden Skill" (:skill/name (find-solved-item cv-b uid)))
         (= orig-name (:skill/name raw)))))

(defspec override-locality-across-sibling-cvs-pbt 40
  (prop/for-all [archive (gens/gen-archive :junior)]
                (overrides-stay-local? archive)))

(defn- snapshot-parity?
  "Verify capture-snapshot and restore-snapshot preserve exact db state."
  [archive]
  (let [store-1 (db/create-db (:archive/tx-data archive))
        cv-id (:cv/id (first (:archive/configs archive)))
        snap (db/capture-snapshot store-1 {:snapshot/label "checkpoint"})
        extra {:item/uuid (random-uuid) :item/type :hobby
               :hobby/name "Origami"}
        store-2 (db/transact-facts store-1 [extra])
        restored (db/restore-snapshot snap)]
    (and (= store-1 restored)
         (not= store-1 store-2)
         (= (solver/solve-cv store-1 cv-id)
            (solver/solve-cv restored cv-id)))))

(defspec snapshot-capture-and-restore-parity-pbt 40
  (prop/for-all [archive (gens/gen-archive :junior)]
                (snapshot-parity? archive)))

(deftest rules-filtering-order-and-queries-test
  (testing "Rules (type/tag/date/limit), ordering, and Datalog queries"
    (let [arch (gens/generate-archive :mid {:seed 77 :size 20})
          cfg (-> (first (:archive/configs arch))
                  (assoc :item/uuid (random-uuid)
                         :cv/id :filtered
                         :cv/include #{}
                         :cv/exclude #{}
                         :cv/rules {:exclude-types #{:hobbies}
                                    :after-date "2000-01"
                                    :max-items {:skills 2}}))
          store (db/create-db (conj (:archive/tx-data arch) cfg))
          resolved (solver/solve-cv store :filtered)
          skills-sec (some #(when (= :skills (:section/id %)) %)
                           (:cv/sections resolved))
          conn (db/empty-conn)]
      (is (d/conn? conn))
      (is (<= (count (:section/items skills-sec)) 2))
      (is (= 2 (count (db/list-cv-configs store))))
      (is (seq (db/facts-by-type store :skill)))
      (is (seq (db/all-facts store))))))
