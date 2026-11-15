(ns easy-cv.core.importer-guide-test
  "Unit and property-based tests for Easy CV importer guides and prompts."
  (:require [clojure.edn :as edn]
            [clojure.string :as str]
            [clojure.test :refer [deftest is testing]]
            [clojure.test.check.clojure-test :refer [defspec]]
            [clojure.test.check.generators :as gen]
            [clojure.test.check.properties :as prop]
            [easy-cv.core.importer-guide :as guide]
            [easy-cv.core.io :as io]
            [easy-cv.core.schema :as schema]
            [malli.core :as m]
            #?@(:clj [[babashka.json :as json]]
                :cljs [])))

(defn- parse-json-check
  [s]
  #?(:clj (json/read-str s {:key-fn identity})
     :cljs (js->clj (js/JSON.parse s))))

(def ^:private type->schema
  {:person schema/Person
   :org schema/Organization
   :history schema/HistoryItem
   :skill schema/Skill
   :project schema/Project
   :document schema/Document
   :hobby schema/Hobby})

(defn- fact-valid?
  [fact]
  (if-let [sch (get type->schema (:item/type fact))]
    (m/validate sch fact)
    true))

(defn- guide-and-example-valid?
  [fmt]
  (let [g (guide/format-guide fmt)
        ex (guide/example-payload fmt)]
    (and (m/validate guide/FormatGuide g)
         (seq (:guide/entities g))
         (seq (:guide/tips g))
         (not (str/blank? ex))
         (if (:guide/importable? g)
           (let [opts (if (= fmt :json-ld) {:target :facts} {})
                 res (io/parse-input fmt ex opts)]
             (and (:ok? res)
                  (seq (:io/data res))
                  (every? fact-valid? (:io/data res))))
           true))))

(defspec format-guides-and-examples-pbt 30
  (prop/for-all [fmt (gen/elements guide/supported-formats)]
                (guide-and-example-valid? fmt)))

(deftest json-ld-canonical-and-edn-facts-test
  (testing "JSON-LD starter validates as CanonicalCV and EDN covers entities"
    (let [jld-ex (guide/example-payload :json-ld)
          jld-cv (io/parse-input :json-ld jld-ex)
          edn-res (io/parse-input :edn (guide/example-payload :edn))
          types (set (map :item/type (:io/data edn-res)))]
      (is (:ok? jld-cv))
      (is (m/validate schema/CanonicalCV (:io/data jld-cv)))
      (is (= #{:person :org :history :skill :project :document :hobby}
             types)))))

(deftest exposed-schema-bundle-test
  (testing "Exposed schema bundle provides valid EDN Malli and JSON Schema"
    (let [bundle (guide/exposed-schema-spec)
          edn-parsed (edn/read-string (:schema/edn-spec bundle))
          json-str (guide/export-json-schema-str bundle)
          json-obj (parse-json-check json-str)]
      (is (m/validate guide/ExposedSchemaBundle bundle))
      (is (= schema/schema-version (:schema/version edn-parsed)))
      (is (contains? (:schema/malli edn-parsed) :Person))
      (is (contains? (get json-obj "$defs") "HistoryItem"))
      (is (contains? json-obj "x-schema-org-mapping")))))

(defn- prompt-satisfies-contract?
  [fmt role notes]
  (let [opts {:prompt/target-format fmt
              :prompt/role-focus role
              :prompt/extra-notes notes}
        prompt (guide/build-llm-cv-prompt opts)]
    (and (m/validate guide/PromptOptions opts)
         (string? prompt)
         (str/includes? prompt "YYYY-MM")
         (str/includes? prompt "NEVER fabricate")
         (str/includes? prompt (str/upper-case (name fmt)))
         (or (str/blank? role) (str/includes? prompt (str/trim role)))
         (or (str/blank? notes) (str/includes? prompt (str/trim notes))))))

(defspec llm-cv-prompt-generator-pbt 30
  (prop/for-all [fmt (gen/elements guide/prompt-target-formats)
                 role (gen/one-of [(gen/return nil) gen/string-alphanumeric])
                 notes (gen/one-of [(gen/return nil) gen/string-alphanumeric])]
                (prompt-satisfies-contract? fmt role notes)))

(deftest llm-prompt-key-coverage-test
  (testing "LLM prompt includes format-specific Easy CV or Schema.org keys"
    (let [jld-p (guide/build-llm-cv-prompt {:prompt/target-format :json-ld
                                            :prompt/role-focus "Staff SRE"})
          edn-p (guide/build-llm-cv-prompt {:prompt/target-format :edn})
          yml-p (guide/build-llm-cv-prompt {:prompt/target-format :yaml})]
      (is (str/includes? jld-p "OrganizationRole"))
      (is (str/includes? jld-p "Staff SRE"))
      (is (str/includes? edn-p ":history/start-date"))
      (is (str/includes? yml-p "project/name")))))
