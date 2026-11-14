(ns easy-cv.core.io
  "Unified pure .cljc boundary parsers and exporters for EDN, YAML, JSON-LD,
   BibTeX, and CSL-JSON."
  (:require [clojure.edn :as edn]
            [clojure.string :as str]
            [clojure.walk :as walk]
            [easy-cv.core.bibtex :as bibtex]
            [easy-cv.core.schema :as schema]
            [easy-cv.core.schema-org :as schema-org]
            [malli.core :as m]
            [malli.transform :as mt]
            #?@(:clj [[babashka.json :as json]
                      [clj-yaml.core :as yaml]]
                :cljs [["js-yaml" :as js-yaml]])))

(defn- parse-json-str
  "Parse JSON string into Clojure data with string keys."
  [text]
  #?(:clj (json/read-str text {:key-fn identity})
     :cljs (js->clj (js/JSON.parse text))))

(defn- write-json-str
  "Serialize Clojure data into a JSON string."
  [payload]
  #?(:clj (json/write-str payload)
     :cljs (js/JSON.stringify (clj->js payload) nil 2)))

(defn- encode-yaml-node
  "Convert Clojure-only types (UUID, keyword, set) for clean YAML output."
  [x]
  (cond
    (uuid? x) (str x)
    (keyword? x) (subs (str x) 1)
    (set? x) (vec x)
    (map? x) (into {} (map (fn [[k v]]
                             [(if (keyword? k) (subs (str k) 1) k) v]))
                   x)
    (seq? x) (vec x)
    :else x))

(defn- write-yaml-str
  "Serialize Clojure data to YAML string without JVM-specific tags."
  [payload]
  (let [encoded (walk/postwalk encode-yaml-node payload)]
    #?(:clj (yaml/generate-string encoded)
       :cljs (js-yaml/dump (clj->js encoded)))))

(defn- keywordize-keys-deep
  "Convert string map keys and ordered maps into plain keyword-keyed maps."
  [x]
  (walk/postwalk
   (fn [node]
     (cond
       (map? node) (into {} (map (fn [[k v]]
                                   [(if (string? k) (keyword k) k) v]))
                         node)
       (seq? node) (vec node)
       :else node))
   x))

(defn- parse-yaml-str
  "Parse YAML string into plain Clojure maps with keyword keys."
  [text]
  (let [raw #?(:clj (yaml/parse-string text :keywords false)
               :cljs (js->clj (js-yaml/load text)))]
    (keywordize-keys-deep raw)))

(def ^:private kw-fields
  #{:item/type :history/type :document/type :org/type :theme :layout})

(def ^:private uuid-fields
  #{:item/uuid :history/organization :cv/person})

(defn- restore-field
  "Coerce decoded YAML scalar/collection field back to expected Clojure type."
  [k v]
  (cond
    (and (= :item/tags k) (coll? v)) (set (map keyword v))
    (and (contains? kw-fields k) (string? v)) (keyword v)
    (and (contains? uuid-fields k) (string? v)) (or (parse-uuid v) v)
    (and (= :project/skills k) (coll? v))
    (mapv #(if (string? %) (or (parse-uuid %) %) %) v)
    :else v))

(defn- restore-yaml-types
  "Restore UUIDs, sets, and enum keywords in nested parsed YAML structures."
  [form]
  (walk/postwalk
   (fn [node]
     (if (map? node)
       (reduce-kv (fn [acc k v] (assoc acc k (restore-field k v))) {} node)
       node))
   form))

(defn- infer-schema
  "Select validation schema for parsed EDN/YAML value unless overridden."
  [v opts]
  (or (:schema opts)
      (cond
        (and (map? v) (contains? v :cv/sections)) schema/CanonicalCV
        (and (map? v) (contains? v :cv/id)) schema/CVConfig
        (and (map? v) (contains? v :person/name)) schema/Person
        (vector? v) [:vector :map]
        :else :any)))

(defn- validate-parsed
  "Validate parsed value against target Malli schema and build result map."
  [target-schema parsed]
  (if-let [errs (schema/explain-human target-schema parsed)]
    {:ok? false :errors errs}
    {:ok? true :io/data parsed}))

(defn- parse-edn-input
  "Parse EDN string and validate against inferred or explicit schema."
  [text opts]
  (when (str/blank? text)
    (throw (ex-info "EDN input is blank" {:errors ["EDN input is blank"]})))
  (let [parsed (edn/read-string text)]
    (validate-parsed (infer-schema parsed opts) parsed)))

(defn- parse-yaml-input
  "Parse YAML string, decode schema types, and validate."
  [text opts]
  (when (str/blank? text)
    (throw (ex-info "YAML input is blank" {:errors ["YAML input is blank"]})))
  (let [raw (restore-yaml-types (parse-yaml-str text))
        sch (infer-schema raw opts)
        decoded (m/decode sch raw (mt/json-transformer))]
    (validate-parsed sch decoded)))

(defn- parse-json-ld-input
  "Parse Schema.org JSON-LD string into CanonicalCV or fact vector."
  [text opts]
  (let [raw (parse-json-str text)]
    (if (= :facts (:target opts))
      (validate-parsed [:vector :map] (schema-org/json-ld->facts raw))
      (validate-parsed schema/CanonicalCV
                       (schema-org/json-ld->canonical raw opts)))))

(defn- parse-bibtex-input
  "Parse BibTeX string and validate every Document fact."
  [text]
  (let [docs (bibtex/parse-bibtex text)]
    (validate-parsed [:vector schema/Document] docs)))

(defn- parse-csl-input
  "Parse Zotero CSL-JSON string and validate every Document fact."
  [text]
  (let [raw (parse-json-str text)
        docs (bibtex/csl-json->facts raw)]
    (validate-parsed [:vector schema/Document] docs)))

(defn- format-ex-errors
  "Extract structured error list from a caught parse exception."
  [e]
  (or (:errors (ex-data e))
      [(or (ex-message e) "Parse failed")]))

(defn parse-input
  "Parse text in fmt (:edn :yaml :json-ld :bibtex :csl-json) and validate."
  ([fmt text] (parse-input fmt text {}))
  ([fmt text opts]
   (try
     (case fmt
       :edn (parse-edn-input text opts)
       :yaml (parse-yaml-input text opts)
       :json-ld (parse-json-ld-input text opts)
       :bibtex (parse-bibtex-input text)
       :csl-json (parse-csl-input text)
       {:ok? false :errors [(str "Unsupported input format: " fmt)]})
     (catch #?(:clj Exception :cljs :default) e
       {:ok? false :errors (format-ex-errors e)}))))

(defn export-output
  "Serialize payload to string in fmt (:edn :yaml :json-ld :bibtex :csl-json)."
  ([fmt payload] (export-output fmt payload {}))
  ([fmt payload _opts]
   (case fmt
     :edn (pr-str payload)
     :yaml (write-yaml-str payload)
     :json-ld (write-json-str (schema-org/cv->json-ld payload))
     :bibtex (bibtex/facts->bibtex payload)
     :csl-json (let [items (if (map? payload) [payload] payload)]
                 (write-json-str (mapv bibtex/fact->csl-json items)))
     (throw (ex-info (str "Unsupported export format: " fmt)
                     {:fmt fmt})))))
