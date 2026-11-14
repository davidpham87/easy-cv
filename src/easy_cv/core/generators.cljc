(ns easy-cv.core.generators
  "Seeded Malli and test.check generators for fake CV tiers and archives."
  (:require [clojure.string :as str]
            [clojure.test.check.generators :as gen]
            [easy-cv.core.schema :as schema]
            [malli.generator :as mg]))

(def tiers
  "Supported career stage tiers ordered from entry to executive."
  [:student :junior :mid :senior :vp :exec])

(def tier-profiles
  "Counts, titles, and defaults characterizing each career tier."
  {:student {:tier/work [0 1] :tier/edu [1 2] :tier/proj [2 3]
             :tier/skill [4 8] :tier/doc [0 1] :tier/hobby [1 2]
             :tier/titles ["Software Engineering Intern" "Teaching Assistant"]
             :tier/cv-title "CS Student & Intern CV"
             :tier/theme :modern-minimal :tier/layout :single-column}
   :junior  {:tier/work [1 2] :tier/edu [1 1] :tier/proj [3 5]
             :tier/skill [6 12] :tier/doc [0 2] :tier/hobby [1 3]
             :tier/titles ["Junior Software Engineer" "Full-Stack Developer"
                           "Backend Developer"]
             :tier/cv-title "Junior Software Engineer CV"
             :tier/theme :modern-minimal :tier/layout :single-column}
   :mid     {:tier/work [2 4] :tier/edu [1 1] :tier/proj [3 5]
             :tier/skill [8 14] :tier/doc [1 3] :tier/hobby [1 2]
             :tier/titles ["Software Engineer" "Senior Backend Engineer"
                           "Product Engineer"]
             :tier/cv-title "Software Engineer CV"
             :tier/theme :swiss-grid :tier/layout :single-column}
   :senior  {:tier/work [3 5] :tier/edu [1 2] :tier/proj [4 6]
             :tier/skill [10 16] :tier/doc [1 4] :tier/hobby [1 2]
             :tier/titles ["Staff Software Engineer" "Principal Engineer"
                           "Tech Lead"]
             :tier/cv-title "Staff & Principal Engineer CV"
             :tier/theme :compact-ats :tier/layout :two-column-sidebar}
   :vp      {:tier/work [4 6] :tier/edu [1 2] :tier/proj [2 4]
             :tier/skill [8 14] :tier/doc [2 5] :tier/hobby [1 2]
             :tier/titles ["VP of Engineering" "Director of Platform"
                           "Head of Infrastructure"]
             :tier/cv-title "VP Engineering CV"
             :tier/theme :executive-serif :tier/layout :two-column-sidebar}
   :exec    {:tier/work [5 8] :tier/edu [1 2] :tier/proj [1 3]
             :tier/skill [6 12] :tier/doc [2 6] :tier/hobby [1 2]
             :tier/titles ["Chief Technology Officer" "SVP Engineering"
                           "Board Technical Advisor"]
             :tier/cv-title "Executive Technology Leader CV"
             :tier/theme :executive-serif :tier/layout :single-column}})

(def ^:private skill-catalog
  [["Clojure" "Language"] ["ClojureScript" "Language"] ["SQL" "Data"]
   ["PostgreSQL" "Data"] ["SQLite" "Data"] ["Reagent" "Framework"]
   ["Re-frame" "Framework"] ["DataScript" "Database"] ["Malli" "Library"]
   ["Docker" "Infra"] ["Linux" "Infra"] ["Git" "Tooling"]
   ["TypeScript" "Language"] ["Python" "Language"] ["LaTeX" "Publishing"]
   ["Distributed Systems" "Architecture"]])

(def ^:private tag-pool
  [:clojure :backend :frontend :sql :systems :academic :leadership :cloud])

(def ^:private org-names
  ["Acme Labs" "Northstar Tech" "Beacon Systems" "Vanguard Cloud"
   "Polytechnic Institute" "Open Data Collective" "Summit Analytics"
   "Helios Compute" "Civic Tech Studio" "Apex Robotics"])

