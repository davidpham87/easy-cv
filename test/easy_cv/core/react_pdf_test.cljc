(ns easy-cv.core.react-pdf-test
  "Unit and property-based tests for pure React-PDF CV document mapping."
  (:require [clojure.string :as str]
            [clojure.test :refer [deftest is testing]]
            [clojure.test.check.clojure-test :refer [defspec]]
            [clojure.test.check.generators :as gen]
            [clojure.test.check.properties :as prop]
            [easy-cv.core.react-pdf :as rp]
            [malli.core :as m]))

(def ^:private gen-style
  (gen/hash-map
   :design (gen/elements [:academic :nordic :solarized :minimal :modern])
   :font (gen/elements [:sans :serif :mono])
   :page-size (gen/elements [:a4 :letter])))

(def ^:private gen-canonical
  (gen/let [pname (gen/not-empty gen/string-alphanumeric)
            email (gen/not-empty gen/string-alphanumeric)
            role (gen/not-empty gen/string-alphanumeric)
            skill (gen/not-empty gen/string-alphanumeric)]
    {:cv/id "cv-pbt"
     :cv/person {:person/name pname
                 :person/email (str email "@example.org")
                 :person/location "Paris"}
     :cv/sections [{:section/id :work
                    :section/items [{:history/title role
                                     :history/start-date "2024"}]}
                   {:section/id :skills
                    :section/items
                    [{:skill/name skill :skill/level "High"}]}]}))

(defspec canonical-to-pdf-model-satisfies-schema-pbt 60
  (prop/for-all [canon gen-canonical
                 style gen-style]
                (let [doc (rp/canonical->pdf-model canon style)
                      expected (get-in canon [:cv/person :person/name])]
                  (and (m/validate rp/ReactPdfDoc doc)
                       (= (:pdf/person-name doc) expected)
                       (= 2 (count (:pdf/sections doc)))))))

(deftest nil-canonical-returns-nil-model
  (testing "nil safety and fallback section formatting"
    (let [line (rp/format-item-lines :other {:x 1})]
      (is (nil? (rp/canonical->pdf-model nil {})))
      (is (str/includes? (:pdf/title line) ":x")))))
