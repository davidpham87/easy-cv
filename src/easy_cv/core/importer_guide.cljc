(ns easy-cv.core.importer-guide
  "Pure .cljc format guides, exposed schemas, and LLM PDF-to-CV prompt."
  (:require [clojure.string :as str]
            [clojure.walk :as walk]
            [easy-cv.core.importer-guide-data :as data]
            [easy-cv.core.schema :as schema]
            [malli.core :as m]
            #?@(:clj [[babashka.json :as json]]
                :cljs [])))

(def supported-formats
  "Ordered list of editor and guide formats."
  [:edn :yaml :json-ld :bibtex :csl-json :latex])

(def prompt-target-formats
  "Supported output formats for LLM PDF-to-CV extraction."
  [:json-ld :edn :yaml])

(def SupportedFormat
  "Malli schema for supported editor guide formats."
  [:enum :edn :yaml :json-ld :bibtex :csl-json :latex])

(def PromptTargetFormat
  "Malli schema for LLM prompt output formats."
  [:enum :json-ld :edn :yaml])

(def EntityDoc
  "Malli schema describing one entity type in a format guide."
  [:map
   [:entity/type :keyword]
   [:entity/label :string]
   [:entity/required [:vector :string]]
   [:entity/optional [:vector :string]]
   [:entity/notes :string]])

(def FormatGuide
  "Malli schema for per-format editor guidance and starter example."
  [:map
   [:guide/format SupportedFormat]
   [:guide/label :string]
   [:guide/importable? :boolean]
   [:guide/summary :string]
   [:guide/required-keys [:vector :string]]
   [:guide/optional-keys [:vector :string]]
   [:guide/entities [:vector EntityDoc]]
   [:guide/tips [:vector :string]]
   [:guide/example-payload :string]])

(def PromptOptions
  "Malli schema for build-llm-cv-prompt input options."
  [:map
   [:prompt/target-format {:optional true} PromptTargetFormat]
   [:prompt/role-focus {:optional true} [:maybe :string]]
   [:prompt/extra-notes {:optional true} [:maybe :string]]])

(def ExposedSchemaBundle
  "Malli schema for exported Easy CV Malli EDN and JSON Schema bundle."
  [:map
   [:schema/version pos-int?]
   [:schema/edn-spec :string]
   [:schema/json-schema :map]
   [:schema/schema-org-mapping :map]])

(defn format-guide
  "Return the structured guide map for fmt, or nil if unsupported."
  [fmt]
  (get data/format-guides-map fmt))

(defn example-payload
  "Return the valid starter example string for fmt, or empty string."
  [fmt]
  (or (:guide/example-payload (format-guide fmt)) ""))

(defn- regex?
  [x]
  #?(:clj (instance? java.util.regex.Pattern x)
     :cljs (regexp? x)))

(defn- sanitize-malli-form
  "Strip generator metadata and regex objects from Malli forms for EDN."
  [sch]
  (walk/postwalk
   (fn [node]
     (cond
       (map? node) (dissoc node :gen/gen)
       (regex? node) (str node)
       :else node))
   (m/form sch)))

(def ^:private named-schemas
  [[:DateISO schema/DateISO]
   [:Person schema/Person]
   [:Organization schema/Organization]
   [:HistoryItem schema/HistoryItem]
   [:Skill schema/Skill]
   [:Project schema/Project]
   [:Document schema/Document]
   [:Hobby schema/Hobby]
   [:CVStyle schema/CVStyle]
   [:CVRules schema/CVRules]
   [:CVConfig schema/CVConfig]
   [:CanonicalCV schema/CanonicalCV]])

(defn- format-edn-spec
  "Build readable EDN Malli spec string for all Easy CV domain schemas."
  []
  (let [entries (for [[k sch] named-schemas]
                  (str " " (pr-str k) " " (pr-str (sanitize-malli-form sch))))]
    (str "{:schema/version " schema/schema-version "\n"
         " :schema/malli\n {" (str/join "\n  " entries) "}}")))

