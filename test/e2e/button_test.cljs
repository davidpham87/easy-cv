(ns e2e.button-test
  "Playwright E2E tests exercising real UI button clicks in Easy CV."
  (:require [cljs.test :refer [async deftest is testing]]
            [clojure.test.check.clojure-test :refer [defspec]]
            [clojure.test.check.generators :as gen]
            [clojure.test.check.properties :as prop]
            [e2e.support :as support]))

(defn- root-bg-var
  "Reads --bg-page CSS custom property from documentElement."
  [^js page]
  (.evaluate page
             (fn []
               (.. js/document -documentElement -style
                   (getPropertyValue "--bg-page")))))

(defn- wait-for-dark-bg!
  "Waits until Re-frame flushes --bg-page to the dark palette hex."
  [^js page]
  (.waitForFunction page
                    (fn []
                      (= "#2e3440"
                         (.. js/document -documentElement -style
                             (getPropertyValue "--bg-page"))))
                    nil
                    #js {:timeout 5000}))

(defn- click-theme-flow!
  "Navigates to app, clicks Dark theme button, and returns theme state."
  [^js page url]
  (let [^js btn (.getByRole page "button" #js {:name "Dark"})]
    (-> (.goto page url #js {:waitUntil "domcontentloaded"})
        (.then #(.waitFor btn #js {:timeout 8000}))
        (.then #(root-bg-var page))
        (.then (fn [before]
                 (-> (.click btn)
                     (.then #(wait-for-dark-bg! page))
                     (.then #(root-bg-var page))
                     (.then (fn [after]
                              {:before before :after after}))))))))

(deftest click-dark-theme-button-e2e-test
  (testing "Clicking the Dark button in top bar updates the active theme"
    (async done
           (-> (support/with-app-page
                 (fn [page url errors]
                   (-> (click-theme-flow! page url)
                       (.then (fn [res]
                                (is (support/valid-page-result? res))
                                (is (= "#2e3440" (:after res)))
                                (is (not= (:before res) (:after res)))
                                (is (empty? @errors)))))))
               (.catch (fn [err] (is (nil? err))))
               (.finally done)))))

(defn- click-nav-solver-flow!
  "Opens the Navigation dropdown button and clicks CV Solver."
  [^js page url]
  (let [^js nav-btn (.locator page "[title='Navigation']")
        ^js solver-link (.getByRole page "link" #js {:name "CV Solver"})
        ^js heading (.getByText page "Multi-CV Solver")]
    (-> (.goto page url #js {:waitUntil "domcontentloaded"})
        (.then #(.waitFor nav-btn #js {:timeout 8000}))
        (.then #(.click nav-btn))
        (.then #(.click solver-link))
        (.then #(.waitFor heading #js {:timeout 5000}))
        (.then #(.isVisible heading)))))

(deftest click-navigation-button-to-solver-e2e-test
  (testing "Clicking Navigation button lets user navigate to CV Solver"
    (async done
           (-> (support/with-app-page
                 (fn [page url errors]
                   (-> (click-nav-solver-flow! page url)
                       (.then (fn [visible?]
                                (is (true? visible?))
                                (is (empty? @errors)))))))
               (.catch (fn [err] (is (nil? err))))
               (.finally done)))))

(defspec e2e-page-result-contract-pbt 30
  (prop/for-all [before (gen/elements ["#f8fafc" "#fdf6e3"])
                 after (gen/elements ["#2e3440"])]
                (let [res {:before before :after after}]
                  (and (support/valid-page-result? res)
                       (not= (:before res) (:after res))))))
