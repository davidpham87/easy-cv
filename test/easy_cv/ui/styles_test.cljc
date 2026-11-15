(ns easy-cv.ui.styles-test
  (:require [clojure.string :as str]
            [clojure.test :refer [deftest is testing]]
            [clojure.test.check.clojure-test :refer [defspec]]
            [clojure.test.check.generators :as gen]
            [clojure.test.check.properties :as prop]
            [easy-cv.ui.styles :as styles]
            [malli.core :as m]
            #?@(:cljs [[day8.re-frame.test :as rf-test]
                       [easy-cv.ui.components.common :as common]
                       [easy-cv.ui.components.layout :as layout]
                       [easy-cv.ui.components.navigation :as nav]
                       [easy-cv.ui.theme :as theme]
                       [re-frame.core :as rf]
                       [re-frame.db :as rf-db]])))

(def ^:private hex-gen
  (gen/fmap #(apply str "#" %)
            (gen/vector (gen/elements (seq "0123456789abcdef")) 6)))

(defspec all-style-preset-combinations-resolve-test 60
  (testing "Every design, layout, font, density, and chip combo resolves"
    (prop/for-all
     [design (gen/elements (keys styles/design-presets))
      layout-k (gen/elements (keys styles/layout-presets))
      font (gen/elements (keys styles/font-presets))
      density (gen/elements (keys styles/density-presets))
      chip-style (gen/elements (keys styles/chip-presets))
      palette (gen/elements (keys styles/color-palettes))]
     (let [cfg {:design design
                :layout layout-k
                :font font
                :density density
                :chip-style chip-style
                :palette palette}
           resolved (styles/resolve-cv-classes cfg)]
       (and (m/validate styles/StyleConfig cfg)
            (styles/valid-resolved-classes? resolved)
            (= (get styles/chip-presets chip-style) (:badge resolved))
            (every? #(not (str/blank? %)) (vals resolved)))))))

(defspec color-palette-and-custom-colors-resolve-test 50
  (testing "Custom hex colors override palette defaults cleanly"
    (prop/for-all
     [design (gen/elements (keys styles/design-presets))
      palette (gen/elements (keys styles/color-palettes))
      hdr-bg hex-gen
      chip-bg hex-gen
      accent hex-gen]
     (let [colors {:header-bg hdr-bg :chip-bg chip-bg :accent accent}
           cfg {:design design :palette palette :colors colors}
           resolved (styles/resolve-cv-colors cfg)]
       (and (m/validate styles/StyleConfig cfg)
            (m/validate styles/ColorMap resolved)
            (= hdr-bg (:header-bg resolved))
            (= chip-bg (:chip-bg resolved))
            (= accent (:accent resolved))
            (= 7 (count resolved)))))))

(deftest style-bundles-and-overrides-test
  (testing "Predefined bundles, preset counts, and overrides resolve cleanly"
    (is (= 8 (count styles/design-presets)))
    (is (= 5 (count styles/layout-presets)))
    (is (= 5 (count styles/font-presets)))
    (is (= 5 (count styles/chip-presets)))
    (is (= 6 (count styles/color-palettes)))
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
          (is (every? #(contains? theme/theme-presets %)
                      [:light :dark :paper]))
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
