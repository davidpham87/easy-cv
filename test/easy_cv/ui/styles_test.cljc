(ns easy-cv.ui.styles-test
  (:require [clojure.string :as str]
            [clojure.test :refer [deftest is testing]]
            [clojure.test.check.clojure-test :refer [defspec]]
            [clojure.test.check.generators :as gen]
            [clojure.test.check.properties :as prop]
            [easy-cv.ui.styles :as styles]
            #?@(:cljs [[day8.re-frame.test :as rf-test]
                       [easy-cv.ui.components.common :as common]
                       [easy-cv.ui.components.layout :as layout]
                       [easy-cv.ui.components.navigation :as nav]
                       [easy-cv.ui.theme :as theme]
                       [re-frame.core :as rf]
                       [re-frame.db :as rf-db]])))

(defspec all-style-preset-combinations-resolve-test 40
  (testing "Every design, layout, font, and density combo resolves cleanly"
    (prop/for-all
     [design (gen/elements (keys styles/design-presets))
      layout-k (gen/elements (keys styles/layout-presets))
      font (gen/elements (keys styles/font-presets))
      density (gen/elements (keys styles/density-presets))]
     (let [resolved (styles/resolve-cv-classes
                     {:design design
                      :layout layout-k
                      :font font
                      :density density})]
       (and (styles/valid-resolved-classes? resolved)
            (every? #(not (str/blank? %)) (vals resolved)))))))

(deftest style-bundles-and-overrides-test
  (testing "Predefined bundles and class overrides resolve cleanly"
    (doseq [[_ bundle] styles/style-bundles]
      (let [resolved (styles/resolve-cv-classes
                      (assoc bundle :class-overrides {:title "tracking-wide"}))]
        (is (styles/valid-resolved-classes? resolved))
        (is (str/includes? (:title resolved) "tracking-wide"))))))

#?(:cljs
   (deftest theme-slice-isolation-test
     (testing "Setting theme updates only [:ui :theme] without cross-slice leak"
       (rf-test/run-test-sync
        (rf/reg-event-db
         ::seed-db
         (fn [_ _]
           {:archive {:fact/count 3}
            :solver {:cv/active :academic}
            :editor {:editor/mode :edn}
            :ui {:theme :light}}))
        (rf/dispatch [::seed-db])
        (let [sub (rf/subscribe [:easy-cv.ui.theme/current-theme])]
          (is (= #{:light :dark :paper} (set (keys theme/theme-presets))))
          (is (= :light @sub))
          (rf/dispatch [:easy-cv.ui.theme/set-theme :dark])
          (is (= :dark @sub))
          (rf/dispatch [:easy-cv.ui.theme/set-theme :paper])
          (is (= :paper @sub))
          (is (= {:fact/count 3} (:archive @rf-db/app-db)))
          (is (= {:cv/active :academic} (:solver @rf-db/app-db)))
          (is (= {:editor/mode :edn} (:editor @rf-db/app-db))))))))

#?(:cljs
   (deftest ui-components-hiccup-smoke-test
     (testing "Common, layout, and navigation primitives produce valid hiccup"
       (let [btn (common/button {:variant :primary :size :sm} "Save")
             bdg (common/badge {} "Clojure")
             split (layout/split-view {:ratio :1-2} [:div "L"] [:div "R"])
             bar (nav/top-bar {:active-label "CV Solver"})]
         (is (= :button (first btn)))
         (is (= :span (first bdg)))
         (is (= :div (first split)))
         (is (= :div (first bar)))))))
