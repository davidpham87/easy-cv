(ns easy-cv.core.schema-test
  "Property-based and boundary tests for Malli schemas and CV generators."
  (:require [clojure.test :refer [deftest is testing]]
            [clojure.test.check :as tc]
            [clojure.test.check.properties :as prop]
            [easy-cv.core.generators :as gens]
            [easy-cv.core.schema :as schema]
            [malli.core :as m]))

(defn- valid-cv-bundle?
  "Return true when generated bundle passes CanonicalCV and CVConfig."
  [{:cv/keys [config canonical]}]
  (and (schema/valid? schema/CVConfig config)
       (schema/valid? schema/CanonicalCV canonical)
       (m/validate schema/CanonicalCV canonical)
       (:schema/valid? (schema/validate schema/CVConfig config))
       (:schema/valid? (schema/validate schema/CanonicalCV canonical))))

(deftest generated-tiers-validate-pbt-test
  (testing "100 generated CVs per tier satisfy CanonicalCV and CVConfig"
    (doseq [tier gens/tiers]
      (let [prop (prop/for-all [bundle (gens/gen-cv-bundle tier)]
                               (valid-cv-bundle? bundle))
            res (tc/quick-check 100 prop)]
        (is (true? (:pass? res))
            (str "Tier " tier " failed PBT: " (pr-str res)))))))

(deftest seeded-determinism-and-open-accretion-test
  (testing "Seeded generation is deterministic and schemas allow extra keys"
    (let [cv-a (gens/generate-cv :junior {:seed 99 :size 15})
          cv-b (gens/generate-cv :junior {:seed 99 :size 15})
          accreted (assoc cv-a :custom/extra-field "preserved")]
      (is (= cv-a cv-b))
      (is (schema/valid? schema/CanonicalCV accreted)))))

(deftest boundary-explain-human-test
  (testing "Invalid inputs return structured human-readable errors"
    (let [bad-style {:theme :minimal :layout :single :font {:heading 42}}
          res (schema/validate schema/CVStyle bad-style)]
      (is (false? (:schema/valid? res)))
      (is (map? (:schema/errors res)))
      (is (some? (schema/explain-human schema/DateISO "not-a-date")))
      (is (nil? (schema/explain-human schema/DateISO "2024-05"))))))
