(ns easy-cv.core.schema
  "DataScript fact schema and Malli boundary contracts for Easy CV."
  (:require [clojure.test.check.generators :as gen]
            [malli.core :as m]
            [malli.error :as me]))

(def schema-version
  "Current canonical CV and snapshot schema version."
  1)

(def ds-schema
  "Accreted DataScript schema preserving all wiki/docs/schema.md keys."
  {:item/uuid            {:db/unique :db.unique/identity}
   :item/type            {}
   :item/tags            {:db/cardinality :db.cardinality/many}
   :item/archived?       {}

   :person/email         {:db/unique :db.unique/identity}
   :person/name          {}
   :person/phone         {}
   :person/website       {}
   :person/summary       {}
   :person/location      {}
   :person/links         {:db/cardinality :db.cardinality/many}

   :org/name             {}
   :org/type             {}
   :org/location         {}
   :org/url              {}

   :history/organization {:db/valueType :db.type/ref}
   :history/type         {}
   :history/title        {}
   :history/subtitle     {}
   :history/start-date   {}
   :history/end-date     {}
   :history/description  {}
   :history/highlights   {:db/cardinality :db.cardinality/many}
   :history/location     {}

   :skill/name           {:db/unique :db.unique/identity}
   :skill/category       {}
   :skill/level          {}
   :skill/description    {}

   :project/name         {}
   :project/description  {}
   :project/url          {}
   :project/start-date   {}
   :project/end-date     {}
   :project/skills       {:db/cardinality :db.cardinality/many
                          :db/valueType   :db.type/ref}

   :document/name        {}
   :document/url         {}
   :document/type        {}
   :document/date        {}
   :document/citekey     {:db/unique :db.unique/identity}
   :document/doi         {}
   :document/venue       {}
   :document/authors     {:db/cardinality :db.cardinality/many}
   :document/bibtex      {}
   :document/csl-json    {}

   :hobby/name           {}
   :hobby/description    {}

   :cv/id                {:db/unique :db.unique/identity}
   :cv/title             {}
   :cv/person            {:db/valueType :db.type/ref}
   :cv/history           {:db/cardinality :db.cardinality/many
                          :db/valueType   :db.type/ref}
   :cv/skills            {:db/cardinality :db.cardinality/many
                          :db/valueType   :db.type/ref}
   :cv/projects          {:db/cardinality :db.cardinality/many
                          :db/valueType   :db.type/ref}
   :cv/documents         {:db/cardinality :db.cardinality/many
                          :db/valueType   :db.type/ref}
   :cv/hobbies           {:db/cardinality :db.cardinality/many
                          :db/valueType   :db.type/ref}
   :cv/include           {}
   :cv/exclude           {}
   :cv/rules             {}
   :cv/overrides         {}
   :cv/order             {}
   :cv/section-order     {}
   :cv/style             {}
   :cv/theme             {}
   :cv/layout            {}
   :cv/font              {}
   :cv/summary-override  {}

   :snapshot/id          {:db/unique :db.unique/identity}
   :snapshot/parent      {}
   :snapshot/label       {}
   :snapshot/created-at  {}
   :snapshot/schema-version {}})

(defn- pad-two
  "Zero-pad integers < 10 to two digits."
  [n]
  (if (< n 10) (str "0" n) (str n)))

(def ^:private date-iso-gen
  "Generator for ISO partial dates YYYY-MM or YYYY-MM-DD."
  (gen/let [y (gen/choose 1985 2026)
            m (gen/choose 1 12)
            d (gen/one-of [(gen/return nil) (gen/choose 1 28)])]
    (if d
      (str y "-" (pad-two m) "-" (pad-two d))
      (str y "-" (pad-two m)))))

