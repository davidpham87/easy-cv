(ns easy-cv.ui.theme-test
  "Unit, property-based, and Hiccup tests for MD3 themes and UI shell."
  (:require [clojure.test :refer [deftest is testing]]
            #?@(:cljs [[clojure.string :as str]
                       [clojure.test.check.clojure-test :refer [defspec]]
                       [clojure.test.check.generators :as gen]
                       [clojure.test.check.properties :as prop]
                       [day8.re-frame.test :as rf-test]
                       [easy-cv.ui.components.common :as common]
                       [easy-cv.ui.components.layout :as layout]
                       [easy-cv.ui.components.navigation :as nav]
                       [easy-cv.ui.core :as ui-core]
                       [easy-cv.ui.theme :as theme]
                       [re-frame.core :as rf]
                       [re-frame.db :as rf-db]])))

(def expected-themes
  "All 12 required chrome theme keywords."
  #{:light :dark :paper :zenburn :nord :nord-light :solarized-dark
    :solarized-light :material-ui :zerodark :gruvbox :catppuccin})

(deftest expected-theme-count-test
  (testing "Catalog defines all 12 expected theme keywords"
    (is (= 12 (count expected-themes)))
    (is (every? expected-themes [:light :dark :paper :zenburn]))))

#?(:cljs
   (defn- expand-hiccup
     "Recursively expands Reagent component vectors for Hiccup inspection."
     [node]
     (cond
       (and (vector? node) (fn? (first node)))
       (let [res (apply (first node) (rest node))]
         (expand-hiccup (if (fn? res) (res) res)))
       (vector? node) (mapv expand-hiccup node)
       (seq? node) (doall (map expand-hiccup node))
       :else node)))

#?(:cljs
   (defn- collect-tags
     "Collects all Hiccup element vectors matching tag keyword."
     [node tag]
     (cond
       (vector? node)
       (let [here (when (= tag (first node)) [node])]
         (into (vec here) (mapcat #(collect-tags % tag) (rest node))))
       (seq? node)
       (vec (mapcat #(collect-tags % tag) node))
       :else [])))

#?(:cljs
   (deftest all-twelve-themes-and-dark-bg-test
     (testing "All 12 themes exist and :dark preserves #2e3440 bg"
       (is (= expected-themes (set (keys theme/theme-presets))))
       (is (= expected-themes (set (map :theme/key theme/theme-catalog))))
       (let [dark (theme/theme-by-id :dark)
             dark-vars (theme/resolve-theme-colors dark)]
         (is (= "#2e3440" (:portal.colors/background dark)))
         (is (= "#2e3440" (get dark-vars "--bg-page")))))))

#?(:cljs
   (defspec all-themes-resolve-md3-vars-pbt 36
     (testing "Every theme resolves standard and MD3 CSS vars cleanly"
       (prop/for-all
        [k (gen/elements (vec expected-themes))]
        (let [palette (theme/theme-by-id k)
              vars (theme/resolve-theme-colors palette)
              req ["--bg-page" "--bg-card" "--text-primary" "--md-surface"
                   "--md-surface-container" "--md-surface-container-high"
                   "--md-primary" "--md-on-primary" "--md-secondary-container"
                   "--md-outline" "--md-elevation-1" "--md-elevation-2"]]
          (and (map? palette)
               (every? #(not (str/blank? (get vars %))) req)))))))

#?(:cljs
   (deftest theme-reframe-switching-and-fallback-test
     (testing "Theme dispatch switches all themes and isolates [:ui :theme]"
       (rf-test/run-test-sync
        (rf/reg-event-db ::seed (fn [_ _] {:archive {:ok true}}))
        (rf/dispatch [::seed])
        (let [sub (rf/subscribe [:easy-cv.ui.theme/current-theme])]
          (doseq [k [:zenburn :catppuccin :nord :gruvbox :dark]]
            (rf/dispatch [:easy-cv.ui.theme/set-theme k])
            (is (= k @sub)))
          (rf/dispatch [:easy-cv.ui.theme/set-theme :unknown-theme])
          (is (= :light @sub))
          (is (= {:ok true} (:archive @rf-db/app-db))))))))

#?(:cljs
   (deftest theme-switcher-controls-render-test
     (testing "Top bar theme-toggle renders 4 quick buttons and 12 options"
       (rf-test/run-test-sync
        (rf/dispatch [:easy-cv.ui.theme/set-theme :zenburn])
        (let [tree (expand-hiccup [ui-core/theme-toggle])
              btns (collect-tags tree :button)
              opts (collect-tags tree :option)
              btn-labels (set (map last btns))]
          (is (= #{"Light" "Dark" "Paper" "Zenburn"} btn-labels))
          (is (= 12 (count opts)))
          (is (= expected-themes
                 (set (map #(keyword (get-in % [1 :value])) opts)))))))))

#?(:cljs
   (deftest md3-primitives-and-e2e-selectors-test
     (testing "MD3 corners/surfaces and E2E navigation selectors preserved"
       (let [btn (common/button {:variant :primary :size :sm} "Save")
             card (common/card {} "Body")
             bar (expand-hiccup [nav/top-bar {:active-label "Easy CV"}])
             menu (expand-hiccup [nav/menu-button])
             nav-btn (first (collect-tags menu :button))
             split (layout/split-view {:ratio :1-2} [:div "L"] [:div "R"])]
         (is (str/includes? (get-in btn [1 :class]) "rounded-full"))
         (is (str/includes? (get-in card [1 :class]) "rounded-2xl"))
         (is (= :div (first bar)))
         (is (= :div (first split)))
         (is (= "Navigation" (get-in nav-btn [1 :title])))
         (is (some #(= "CV Solver" (:nav/label %)) nav/nav-items))))))
