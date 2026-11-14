(ns easy-cv.ui.views-app-test
  (:require [clojure.test :refer [deftest is testing]]
            [clojure.test.check.clojure-test :refer [defspec]]
            [clojure.test.check.generators :as gen]
            [clojure.test.check.properties :as prop]
            [datascript.core :as d]
            [day8.re-frame.test :as rf-test]
            [easy-cv.core.db :as db]
            [easy-cv.fx.datascript :as fx-ds]
            [easy-cv.fx.router :as fx-router]
            [easy-cv.ui.core :as ui-core]
            [easy-cv.ui.theme :as theme]
            [easy-cv.ui.views.editor :as editor]
            [easy-cv.ui.views.preview :as preview]
            [re-frame.core :as rf]
            [re-frame.db :as rf-db]))

(defn- reset-demo-conn!
  []
  (d/reset-conn! fx-ds/conn (db/create-db))
  (fx-ds/seed-demo-archive! {:tier :student :seed 11 :size 10}))

(defspec editor-and-style-pure-property-test 15
  (testing "Editor parser rejects blank text and style merge preserves keys"
    (prop/for-all [design (gen/elements [:academic :minimal :modern :classic])
                   layout (gen/elements [:single-col :two-col :sidebar])
                   skill-str (gen/not-empty gen/string-alphanumeric)]
                  (let [edn-txt (pr-str [{:item/uuid (random-uuid)
                                          :skill/name skill-str}])
                        parsed (editor/parse-editor-source :edn edn-txt)
                        blank (editor/parse-editor-source :edn "   ")
                        styled (preview/apply-style-to-canonical
                                {:cv/style {:theme :academic}}
                                {:design design :layout layout})]
                    (and (:editor/ok? parsed)
                         (not (:editor/ok? blank))
                         (= design (get-in styled [:cv/style :theme]))
                         (= layout (get-in styled [:cv/style :layout])))))))

(defn- seed-app-slices!
  [slices]
  (rf/reg-event-db ::seed-slices (fn [_ _] slices))
  (rf/dispatch [::seed-slices]))

(deftest editor-slice-isolation-and-import-test
  (testing "Editor events isolate [:editor] and transact valid facts"
    (reset-demo-conn!)
    (rf-test/run-test-sync
     (seed-app-slices! {:archive {:filter :all} :render {:source-tab :html}})
     (let [before-cnt (count (db/all-facts @fx-ds/conn))
           skill-edn (pr-str [{:item/uuid (random-uuid)
                               :skill/name "ClojureScript-Test"}])]
       (rf/dispatch [::editor/set-format :edn])
       (rf/dispatch [::editor/set-text "{:broken"])
       (is (seq @(rf/subscribe [::editor/diagnostics])))
       (rf/dispatch [::editor/set-text skill-edn])
       (is (empty? @(rf/subscribe [::editor/diagnostics])))
       (rf/dispatch [::editor/apply-import])
       (is (= (inc before-cnt) (count (db/all-facts @fx-ds/conn))))
       (is (= {:filter :all} (:archive @rf-db/app-db)))
       (is (= {:source-tab :html} (:render @rf-db/app-db)))))))

(deftest render-slice-isolation-and-export-test
  (testing "Preview events isolate [:render] and render all export formats"
    (reset-demo-conn!)
    (rf-test/run-test-sync
     (seed-app-slices! {:archive {:filter :skills} :editor {:format :yaml}})
     (rf/dispatch [::preview/apply-bundle :exec-brief])
     (rf/dispatch [::preview/set-style-opt :design :modern])
     (rf/dispatch [::preview/set-source-tab :latex])
     (rf/dispatch [::preview/set-preview-mode :source])
     (let [outs @(rf/subscribe [::preview/rendered-outputs])]
       (is (string? (:html outs)))
       (is (string? (get-in outs [:latex :tex])))
       (is (string? (:markdown outs)))
       (is (string? (:json-ld outs)))
       (is (map? (:site outs)))
       (is (= {:filter :skills} (:archive @rf-db/app-db)))
       (is (= {:format :yaml} (:editor @rf-db/app-db)))))))

(deftest app-shell-render-smoke-test
  (testing "App shell dispatches views, themes, and router slices cleanly"
    (reset-demo-conn!)
    (ui-core/init-malli-registry!)
    (rf-test/run-test-sync
     (rf/dispatch [::ui-core/boot])
     (rf/dispatch [::theme/set-theme :dark])
     (rf/dispatch [::fx-router/navigated (fx-router/match-path "/editor")])
     (is (true? (get-in @rf-db/app-db [:ui :booted?])))
     (is (= :dark @(rf/subscribe [::theme/current-theme])))
     (is (= :editor @(rf/subscribe [::fx-router/route-name])))
     (is (vector? (ui-core/root-view)))
     (is (vector? (ui-core/view :archive)))
     (is (vector? (ui-core/view :solver)))
     (is (vector? (ui-core/view :editor)))
     (is (vector? (ui-core/view :preview))))))
