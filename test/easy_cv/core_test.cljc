(ns easy-cv.core-test
  (:require [clojure.test :refer [testing]]
            [clojure.test.check.clojure-test :refer [defspec]]
            [clojure.test.check.properties :as prop]
            [malli.core :as m]
            [malli.generator :as mg]))

(def ^:private SmokeFact
  [:map
   [:fact/id :uuid]
   [:fact/label [:string {:min 1 :max 40}]]])

(defspec phase0-malli-test-check-smoke 25
  (testing "Malli + test.check work in both JVM and CLJS"
    (prop/for-all [fact (mg/generator SmokeFact)]
                  (m/validate SmokeFact fact))))