(def ^:private schema-org-mapping
  {"Person" {:easy-cv/entity :person
             :fields {"name" ":person/name"
                      "email" ":person/email"
                      "telephone" ":person/phone"
                      "url" ":person/website"
                      "description" ":person/summary"
                      "address.addressLocality" ":person/location"
                      "sameAs" ":person/links"
                      "knowsAbout" ":skill/name (vector of Skill facts)"}}
   "OrganizationRole" {:easy-cv/entity :history
                       :history/type :work
                       :fields {"roleName" ":history/title"
                                "startDate" ":history/start-date (YYYY-MM)"
                                "endDate" ":history/end-date"
                                "description" ":history/description"
                                "worksFor" ":history/organization (:org/*)"}}
   "EducationalOccupationalCredential"
   {:easy-cv/entity :history
    :history/type :education
    :fields {"credentialCategory" ":history/title"
             "about" ":history/subtitle"
             "startDate" ":history/start-date"
             "endDate" ":history/end-date"
             "recognizedBy" ":history/organization (:org/* :school)"}}
   "CreativeWork" {:easy-cv/entity :project
                   :fields {"name" ":project/name"
                            "description" ":project/description"
                            "url" ":project/url"
                            "dateCreated" ":project/start-date"}}
   "ScholarlyArticle" {:easy-cv/entity :document
                       :document/type :publication
                       :fields {"headline" ":document/name"
                                "url" ":document/url"
                                "datePublished" ":document/date"
                                "identifier" ":document/doi"
                                "isPartOf" ":document/venue"
                                "author" ":document/authors"}}})

(defn- json-prop-str
  [desc]
  {"type" "string" "description" desc})

(defn- json-prop-date
  [desc]
  {"type" "string"
   "pattern" "^\\d{4}(-\\d{2}(-\\d{2})?)?$"
   "description" desc})

(def ^:private json-defs-entities
  "JSON Schema $defs for Person, Organization, HistoryItem, and Skill."
  {"Person"
   {"type" "object"
    "required" ["person/name" "person/email"]
    "properties"
    {"item/uuid" (json-prop-str "UUID v4 string")
     "item/type" {"const" "person"}
     "person/name" (json-prop-str "Full display name")
     "person/email" (json-prop-str "Primary email address")
     "person/phone" (json-prop-str "Phone number")
     "person/website" (json-prop-str "Personal site URL")
     "person/summary" (json-prop-str "Professional bio summary")
     "person/location" (json-prop-str "City, Region")
     "person/links" {"type" "array" "items" {"type" "string"}}}}
   "Organization"
   {"type" "object"
    "required" ["org/name" "org/type"]
    "properties"
    {"item/uuid" (json-prop-str "UUID v4 string")
     "item/type" {"const" "org"}
     "org/name" (json-prop-str "Organization name")
     "org/type" {"enum" ["company" "school" "lab" "community"]}
     "org/location" (json-prop-str "Organization city/country")
     "org/url" (json-prop-str "Organization homepage URL")}}
   "HistoryItem"
   {"type" "object"
    "required" ["history/type" "history/title" "history/start-date"]
    "properties"
    {"item/uuid" (json-prop-str "UUID v4 string")
     "item/type" {"const" "history"}
     "item/tags" {"type" "array" "items" {"type" "string"}}
     "history/type" {"enum" ["work" "education" "volunteer"]}
     "history/title" (json-prop-str "Role title or degree")
     "history/subtitle" (json-prop-str "Department or field of study")
     "history/organization" (json-prop-str "Organization UUID or object")
     "history/start-date" (json-prop-date "Start date YYYY-MM")
     "history/end-date" (json-prop-date "End date YYYY-MM (omit if ongoing)")
     "history/description" (json-prop-str "Summary paragraph")
     "history/highlights" {"type" "array" "items" {"type" "string"}}
     "history/location" (json-prop-str "Role location")}}
   "Skill"
   {"type" "object"
    "required" ["skill/name"]
    "properties"
    {"item/uuid" (json-prop-str "UUID v4 string")
     "item/type" {"const" "skill"}
     "skill/name" (json-prop-str "Skill label")
     "skill/category" (json-prop-str "Grouping category")
     "skill/level" (json-prop-str "Proficiency level")
     "skill/description" (json-prop-str "Skill context")}}})

