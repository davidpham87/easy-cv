(ns easy-cv.ui.views-core-test
  "Re-frame slice isolation, solver workflow, and cv-blocks render tests."
  (:require [clojure.test :refer [deftest is testing]]
            [clojure.test.check.clojure-test :refer [defspec]]
            [clojure.test.check.generators :as gen]
            [clojure.test.check.properties :as prop]
            [datascript.core :as d]
            [day8.re-frame.test :as rf-test]
            [easy-cv.core.generators :as cv-gen]
            [easy-cv.core.honeysql :as honeysql]
            [easy-cv.core.schema :as schema]
            [easy-cv.fx.datascript :as fx-ds]
            [easy-cv.ui.components.cv-blocks :as cv-blocks]
            [easy-cv.ui.views.archive :as archive]
            [easy-cv.ui.views.solver :as solver]
            [malli.core :as m]
            [re-frame.core :as rf]
            [re-frame.db :as rf-db]))

(defn- reset-test-conn!
  "Reset shared DataScript conn and return an empty db."
  []
  (d/reset-conn! fx-ds/conn @(d/create-conn schema/ds-schema)))

(defn- contains-hiccup-tag?
  "Recursively check whether rendered Hiccup tree contains tag keyword."
  [tree tag]
  (cond
    (vector? tree) (or (= tag (first tree))
                       (boolean (some #(contains-hiccup-tag? % tag)
                                      (rest tree))))
    (seq? tree) (boolean (some #(contains-hiccup-tag? % tag) tree))
    :else false))

(defn- seed-slices-db!
  "Register a test-only seeding event with sibling app-db slices."
  []
  (rf/reg-event-db
   ::seed-slices
   (fn [_ _]
     {:editor {:editor/mode :edn}
      :ui {:theme :light}}))
  (rf/dispatch [::seed-slices]))

(deftest archive-and-solver-slice-isolation-test
  (testing "Archive and Solver events never leak across app-db slices"
    (reset-test-conn!)
    (rf-test/run-test-sync
     (seed-slices-db!)
     (rf/dispatch [::archive/init])
     (rf/dispatch [::solver/init])
     (let [solver-before (:solver @rf-db/app-db)]
       (rf/dispatch [::archive/set-filter-type :skill])
       (rf/dispatch [::archive/set-filter-tag "clojure"])
       (rf/dispatch [::archive/set-search-text "re-frame"])
       (rf/dispatch [::archive/set-selected-tier :senior])
       (rf/dispatch [::archive/update-quick-fact :fact/title "Malli"])
       (is (= solver-before (:solver @rf-db/app-db)))
       (is (= {:editor/mode :edn} (:editor @rf-db/app-db)))
       (is (= {:theme :light} (:ui @rf-db/app-db))))
     (let [archive-before (:archive @rf-db/app-db)]
       (rf/dispatch [::solver/select-cv :custom])
       (rf/dispatch [::solver/set-new-cv-title "Staff CV"])
       (rf/dispatch [::solver/toggle-preview])
       (rf/dispatch [::solver/set-override-draft :override/text "Edited"])
       (is (= archive-before (:archive @rf-db/app-db)))
       (is (= {:editor/mode :edn} (:editor @rf-db/app-db)))
       (is (= {:theme :light} (:ui @rf-db/app-db)))))))

(deftest archive-facts-snapshots-and-sql-export-test
  (testing "Archive loads tier, adds quick fact, snapshots, and exports SQL"
    (reset-test-conn!)
    (let [downloaded (atom nil)]
      (rf-test/run-test-sync
       (rf/reg-fx :io/download-file #(reset! downloaded %))
       (rf/dispatch [::archive/init])
       (rf/dispatch [::archive/load-tier-archive :junior])
       (rf/dispatch [::archive/set-snapshot-label "Before quick fact"])
       (rf/dispatch [::archive/capture-snapshot])
       (let [snaps @(rf/subscribe [::archive/snapshots])
             snap-id (:snapshot/id (first snaps))
             cnt-before (count @(rf/subscribe [::archive/all-facts]))]
         (rf/dispatch [::archive/update-quick-fact :fact/title "Datomic"])
         (rf/dispatch [::archive/add-quick-fact])
         (is (= (inc cnt-before) (count @(rf/subscribe [::archive/all-facts]))))
         (rf/dispatch [::archive/export-sql-json])
         (is (string? (:io/content @downloaded)))
         (is (= "application/json" (:io/mime-type @downloaded)))
         (rf/dispatch [::archive/restore-snapshot snap-id])
         (is (= cnt-before (count @(rf/subscribe [::archive/all-facts])))))))))

(deftest solver-target-rules-overrides-and-order-test
  (testing "Solver toggles facts, edits overrides, and reorders sections"
    (reset-test-conn!)
    (rf-test/run-test-sync
     (rf/dispatch [::archive/init])
     (rf/dispatch [::solver/init])
     (rf/dispatch [::archive/load-tier-archive :junior])
     (let [items @(rf/subscribe [::solver/candidate-items])
           first-uid (:item/uuid (first items))
           cv-id @(rf/subscribe [::solver/active-cv-id])]
       (rf/dispatch [::solver/exclude-fact cv-id first-uid])
       (rf/dispatch [::solver/include-fact cv-id first-uid])
       (rf/dispatch [::solver/set-override cv-id first-uid
                     :history/title "Principal Consultant"])
       (rf/dispatch [::solver/move-section cv-id :education :up])
       (let [solved @(rf/subscribe [::solver/solved-cv])]
         (is (m/validate schema/CanonicalCV solved))
         (is (= :education (:section/id (first (:cv/sections solved))))))))))

(deftest cv-blocks-layouts-and-sections-render-test
  (testing "cv-preview renders single-col, two-col, sidebar, and volunteer"
    (let [base-cv (cv-gen/generate-cv :junior {:seed 7})
          vol-sec {:section/id :volunteer
                   :section/items [{:item/uuid (random-uuid)
                                    :history/type :volunteer
                                    :history/title "OSS Maintainer"
                                    :history/start-date "2023-01"}]}
          rich-cv (update base-cv :cv/sections conj vol-sec)]
      (is (= :div (first (cv-blocks/cv-preview nil))))
      (doseq [layout-k [:single-col :two-col :sidebar]]
        (let [hiccup (cv-blocks/cv-preview
                      (assoc-in rich-cv [:cv/style :layout] layout-k))]
          (is (= :article (first hiccup)))
          (is (contains-hiccup-tag? hiccup :header))
          (is (contains-hiccup-tag? hiccup :section))
          (when (not= :single-col layout-k)
            (is (contains-hiccup-tag? hiccup :aside))))))))

(defspec generated-cvs-render-and-export-cleanly 24
  (prop/for-all
   [tier (gen/elements cv-gen/tiers)
    layout-k (gen/elements [:single-col :two-col :sidebar])
    seed (gen/choose 1 500)]
   (let [cv (assoc-in (cv-gen/generate-cv tier {:seed seed})
                      [:cv/style :layout] layout-k)
         hiccup (cv-blocks/cv-preview cv)
         export-map (archive/build-sql-export-payload
                     (d/empty-db schema/ds-schema)
                     [] 1000 "2026-01-01T00:00Z")]
     (and (m/validate schema/CanonicalCV cv)
          (= :article (first hiccup))
          (honeysql/valid-export? export-map)))))
