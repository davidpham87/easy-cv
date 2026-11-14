(ns easy-cv.fx.datascript-test
  (:require [clojure.test :refer [deftest is testing]]
            [clojure.test.check.clojure-test :refer [defspec]]
            [clojure.test.check.generators :as gen]
            [clojure.test.check.properties :as prop]
            [datascript.core :as d]
            [day8.re-frame.test :as rf-test]
            [easy-cv.core.db :as db]
            [easy-cv.fx.datascript :as fx-ds]
            [easy-cv.fx.router :as fx-router]
            [easy-cv.ui.components.editor :as editor]
            [re-frame.core :as rf]
            [re-frame.db :as rf-db]))

(defn- reset-conn-empty!
  []
  (d/reset-conn! fx-ds/conn (db/create-db)))

(defspec datascript-transact-fx-property-test 15
  (testing "transact-fx! bumps ds-version and persists facts"
    (prop/for-all [uid gen/uuid
                   skill-name (gen/not-empty gen/string-alphanumeric)]
                  (reset-conn-empty!)
                  (let [v0 @fx-ds/ds-version
                        label (str skill-name "-" uid)]
                    (fx-ds/transact-fx! [{:item/uuid uid :skill/name label}])
                    (and (> @fx-ds/ds-version v0)
                         (= 1 (count (db/all-facts @fx-ds/conn))))))))

(deftest datascript-seed-and-snapshot-test
  (testing "seed-demo-archive! seeds once and snapshot restore works"
    (reset-conn-empty!)
    (let [seeded (fx-ds/seed-demo-archive! {:tier :student :seed 7})
          snap (db/capture-snapshot @fx-ds/conn)
          cnt-before (count (db/all-facts @fx-ds/conn))]
      (is (some? seeded))
      (is (pos? cnt-before))
      (is (nil? (fx-ds/seed-demo-archive! {:tier :student :seed 8})))
      (fx-ds/transact-fx! [{:item/uuid (random-uuid)
                            :hobby/name "Rowing"}])
      (is (= (inc cnt-before) (count (db/all-facts @fx-ds/conn))))
      (fx-ds/restore-snapshot-fx! snap)
      (is (= cnt-before (count (db/all-facts @fx-ds/conn)))))))

(deftest router-fragment-and-isolation-test
  (testing "Router matches all fragment routes without leaking app-db"
    (rf-test/run-test-sync
     (rf/reg-event-db
      ::seed-slices
      (fn [_ _]
        {:archive {:fact/count 4}
         :solver {:cv/active :student}
         :ui {:theme :light}}))
     (rf/dispatch [::seed-slices])
     (let [route-sub (rf/subscribe [::fx-router/route-name])]
       (is (= :archive @route-sub))
       (doseq [[path expected] [["/archive" :archive]
                                ["/solver" :solver]
                                ["/editor" :editor]
                                ["/preview" :preview]
                                ["/styles" :styles]]]
         (rf/dispatch [::fx-router/navigated (fx-router/match-path path)])
         (is (= expected @route-sub)))
       (is (= {:fact/count 4} (:archive @rf-db/app-db)))
       (is (= {:cv/active :student} (:solver @rf-db/app-db)))
       (is (= {:theme :light} (:ui @rf-db/app-db)))))))

(deftest polymorphic-editor-component-test
  (testing "editor-panel renders hiccup for formats and diagnostics"
    (let [view (editor/editor-panel
                {:editor/format :edn
                 :editor/text "{:cv/id :student}"
                 :editor/diagnostics
                 [{:diag/severity :error :diag/line 1 :diag/message "Bad"}]})]
      (is (= editor/render-editor (first view)))
      (let [rendered (editor/render-editor
                      {:editor/format :latex
                       :editor/text "\\section{Skills}"})]
        (is (= :div (first rendered)))))))