(def DateISO
  "ISO date string matching YYYY, YYYY-MM, or YYYY-MM-DD."
  [:and
   {:gen/gen date-iso-gen}
   :string
   [:re #"^\d{4}(-\d{2}(-\d{2})?)?$"]])

(def ^:private TagColl
  [:or [:set :keyword] [:vector :keyword]])

(def ^:private NonEmptyStr
  [:string {:min 1 :max 160}])

(def Person
  "Malli schema for the CV owner profile."
  [:map
   [:item/uuid {:optional true} :uuid]
   [:item/tags {:optional true} TagColl]
   [:item/archived? {:optional true} :boolean]
   [:person/name NonEmptyStr]
   [:person/email NonEmptyStr]
   [:person/phone {:optional true} :string]
   [:person/website {:optional true} :string]
   [:person/summary {:optional true} :string]
   [:person/location {:optional true} :string]
   [:person/links {:optional true} [:vector :string]]])

(def Organization
  "Malli schema for a company, university, lab, or community."
  [:map
   [:item/uuid {:optional true} :uuid]
   [:org/name NonEmptyStr]
   [:org/type [:enum :company :school :lab :community]]
   [:org/location {:optional true} :string]
   [:org/url {:optional true} :string]])

(def HistoryItem
  "Malli schema for work, education, or volunteer timeline entries."
  [:map
   [:item/uuid {:optional true} :uuid]
   [:item/tags {:optional true} TagColl]
   [:item/archived? {:optional true} :boolean]
   [:history/type [:enum :work :education :volunteer]]
   [:history/title NonEmptyStr]
   [:history/subtitle {:optional true} :string]
   [:history/organization {:optional true} [:or :uuid Organization]]
   [:history/start-date DateISO]
   [:history/end-date {:optional true} [:maybe DateISO]]
   [:history/description {:optional true} :string]
   [:history/highlights {:optional true} [:vector :string]]
   [:history/location {:optional true} :string]])

(def Skill
  "Malli schema for a technical or domain skill."
  [:map
   [:item/uuid {:optional true} :uuid]
   [:item/tags {:optional true} TagColl]
   [:item/archived? {:optional true} :boolean]
   [:skill/name NonEmptyStr]
   [:skill/category {:optional true} :string]
   [:skill/level {:optional true} :string]
   [:skill/description {:optional true} :string]])

(def Project
  "Malli schema for a software, research, or coursework project."
  [:map
   [:item/uuid {:optional true} :uuid]
   [:item/tags {:optional true} TagColl]
   [:item/archived? {:optional true} :boolean]
   [:project/name NonEmptyStr]
   [:project/description {:optional true} :string]
   [:project/url {:optional true} :string]
   [:project/start-date {:optional true} DateISO]
   [:project/end-date {:optional true} [:maybe DateISO]]
   [:project/skills {:optional true} [:vector [:or :uuid :string Skill]]]])

(def Document
  "Malli schema for a publication, diploma, reference, or talk."
  [:map
   [:item/uuid {:optional true} :uuid]
   [:item/tags {:optional true} TagColl]
   [:item/archived? {:optional true} :boolean]
   [:document/name NonEmptyStr]
   [:document/url {:optional true} :string]
   [:document/type [:enum :publication :reference :diploma :talk]]
   [:document/date {:optional true} DateISO]
   [:document/citekey {:optional true} :string]
   [:document/doi {:optional true} :string]
   [:document/venue {:optional true} :string]
   [:document/authors {:optional true} [:vector :string]]
   [:document/bibtex {:optional true} :string]
   [:document/csl-json {:optional true} :map]])

(def Hobby
  "Malli schema for an interest or hobby."
  [:map
   [:item/uuid {:optional true} :uuid]
   [:item/tags {:optional true} TagColl]
   [:item/archived? {:optional true} :boolean]
   [:hobby/name NonEmptyStr]
   [:hobby/description {:optional true} :string]])

(def CVStyle
  "Malli schema for CV visual theme, layout, and font family strings."
  [:map
   [:theme :keyword]
   [:layout :keyword]
   [:font [:map
           [:heading NonEmptyStr]
           [:body NonEmptyStr]
           [:mono NonEmptyStr]]]])

(def CVRules
  "Malli schema for declarative CV fact filtering and budget rules."
  [:map
   [:tags-any {:optional true} TagColl]
   [:tags-all {:optional true} TagColl]
   [:include-types {:optional true} TagColl]
   [:exclude-types {:optional true} TagColl]
   [:after-date {:optional true} DateISO]
   [:before-date {:optional true} DateISO]
   [:max-items {:optional true} [:map-of :keyword pos-int?]]])

(def CVConfig
  "Malli schema for a multi-CV solver target configuration."
  [:map
   [:item/uuid {:optional true} :uuid]
   [:cv/id [:or :keyword :uuid]]
   [:cv/title NonEmptyStr]
   [:cv/person {:optional true} [:or :uuid Person]]
   [:cv/include {:optional true} [:set :uuid]]
   [:cv/exclude {:optional true} [:set :uuid]]
   [:cv/rules {:optional true} CVRules]
   [:cv/overrides {:optional true} [:map-of :uuid :map]]
   [:cv/order {:optional true} [:map-of :keyword [:vector :uuid]]]
   [:cv/section-order [:vector :keyword]]
   [:cv/style CVStyle]
   [:cv/summary-override {:optional true} :string]])

(def CVSection
  "Malli schema for a resolved section inside CanonicalCV."
  [:map
   [:section/id :keyword]
   [:section/items [:vector :map]]])

(def CanonicalCV
  "Open Malli IR schema for a resolved, renderer-ready CV."
  [:map
   [:cv/id [:or :keyword :uuid]]
   [:cv/title NonEmptyStr]
   [:cv/schema-version pos-int?]
   [:cv/style CVStyle]
   [:cv/person Person]
   [:cv/sections [:vector CVSection]]])

(defn valid?
  "Return true when value conforms to the given Malli schema."
  [schema value]
  (m/validate schema value))

(defn explain-human
  "Return humanized Malli validation errors, or nil when valid."
  [schema value]
  (some-> (m/explain schema value) me/humanize))

(defn validate
  "Validate value against schema, returning :schema/valid? result map."
  [schema value]
  (if-let [errors (explain-human schema value)]
    {:schema/valid? false :schema/errors errors}
    {:schema/valid? true :schema/value value}))