(def ^:private person-names
  ["Ada Lovelace" "Alan Turing" "Grace Hopper" "Barbara Liskov"
   "Donald Knuth" "Margaret Hamilton" "John McCarthy" "Radia Perlman"])

(def ^:private font-presets
  [{:heading "Inter" :body "Inter" :mono "IBM Plex Mono"}
   {:heading "Merriweather" :body "Source Sans 3" :mono "JetBrains Mono"}
   {:heading "Computer Modern" :body "Computer Modern" :mono "LM Mono"}])

(defn- gen-range-count
  "Choose an integer count within [lo hi] inclusive."
  [[lo hi]]
  (gen/choose lo hi))

(defn- gen-tags
  "Generate a non-empty subset of domain tags."
  []
  (gen/fmap set (gen/vector (gen/elements tag-pool) 1 3)))

(defn gen-person
  "Generator for a valid Person entity with :item/uuid."
  []
  (gen/let [uid (mg/generator :uuid)
            full-name (gen/elements person-names)
            tags (gen-tags)
            loc (gen/elements ["Zurich, CH" "Berlin, DE" "London, UK"
                               "Remote" "Boston, MA"])]
    (let [slug (-> full-name str/lower-case (str/replace #"\s+" "."))]
      {:item/uuid uid
       :item/type :person
       :item/tags tags
       :person/name full-name
       :person/email (str slug "@example.org")
       :person/website (str "https://example.org/" slug)
       :person/location loc
       :person/summary "Builder focused on reliable data systems."
       :person/links [(str "https://github.com/" slug)]})))

(defn gen-org
  "Generator for an Organization entity."
  []
  (gen/let [uid (mg/generator :uuid)
            org-name (gen/elements org-names)
            org-type (gen/elements [:company :school :lab :community])
            loc (gen/elements ["Zurich" "London" "NYC" "Remote"])]
    {:item/uuid uid
     :item/type :org
     :org/name org-name
     :org/type org-type
     :org/location loc}))

(defn- gen-work-item
  "Generator for one work HistoryItem linked to an org."
  [tier org-ids]
  (let [titles (get-in tier-profiles [tier :tier/titles])
        date-gen (mg/generator schema/DateISO)]
    (gen/let [uid (mg/generator :uuid)
              org-id (gen/elements org-ids)
              title (gen/elements titles)
              start-d date-gen
              end-d (gen/one-of [(gen/return nil) date-gen])
              tags (gen-tags)]
      (cond-> {:item/uuid uid :item/type :history :item/tags tags
               :history/type :work :history/organization org-id
               :history/title title :history/start-date start-d
               :history/description "Designed and delivered core modules."
               :history/highlights ["Reduced latency by 35% via indexing."]}
        end-d (assoc :history/end-date end-d)))))

(defn- gen-edu-item
  "Generator for an education HistoryItem linked to an org."
  [tier org-ids]
  (gen/let [uid (mg/generator :uuid)
            org-id (gen/elements org-ids)
            deg (gen/elements ["B.Sc. Computer Science"
                               "M.Sc. Distributed Systems"])
            start-d (mg/generator schema/DateISO)
            end-d (mg/generator schema/DateISO)
            tags (gen-tags)]
    (cond-> {:item/uuid uid
             :item/type :history
             :item/tags tags
             :history/type :education
             :history/organization org-id
             :history/title deg
             :history/subtitle "Computer Science"
             :history/start-date start-d}
      (not= tier :student) (assoc :history/end-date end-d))))

(defn- gen-skills-list
  "Generator for n Skills with distinct :skill/name values."
  [n]
  (gen/let [uids (gen/vector (mg/generator :uuid) n)
            picks (gen/shuffle skill-catalog)
            tags-list (gen/vector (gen-tags) n)
            lvl (gen/elements ["Beginner" "Intermediate" "Expert"])]
    (mapv (fn [uid [sname cat] tags]
            {:item/uuid uid
             :item/type :skill
             :item/tags tags
             :skill/name sname
             :skill/category cat
             :skill/level lvl})
          uids (take n picks) tags-list)))

(defn- gen-project-item
  "Generator for one Project referencing generated Skill UUIDs."
  [skill-ids]
  (let [max-refs (min 3 (count skill-ids))]
    (gen/let [uid (mg/generator :uuid)
              pname (gen/elements ["Query Engine" "CV Compiler" "Sync Bus"
                                   "Ledger CLI" "Schema Checker" "Task Graph"])
              start-d (mg/generator schema/DateISO)
              tags (gen-tags)
              refs (gen/vector (gen/elements skill-ids) 1 max-refs)]
      {:item/uuid uid
       :item/type :project
       :item/tags tags
       :project/name pname
       :project/description "Open-source tool built with immutable data."
       :project/start-date start-d
       :project/skills (vec (distinct refs))})))

(defn- gen-doc-item
  "Generator for one Document with a unique citekey."
  [idx]
  (gen/let [uid (mg/generator :uuid)
            dtype (gen/elements [:publication :talk :diploma :reference])
            pub-d (mg/generator schema/DateISO)
            tags (gen-tags)]
    {:item/uuid uid
     :item/type :document
     :item/tags tags
     :document/name (str "Technical Note #" (inc idx))
     :document/type dtype
     :document/date pub-d
     :document/citekey (str "easycv-" idx "-" (subs (str uid) 0 6))
     :document/venue "Symposium on Data Systems"
     :document/authors ["A. Lovelace" "B. Liskov"]}))

(defn- gen-hobby-item
  "Generator for one Hobby entity."
  []
  (gen/let [uid (mg/generator :uuid)
            hname (gen/elements ["Bouldering" "Chess" "Cycling" "Piano"])]
    {:item/uuid uid
     :item/type :hobby
     :hobby/name hname
     :hobby/description "Weekend practice and community meetups."}))

(defn- gen-style
  "Generator for CVStyle aligned with tier defaults."
  [tier]
  (let [{:tier/keys [theme layout]} (get tier-profiles tier)]
    (gen/let [font (gen/elements font-presets)]
      {:theme theme
       :layout layout
       :font font})))

(defn- build-cv-config
  "Build CVConfig map from generated fields."
  [cfg-uuid cv-id title person-id style inc-ids exc-ids tags]
  {:item/uuid cfg-uuid
   :item/type :cv/config
   :cv/id cv-id
   :cv/title title
   :cv/person person-id
   :cv/include (set inc-ids)
   :cv/exclude (set exc-ids)
   :cv/rules (cond-> {} (seq tags) (assoc :tags-any (set tags)))
   :cv/overrides {}
   :cv/order {}
   :cv/section-order [:work :education :projects :skills :documents :hobbies]
   :cv/style style})

(defn- gen-cv-config-for
  "Generator for a CVConfig referencing archive entities."
  [tier person-id items]
  (let [title (get-in tier-profiles [tier :tier/cv-title])
        all-ids (mapv :item/uuid items)]
    (gen/let [cfg-uuid (mg/generator :uuid)
              cv-id (gen/elements [:primary :targeted :academic])
              style (gen-style tier)
              inc-n (gen/choose 0 (min 2 (count all-ids)))
              exc-n (gen/choose 0 (min 1 (count all-ids)))
              tags (gen/vector (gen/elements tag-pool) 0 2)]
      (build-cv-config cfg-uuid cv-id title person-id style
                       (take inc-n all-ids)
                       (take exc-n (reverse all-ids))
                       tags))))

(defn- gen-counts
  "Generator returning item counts map for a career tier."
  [tier]
  (let [p (get tier-profiles tier (:junior tier-profiles))]
    (gen/let [w (gen-range-count (:tier/work p))
              e (gen-range-count (:tier/edu p))
              pr (gen-range-count (:tier/proj p))
              sk (gen-range-count (:tier/skill p))
              dc (gen-range-count (:tier/doc p))
              hb (gen-range-count (:tier/hobby p))]
      {:cnt/work w :cnt/edu e :cnt/proj pr
       :cnt/skill sk :cnt/doc dc :cnt/hobby hb})))

(defn- gen-docs-vec
  "Generator for n Document items with distinct citekeys."
  [n]
  (if (pos? n)
    (gen/fmap vec (apply gen/tuple (map gen-doc-item (range n))))
    (gen/return [])))

(defn- assemble-archive
  "Build normalized archive map and tx-data vector from generated parts."
  [tier person orgs skills hobbies docs works edus projs cfg]
  (let [history (vec (concat works edus))
        tx-data (vec (concat [person] orgs history skills
                             projs docs hobbies [cfg]))]
    {:archive/tier tier
     :archive/person person
     :archive/orgs orgs
     :archive/history history
     :archive/skills skills
     :archive/projects projs
     :archive/documents docs
     :archive/hobbies hobbies
     :archive/configs [cfg]
     :archive/tx-data tx-data}))

(defn gen-archive
  "Generator for a complete, linked career fact archive for a tier."
  ([] (gen-archive :junior))
  ([tier]
   (gen/let [person (gen-person)
             orgs (gen/vector (gen-org) 2 4)
             cnts (gen-counts tier)
             skills (gen-skills-list (:cnt/skill cnts))
             hobbies (gen/vector (gen-hobby-item) (:cnt/hobby cnts))
             docs (gen-docs-vec (:cnt/doc cnts))
             works (gen/vector (gen-work-item tier (mapv :item/uuid orgs))
                               (:cnt/work cnts))
             edus (gen/vector (gen-edu-item tier (mapv :item/uuid orgs))
                              (:cnt/edu cnts))
             projs (gen/vector (gen-project-item (mapv :item/uuid skills))
                               (:cnt/proj cnts))
             cfg (gen-cv-config-for tier (:item/uuid person)
                                    (concat works projs skills))]
     (assemble-archive tier person orgs skills hobbies docs
                       works edus projs cfg))))

(defn- archive->sections
  "Assemble section maps from a generated archive in config order."
  [archive cfg]
  (let [by-sec {:work (filterv #(= :work (:history/type %))
                               (:archive/history archive))
                :education (filterv #(= :education (:history/type %))
                                    (:archive/history archive))
                :projects (:archive/projects archive)
                :skills (:archive/skills archive)
                :documents (:archive/documents archive)
                :hobbies (:archive/hobbies archive)}]
    (mapv (fn [sec-id]
            {:section/id sec-id
             :section/items (get by-sec sec-id [])})
          (:cv/section-order cfg))))

(defn gen-cv-bundle
  "Generator returning {:cv/config CVConfig :cv/canonical CanonicalCV}."
  ([] (gen-cv-bundle :junior))
  ([tier]
   (gen/fmap
    (fn [archive]
      (let [cfg (first (:archive/configs archive))]
        {:cv/config cfg
         :cv/canonical {:cv/id (:cv/id cfg)
                        :cv/title (:cv/title cfg)
                        :cv/schema-version schema/schema-version
                        :cv/style (:cv/style cfg)
                        :cv/person (:archive/person archive)
                        :cv/sections (archive->sections archive cfg)}}))
    (gen-archive tier))))

(defn generate-archive
  "Deterministically generate an archive for tier given {:seed n :size s}."
  ([tier] (generate-archive tier {}))
  ([tier {:keys [seed size] :or {seed 42 size 20}}]
   (gen/generate (gen-archive tier) size seed)))

(defn generate-cv
  "Deterministically generate a CanonicalCV for tier."
  ([tier] (generate-cv tier {}))
  ([tier {:keys [seed size] :or {seed 42 size 20}}]
   (:cv/canonical (gen/generate (gen-cv-bundle tier) size seed))))

(defn generate-config
  "Deterministically generate a CVConfig for tier."
  ([tier] (generate-config tier {}))
  ([tier {:keys [seed size] :or {seed 42 size 20}}]
   (:cv/config (gen/generate (gen-cv-bundle tier) size seed))))
