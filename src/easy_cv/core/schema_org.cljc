(ns easy-cv.core.schema-org
  "Bidirectional Schema.org JSON-LD mapping for Easy CV."
  (:require [clojure.string :as str]
            [easy-cv.core.schema :as schema]))

(defn- compact-map
  "Drop nil and empty-collection entries from a JSON-LD map."
  [m]
  (into {}
        (remove (fn [[_ v]]
                  (or (nil? v) (and (coll? v) (empty? v)))))
        m))

(defn- items-by-section
  "Collect all items belonging to section-id from CanonicalCV."
  [cv section-id]
  (->> (:cv/sections cv)
       (filter #(= section-id (:section/id %)))
       (mapcat :section/items)
       vec))

(defn- org->json-ld
  "Convert an organization map or name into Schema.org Organization."
  [org]
  (when (map? org)
    (compact-map {"@type" (if (= :school (:org/type org))
                            "EducationalOrganization"
                            "Organization")
                  "name" (:org/name org)
                  "url" (:org/url org)
                  "location" (:org/location org)})))

(defn- work->json-ld
  "Convert a :work or :volunteer HistoryItem to OrganizationRole."
  [item]
  (let [org (org->json-ld (:history/organization item))]
    (compact-map {"@type" "OrganizationRole"
                  "roleName" (:history/title item)
                  "startDate" (:history/start-date item)
                  "endDate" (:history/end-date item)
                  "description" (:history/description item)
                  "worksFor" org})))

(defn- edu-org->json-ld
  "Convert an education HistoryItem to EducationalOrganization."
  [item]
  (let [org (:history/organization item)]
    (compact-map {"@type" "EducationalOrganization"
                  "name" (or (:org/name org) (:history/title item))
                  "url" (:org/url org)
                  "location" (or (:org/location org)
                                 (:history/location item))})))

(defn- edu-cred->json-ld
  "Convert an education HistoryItem to EducationalOccupationalCredential."
  [item]
  (compact-map {"@type" "EducationalOccupationalCredential"
                "credentialCategory" (:history/title item)
                "about" (:history/subtitle item)
                "startDate" (:history/start-date item)
                "endDate" (:history/end-date item)
                "recognizedBy" (edu-org->json-ld item)}))

(defn- project->json-ld
  "Convert a Project map to Schema.org CreativeWork."
  [proj]
  (let [kws (mapv #(if (map? %) (:skill/name %) (str %))
                  (:project/skills proj))]
    (compact-map {"@type" "CreativeWork"
                  "name" (:project/name proj)
                  "description" (:project/description proj)
                  "url" (:project/url proj)
                  "dateCreated" (:project/start-date proj)
                  "keywords" kws})))

(defn- document->json-ld
  "Convert a Document map to Schema.org ScholarlyArticle."
  [doc]
  (compact-map {"@type" "ScholarlyArticle"
                "headline" (:document/name doc)
                "url" (:document/url doc)
                "datePublished" (:document/date doc)
                "identifier" (:document/doi doc)
                "isPartOf" (:document/venue doc)
                "author" (:document/authors doc)}))

(defn- person-address
  "Build PostalAddress map from location string when present."
  [loc]
  (when (seq loc)
    {"@type" "PostalAddress"
     "addressLocality" loc}))

(defn- person-json-ld-base
  "Build core Schema.org Person fields from person map and skill names."
  [p skills]
  {"@context" "https://schema.org"
   "@type" "Person"
   "name" (:person/name p)
   "email" (:person/email p)
   "telephone" (:person/phone p)
   "url" (:person/website p)
   "description" (:person/summary p)
   "address" (person-address (:person/location p))
   "sameAs" (:person/links p)
   "knowsAbout" (mapv :skill/name skills)})

(defn cv->json-ld
  "Convert a CanonicalCV map into a Schema.org Person JSON-LD graph."
  [cv]
  (let [works (concat (items-by-section cv :work)
                      (items-by-section cv :volunteer))
        edus (items-by-section cv :education)
        projs (items-by-section cv :projects)
        docs (items-by-section cv :documents)
        base (person-json-ld-base (:cv/person cv)
                                  (items-by-section cv :skills))]
    (compact-map
     (assoc base
            "worksFor" (mapv work->json-ld works)
            "alumniOf" (mapv edu-org->json-ld edus)
            "hasCredential" (mapv edu-cred->json-ld edus)
            "workExample" (into (mapv project->json-ld projs)
                                (mapv document->json-ld docs))))))

(defn- str-key-map
  "Normalize keyword/string keys in a JSON-LD map to string keys."
  [m]
  (if (map? m)
    (reduce-kv (fn [acc k v]
                 (let [sk (if (keyword? k) (subs (str k) 1) (str k))]
                   (assoc acc sk (if (map? v)
                                   (str-key-map v)
                                   (if (sequential? v)
                                     (mapv str-key-map v)
                                     v)))))
               {}
               m)
    m))

(defn- as-vec
  "Coerce single JSON-LD object or nil into a vector."
  [x]
  (cond (nil? x) []
        (sequential? x) (vec x)
        :else [x]))

(defn- parse-person
  "Extract a Person fact map from normalized JSON-LD map."
  [jld]
  (let [addr (get jld "address")
        loc (if (map? addr) (get addr "addressLocality") addr)]
    (compact-map
     {:item/uuid (random-uuid)
      :item/type :person
      :person/name (get jld "name")
      :person/email (or (get jld "email") "unknown@example.org")
      :person/phone (get jld "telephone")
      :person/website (get jld "url")
      :person/summary (get jld "description")
      :person/location loc
      :person/links (as-vec (get jld "sameAs"))})))

(defn- parse-org-node
  "Build an Organization fact map from a JSON-LD org node if named."
  [org-src org-type]
  (when (seq (get org-src "name"))
    (compact-map {:item/uuid (random-uuid)
                  :item/type :org
                  :org/name (get org-src "name")
                  :org/type org-type
                  :org/url (get org-src "url")
                  :org/location (get org-src "location")})))

(defn- parse-work-role
  "Extract Organization and HistoryItem facts from OrganizationRole."
  [role]
  (let [org (parse-org-node (or (get role "worksFor") role) :company)
        hist (compact-map {:item/uuid (random-uuid)
                           :item/type :history
                           :history/type :work
                           :history/title (or (get role "roleName")
                                              (get role "name") "Role")
                           :history/start-date (or (get role "startDate")
                                                   "2020-01")
                           :history/end-date (get role "endDate")
                           :history/description (get role "description")
                           :history/organization (:item/uuid org)})]
    {:orgs (if org [org] []) :items [hist]}))

(defn- parse-credential
  "Extract Education Organization and HistoryItem from JSON-LD."
  [cred]
  (let [org (parse-org-node (get cred "recognizedBy") :school)
        hist (compact-map {:item/uuid (random-uuid)
                           :item/type :history
                           :history/type :education
                           :history/title (or (get cred "credentialCategory")
                                              (get cred "name") "Degree")
                           :history/subtitle (get cred "about")
                           :history/start-date (or (get cred "startDate")
                                                   "2018-09")
                           :history/end-date (get cred "endDate")
                           :history/organization (:item/uuid org)})]
    {:orgs (if org [org] []) :items [hist]}))

(defn- parse-scholarly-article
  "Extract Document fact from a Schema.org ScholarlyArticle."
  [ex]
  {:kind :documents
   :fact (compact-map {:item/uuid (random-uuid)
                       :item/type :document
                       :document/name (or (get ex "headline")
                                          (get ex "name"))
                       :document/type :publication
                       :document/url (get ex "url")
                       :document/date (get ex "datePublished")
                       :document/doi (get ex "identifier")
                       :document/venue (get ex "isPartOf")
                       :document/authors (as-vec (get ex "author"))})})

(defn- parse-work-example
  "Extract Project or Document fact from CreativeWork/ScholarlyArticle."
  [ex]
  (if (= "ScholarlyArticle" (get ex "@type"))
    (parse-scholarly-article ex)
    {:kind :projects
     :fact (compact-map {:item/uuid (random-uuid)
                         :item/type :project
                         :project/name (get ex "name")
                         :project/description (get ex "description")
                         :project/url (get ex "url")
                         :project/start-date (get ex "dateCreated")})}))

(defn- assert-valid-person-jld!
  "Ensure normalized JSON-LD has Person @type and non-empty name."
  [jld]
  (when-not (and (map? jld)
                 (= "Person" (get jld "@type"))
                 (not (str/blank? (get jld "name"))))
    (throw (ex-info "Invalid Schema.org Person JSON-LD"
                    {:errors ["Expected Person with non-empty name"]}))))

(defn- parse-alumni-fallback
  "Build education facts from alumniOf when hasCredential is absent."
  [jld]
  (mapv (fn [al]
          (let [org-id (random-uuid)
                oname (or (get al "name") "University")
                org {:item/uuid org-id :item/type :org
                     :org/name oname :org/type :school}]
            {:orgs [org]
             :items [{:item/uuid (random-uuid) :item/type :history
                      :history/type :education :history/title "Degree"
                      :history/start-date "2018-09"
                      :history/organization org-id}]}))
        (as-vec (get jld "alumniOf"))))

(defn- parse-json-ld-parts
  "Parse normalized Schema.org Person JSON-LD into grouped fact parts."
  [raw]
  (let [jld (str-key-map raw)
        _ (assert-valid-person-jld! jld)
        works (mapv parse-work-role (as-vec (get jld "worksFor")))
        raw-creds (as-vec (get jld "hasCredential"))
        creds (mapv (if (seq raw-creds) parse-credential #(identity %))
                    (if (seq raw-creds) raw-creds (parse-alumni-fallback jld)))
        exs (mapv parse-work-example (as-vec (get jld "workExample")))]
    {:person (parse-person jld)
     :orgs (vec (concat (mapcat :orgs works) (mapcat :orgs creds)))
     :work (vec (mapcat :items works))
     :education (vec (mapcat :items creds))
     :skills (mapv #(-> {:item/uuid (random-uuid) :item/type :skill
                         :skill/name (str %)})
                   (as-vec (get jld "knowsAbout")))
     :projects (vec (keep #(when (= :projects (:kind %)) (:fact %)) exs))
     :documents (vec (keep #(when (= :documents (:kind %)) (:fact %)) exs))}))

(defn json-ld->facts
  "Convert Schema.org Person JSON-LD into a vector of DataScript fact maps."
  [raw]
  (let [{:keys [person orgs work education skills projects documents]}
        (parse-json-ld-parts raw)]
    (vec (concat [person] orgs work education skills projects documents))))

(def ^:private default-jld-style
  {:theme :modern-minimal
   :layout :single-column
   :font {:heading "Inter" :body "Inter" :mono "IBM Plex Mono"}})

(defn json-ld->canonical
  "Convert Schema.org Person JSON-LD directly into a CanonicalCV map."
  ([raw] (json-ld->canonical raw {}))
  ([raw {:keys [cv/id cv/title cv/style]}]
   (let [parts (parse-json-ld-parts raw)
         pname (get-in parts [:person :person/name])]
     {:cv/id (or id :imported-json-ld)
      :cv/title (or title (str pname " CV"))
      :cv/schema-version schema/schema-version
      :cv/style (or style default-jld-style)
      :cv/person (:person parts)
      :cv/sections [{:section/id :work :section/items (:work parts)}
                    {:section/id :education :section/items (:education parts)}
                    {:section/id :projects :section/items (:projects parts)}
                    {:section/id :skills :section/items (:skills parts)}
                    {:section/id :documents :section/items (:documents parts)}
                    {:section/id :hobbies :section/items []}]})))