(def ^:private json-defs-extras
  "JSON Schema $defs for Project, Document, Hobby, and CVConfig."
  {"Project"
   {"type" "object"
    "required" ["project/name"]
    "properties"
    {"item/uuid" (json-prop-str "UUID v4 string")
     "item/type" {"const" "project"}
     "project/name" (json-prop-str "Project title")
     "project/description" (json-prop-str "Project summary")
     "project/url" (json-prop-str "Repository or live URL")
     "project/start-date" (json-prop-date "Start date YYYY-MM")
     "project/end-date" (json-prop-date "End date YYYY-MM (omit if ongoing)")
     "project/skills" {"type" "array" "items" {"type" "string"}}}}
   "Document"
   {"type" "object"
    "required" ["document/name" "document/type"]
    "properties"
    {"item/uuid" (json-prop-str "UUID v4 string")
     "item/type" {"const" "document"}
     "document/name" (json-prop-str "Publication or talk title")
     "document/type" {"enum" ["publication" "reference" "diploma" "talk"]}
     "document/date" (json-prop-date "Publication date YYYY-MM")
     "document/citekey" (json-prop-str "Unique BibTeX citekey")
     "document/doi" (json-prop-str "Digital Object Identifier")
     "document/venue" (json-prop-str "Journal or conference")
     "document/authors" {"type" "array" "items" {"type" "string"}}
     "document/url" (json-prop-str "Document URL")}}
   "Hobby"
   {"type" "object"
    "required" ["hobby/name"]
    "properties"
    {"item/uuid" (json-prop-str "UUID v4 string")
     "item/type" {"const" "hobby"}
     "hobby/name" (json-prop-str "Interest or hobby name")
     "hobby/description" (json-prop-str "Optional detail")}}
   "CVConfig"
   {"type" "object"
    "required" ["cv/id" "cv/title" "cv/section-order" "cv/style"]
    "properties"
    {"cv/id" (json-prop-str "Target CV identifier")
     "cv/title" (json-prop-str "Target CV title")
     "cv/section-order" {"type" "array" "items" {"type" "string"}}
     "cv/summary-override" (json-prop-str "Tailored summary override")}}})

(defn- build-json-schema
  "Build Draft 2020-12 JSON Schema map for Easy CV archive facts."
  []
  {"$schema" "https://json-schema.org/draft/2020-12/schema"
   "$id" "https://easy-cv.local/schema/v1/facts.schema.json"
   "title" "Easy CV Facts & Canonical Schema"
   "type" "array"
   "items" {"oneOf" [{"$ref" "#/$defs/Person"}
                     {"$ref" "#/$defs/Organization"}
                     {"$ref" "#/$defs/HistoryItem"}
                     {"$ref" "#/$defs/Skill"}
                     {"$ref" "#/$defs/Project"}
                     {"$ref" "#/$defs/Document"}
                     {"$ref" "#/$defs/Hobby"}
                     {"$ref" "#/$defs/CVConfig"}]}
   "$defs" (merge json-defs-entities json-defs-extras)
   "x-schema-org-mapping" schema-org-mapping})

(defn exposed-schema-spec
  "Return Easy CV's exposed Malli EDN spec, JSON Schema, and Schema.org map."
  []
  {:schema/version schema/schema-version
   :schema/edn-spec (format-edn-spec)
   :schema/json-schema (build-json-schema)
   :schema/schema-org-mapping schema-org-mapping})

(defn- write-pretty-json
  [payload]
  #?(:clj (json/write-str payload)
     :cljs (js/JSON.stringify (clj->js payload) nil 2)))

(defn export-json-schema-str
  "Serialize Easy CV's JSON Schema and Schema.org mapping to a JSON string."
  ([] (export-json-schema-str (exposed-schema-spec)))
  ([bundle] (write-pretty-json (:schema/json-schema bundle))))

(defn- prompt-rules-block
  [target-fmt]
  (str "Strict Extraction Rules:\n"
       "1. Extract ONLY facts explicitly stated in the PDF resume.\n"
       "2. NEVER fabricate dates, employers, metrics, URLs, or emails.\n"
       "3. Omit optional fields if missing rather than guessing.\n"
       "4. Format ALL dates as ISO strings: YYYY-MM (or YYYY if month"
       " unknown).\n"
       "5. Leave ongoing end dates null/nil (or omit endDate).\n"
       "6. Output target format: " (str/upper-case (name target-fmt))
       " only, inside a single fenced code block."))

(def ^:private json-ld-schema-guide
  (str "Schema.org + Easy CV JSON-LD Mapping:\n"
       "- Root object: @context \"https://schema.org\", @type \"Person\"\n"
       "- Required: name; optional: email, telephone, url, description,\n"
       "  address ({@type PostalAddress, addressLocality}), sameAs []\n"
       "- Skills: knowsAbout [\"Clojure\", ...]\n"
       "- Work: worksFor [{@type OrganizationRole, roleName, startDate,\n"
       "  endDate, description, worksFor: {@type Organization, name}}]\n"
       "- Education: hasCredential [{@type EducationalOccupationalCredential,\n"
       "  credentialCategory, about, startDate, endDate,\n"
       "  recognizedBy: {@type EducationalOrganization, name}}]\n"
       "- Projects/Papers: workExample [{@type CreativeWork, name,\n"
       "  description, url, dateCreated} or {@type ScholarlyArticle,\n"
       "  headline, datePublished, identifier, isPartOf, author}]"))

(defn- facts-schema-guide
  [target-fmt]
  (str "Easy CV Exact Fact Keys (" (str/upper-case (name target-fmt))
       " vector of fact maps):\n"
       "- Person (item/type person): :person/name, :person/email,\n"
       "  :person/phone, :person/website, :person/summary, :person/location\n"
       "- Org (item/type org): :org/name, :org/type (:company/:school)\n"
       "- History (item/type history): :history/type (:work/:education),\n"
       "  :history/title, :history/organization, :history/start-date,\n"
       "  :history/end-date, :history/description, :history/highlights []\n"
       "- Skill (item/type skill): :skill/name, :skill/category, :skill/level\n"
       "- Project (item/type project): :project/name, :project/description,\n"
       "  :project/url, :project/start-date, :project/end-date\n"
       "- Document (item/type document): :document/name, :document/type,\n"
       "  :document/date, :document/venue, :document/authors, :document/doi\n"
       "- Hobby (item/type hobby): :hobby/name, :hobby/description"))

(defn- prompt-schema-block
  [target-fmt]
  (if (= target-fmt :json-ld)
    json-ld-schema-guide
    (facts-schema-guide target-fmt)))

(defn- prompt-skeleton-block
  [target-fmt]
  (str "Example Output Skeleton:\n```"
       (case target-fmt :json-ld "json" :edn "clojure" :yaml "yaml")
       "\n"
       (example-payload target-fmt)
       "\n```"))

(defn- prompt-header-block
  [fmt role-focus extra-notes]
  (str "You are converting a user's PDF resume/CV into valid Easy CV "
       (str/upper-case (name fmt)) " data."
       (when-not (str/blank? role-focus)
         (str "\nTarget Role Focus: Emphasize experience, skills,"
              " and tags relevant to \"" (str/trim role-focus) "\"."))
       (when-not (str/blank? extra-notes)
         (str "\nAdditional Instructions: " (str/trim extra-notes)))))

(defn build-llm-cv-prompt
  "Generate a structured prompt to convert a PDF CV into Easy CV format."
  ([] (build-llm-cv-prompt {}))
  ([{:prompt/keys [target-format role-focus extra-notes]}]
   (let [fmt (if (contains? #{:json-ld :edn :yaml} target-format)
               target-format
               :json-ld)]
     (str/join "\n\n"
               [(prompt-header-block fmt role-focus extra-notes)
                (prompt-rules-block fmt)
                (prompt-schema-block fmt)
                (prompt-skeleton-block fmt)]))))
