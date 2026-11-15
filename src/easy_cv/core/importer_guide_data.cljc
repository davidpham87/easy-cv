(ns easy-cv.core.importer-guide-data
  "Static per-format guide metadata and curated valid starter examples.")

(def core-entities
  "Canonical Easy CV entity documentation referencing schema.cljc keys."
  [{:entity/type :person
    :entity/label "Person Profile"
    :entity/required [":person/name" ":person/email"]
    :entity/optional [":person/phone" ":person/website" ":person/summary"
                      ":person/location" ":person/links" ":item/uuid"
                      ":item/tags"]
    :entity/notes "Owner identity; :person/email is unique in DataScript."}
   {:entity/type :org
    :entity/label "Organization"
    :entity/required [":org/name" ":org/type"]
    :entity/optional [":org/location" ":org/url" ":item/uuid"]
    :entity/notes ":org/type is :company, :school, :lab, or :community."}
   {:entity/type :history
    :entity/label "Timeline Entry (Work / Edu / Volunteer)"
    :entity/required [":history/type" ":history/title" ":history/start-date"]
    :entity/optional [":history/subtitle" ":history/organization"
                      ":history/end-date" ":history/description"
                      ":history/highlights" ":history/location" ":item/tags"]
    :entity/notes "Dates use ISO YYYY-MM. :history/type is :work/:education."}
   {:entity/type :skill
    :entity/label "Skill"
    :entity/required [":skill/name"]
    :entity/optional [":skill/category" ":skill/level" ":skill/description"
                      ":item/tags"]
    :entity/notes ":skill/name is unique; group with :skill/category."}
   {:entity/type :project
    :entity/label "Project"
    :entity/required [":project/name"]
    :entity/optional [":project/description" ":project/url"
                      ":project/start-date" ":project/end-date"
                      ":project/skills" ":item/tags"]
    :entity/notes ":project/skills accepts skill UUIDs or names."}
   {:entity/type :document
    :entity/label "Document / Publication"
    :entity/required [":document/name" ":document/type"]
    :entity/optional [":document/url" ":document/date" ":document/citekey"
                      ":document/doi" ":document/venue" ":document/authors"
                      ":document/bibtex" ":document/csl-json"]
    :entity/notes ":document/type is :publication/:reference/:diploma/:talk."}
   {:entity/type :hobby
    :entity/label "Hobby / Interest"
    :entity/required [":hobby/name"]
    :entity/optional [":hobby/description" ":item/tags"]
    :entity/notes "Optional personal interests section."}
   {:entity/type :cv
    :entity/label "CV Solver Config"
    :entity/required [":cv/id" ":cv/title" ":cv/section-order" ":cv/style"]
    :entity/optional [":cv/person" ":cv/include" ":cv/exclude" ":cv/rules"
                      ":cv/overrides" ":cv/order" ":cv/summary-override"]
    :entity/notes "Targets a tailored CV via tags, rules, and overrides."}])

(def edn-starter-example
  "Valid minimal EDN facts vector covering all Easy CV entity types."
  (str "[{:item/uuid #uuid \"11111111-1111-4111-8111-111111111111\"\n"
       "  :item/type :person\n"
       "  :person/name \"Ada Lovelace\"\n"
       "  :person/email \"ada@example.org\"\n"
       "  :person/location \"London, UK\"\n"
       "  :person/summary \"Systems engineer building reliable tools.\"\n"
       "  :person/links [\"https://github.com/ada\"]}\n"
       " {:item/uuid #uuid \"22222222-2222-4222-8222-222222222222\"\n"
       "  :item/type :org\n"
       "  :org/name \"Analytical Labs\"\n"
       "  :org/type :company\n"
       "  :org/location \"London\"}\n"
       " {:item/uuid #uuid \"33333333-3333-4333-8333-333333333333\"\n"
       "  :item/type :history\n"
       "  :item/tags #{:systems :clojure}\n"
       "  :history/type :work\n"
       "  :history/title \"Staff Engineer\"\n"
       "  :history/organization"
       " #uuid \"22222222-2222-4222-8222-222222222222\"\n"
       "  :history/start-date \"2022-03\"\n"
       "  :history/end-date nil\n"
       "  :history/highlights [\"Led distributed parser rewrite\"]}\n"
       " {:item/uuid #uuid \"44444444-4444-4444-8444-444444444444\"\n"
       "  :item/type :skill\n"
       "  :skill/name \"Clojure\"\n"
       "  :skill/category \"Languages\"\n"
       "  :skill/level \"Expert\"}\n"
       " {:item/uuid #uuid \"55555555-5555-4555-8555-555555555555\"\n"
       "  :item/type :project\n"
       "  :project/name \"Easy CV\"\n"
       "  :project/description \"Local-first CV archive and solver\"\n"
       "  :project/start-date \"2024-01\"}\n"
       " {:item/uuid #uuid \"66666666-6666-4666-8666-666666666666\"\n"
       "  :item/type :document\n"
       "  :document/name \"Notes on Analytical Engines\"\n"
       "  :document/type :publication\n"
       "  :document/date \"2024-06\"\n"
       "  :document/citekey \"lovelace2024notes\"}\n"
       " {:item/uuid #uuid \"77777777-7777-4777-8777-777777777777\"\n"
       "  :item/type :hobby\n"
       "  :hobby/name \"Classical Piano\"\n"
       "  :hobby/description \"Chamber music\"}]"))

(def yaml-starter-example
  "Valid minimal YAML facts list for Easy CV."
  (str "- item/uuid: \"11111111-1111-4111-8111-111111111111\"\n"
       "  item/type: person\n"
       "  person/name: \"Ada Lovelace\"\n"
       "  person/email: \"ada@example.org\"\n"
       "  person/location: \"London, UK\"\n"
       "  person/summary: \"Systems engineer building reliable tools.\"\n"
       "- item/uuid: \"22222222-2222-4222-8222-222222222222\"\n"
       "  item/type: org\n"
       "  org/name: \"Analytical Labs\"\n"
       "  org/type: company\n"
       "- item/uuid: \"33333333-3333-4333-8333-333333333333\"\n"
       "  item/type: history\n"
       "  item/tags:\n"
       "    - systems\n"
       "  history/type: work\n"
       "  history/title: \"Staff Engineer\"\n"
       "  history/organization: \"22222222-2222-4222-8222-222222222222\"\n"
       "  history/start-date: \"2022-03\"\n"
       "  history/highlights:\n"
       "    - \"Led distributed parser rewrite\"\n"
       "- item/uuid: \"44444444-4444-4444-8444-444444444444\"\n"
       "  item/type: skill\n"
       "  skill/name: \"Clojure\"\n"
       "  skill/category: \"Languages\"\n"
       "- item/uuid: \"55555555-5555-4555-8555-555555555555\"\n"
       "  item/type: project\n"
       "  project/name: \"Easy CV\"\n"
       "  project/description: \"Local-first CV archive and solver\"\n"
       "  project/start-date: \"2024-01\""))

(def json-ld-starter-example
  "Valid Schema.org Person JSON-LD starter payload."
  (str "{\n"
       "  \"@context\": \"https://schema.org\",\n"
       "  \"@type\": \"Person\",\n"
       "  \"name\": \"Ada Lovelace\",\n"
       "  \"email\": \"ada@example.org\",\n"
       "  \"description\": \"Systems engineer building reliable tools.\",\n"
       "  \"address\": {\"@type\": \"PostalAddress\","
       " \"addressLocality\": \"London, UK\"},\n"
       "  \"sameAs\": [\"https://github.com/ada\"],\n"
       "  \"knowsAbout\": [\"Clojure\", \"Distributed Systems\"],\n"
       "  \"worksFor\": [{\n"
       "    \"@type\": \"OrganizationRole\",\n"
       "    \"roleName\": \"Staff Engineer\",\n"
       "    \"startDate\": \"2022-03\",\n"
       "    \"description\": \"Led distributed parser rewrite.\",\n"
       "    \"worksFor\": {\"@type\": \"Organization\","
       " \"name\": \"Analytical Labs\"}\n"
       "  }],\n"
       "  \"hasCredential\": [{\n"
       "    \"@type\": \"EducationalOccupationalCredential\",\n"
       "    \"credentialCategory\": \"BSc Computer Science\",\n"
       "    \"about\": \"Computing Systems\",\n"
       "    \"startDate\": \"2018-09\",\n"
       "    \"endDate\": \"2022-05\",\n"
       "    \"recognizedBy\": {\"@type\": \"EducationalOrganization\","
       " \"name\": \"Imperial College\"}\n"
       "  }],\n"
       "  \"workExample\": [{\n"
       "    \"@type\": \"CreativeWork\",\n"
       "    \"name\": \"Easy CV\",\n"
       "    \"description\": \"Local-first CV archive and solver\",\n"
       "    \"dateCreated\": \"2024-01\"\n"
       "  }]\n"
       "}"))

(def bibtex-starter-example
  "Valid BibTeX starter entry."
  (str "@article{lovelace2024notes,\n"
       "  author = {Ada Lovelace and Charles Babbage},\n"
       "  title = {Notes on Analytical {Engines}},\n"
       "  journal = {Journal of Computing},\n"
       "  year = {2024},\n"
       "  month = {jun},\n"
       "  doi = {10.1000/easycv.1},\n"
       "  url = {https://example.org/notes}\n"
       "}"))

(def csl-json-starter-example
  "Valid Zotero CSL-JSON starter array."
  (str "[\n"
       "  {\n"
       "    \"id\": \"lovelace2024notes\",\n"
       "    \"type\": \"article-journal\",\n"
       "    \"title\": \"Notes on Analytical Engines\",\n"
       "    \"author\": [{\"given\": \"Ada\", \"family\": \"Lovelace\"}],\n"
       "    \"container-title\": \"Journal of Computing\",\n"
       "    \"issued\": {\"date-parts\": [[2024, 6]]},\n"
       "    \"DOI\": \"10.1000/easycv.1\",\n"
       "    \"URL\": \"https://example.org/notes\"\n"
       "  }\n"
       "]"))

(def latex-starter-example
  "Minimal LaTeX resume snippet."
  (str "\\documentclass[11pt]{article}\n"
       "\\begin{document}\n"
       "\\section*{Ada Lovelace}\n"
       "London, UK $\\cdot$ ada@example.org\n"
       "\\section*{Experience}\n"
       "\\textbf{Staff Engineer}, Analytical Labs (2022-03 -- Present)\n"
       "\\end{document}"))

(def ^:private json-ld-entities
  [{:entity/type :person
    :entity/label "Schema.org Person (Root)"
    :entity/required ["@type: \"Person\"" "name"]
    :entity/optional ["email" "telephone" "url" "description" "address"
                      "sameAs" "knowsAbout"]
    :entity/notes "Maps to :person/* and :skill/* facts."}
   {:entity/type :history
    :entity/label "worksFor (OrganizationRole) & hasCredential"
    :entity/required ["roleName / credentialCategory" "startDate"]
    :entity/optional ["endDate" "description" "worksFor" "recognizedBy"
                      "alumniOf"]
    :entity/notes "Maps to :history/* (:work/:education) and :org/*."}
   {:entity/type :project
    :entity/label "workExample (CreativeWork / ScholarlyArticle)"
    :entity/required ["name / headline"]
    :entity/optional ["description" "url" "dateCreated" "datePublished"
                      "identifier" "isPartOf" "author"]
    :entity/notes (str "CreativeWork -> :project/*;"
                       " ScholarlyArticle -> :document/*.")}])

(def ^:private doc-only-entities
  [{:entity/type :document
    :entity/label "Document / Publication Fact"
    :entity/required [":document/name" ":document/type" ":document/citekey"]
    :entity/optional [":document/authors" ":document/venue" ":document/date"
                      ":document/doi" ":document/url"]
    :entity/notes "Entries import directly as :document/* archive facts."}])

(def format-guides-map
  "Complete per-format guide metadata keyed by format keyword."
  {:edn
   {:guide/format :edn
    :guide/label "EDN (Native Facts & CVConfig)"
    :guide/importable? true
    :guide/summary
    "Native Clojure data vector of facts or single Person/CVConfig map."
    :guide/required-keys [":person/name" ":person/email" ":org/name"
                          ":org/type" ":history/type" ":history/title"
                          ":history/start-date" ":skill/name" ":project/name"
                          ":document/name" ":document/type" ":hobby/name"
                          ":cv/id" ":cv/title" ":cv/section-order"
                          ":cv/style"]
    :guide/optional-keys [":item/uuid" ":item/type" ":item/tags"
                          ":item/archived?" ":history/organization"
                          ":history/end-date" ":history/highlights"
                          ":project/skills" ":cv/rules" ":cv/overrides"]
    :guide/entities core-entities
    :guide/tips ["Use #uuid \"...\" literals so refs link cleanly."
                 "Dates use ISO YYYY-MM (or YYYY / YYYY-MM-DD)."
                 "Paste a vector [...] of maps to import multiple facts."]
    :guide/example-payload edn-starter-example}
   :yaml
   {:guide/format :yaml
    :guide/label "YAML (Fact List or CV Map)"
    :guide/importable? true
    :guide/summary
    "Human-friendly YAML using Easy CV namespaced keys (item/type, etc.)."
    :guide/required-keys ["person/name" "person/email" "org/name" "org/type"
                          "history/type" "history/title" "history/start-date"
                          "skill/name" "project/name" "document/name"
                          "hobby/name" "cv/id"]
    :guide/optional-keys ["item/uuid" "item/type" "item/tags"
                          "history/organization" "history/end-date"
                          "history/highlights" "project/skills"]
    :guide/entities core-entities
    :guide/tips ["Quote dates like \"2022-03\" so YAML keeps strings."
                 "Keywords like item/type: work decode automatically."
                 "Reference org item/uuid strings in history/organization."]
    :guide/example-payload yaml-starter-example}
   :json-ld
   {:guide/format :json-ld
    :guide/label "JSON-LD (Schema.org Person)"
    :guide/importable? true
    :guide/summary
    "Standard Schema.org Person graph mapped into Easy CV archive facts."
    :guide/required-keys ["@type" "name"]
    :guide/optional-keys ["@context" "email" "telephone" "url" "description"
                          "address" "sameAs" "knowsAbout" "worksFor"
                          "hasCredential" "alumniOf" "workExample"]
    :guide/entities json-ld-entities
    :guide/tips ["Root object must have \"@type\": \"Person\" and \"name\"."
                 "Use worksFor OrganizationRole for work history entries."
                 "Use hasCredential for degrees and workExample for projects."]
    :guide/example-payload json-ld-starter-example}
   :bibtex
   {:guide/format :bibtex
    :guide/label "BibTeX (Publications & Talks)"
    :guide/importable? true
    :guide/summary
    "Imports @article, @inproceedings, @book, or @misc as :document/* facts."
    :guide/required-keys ["citekey" "title"]
    :guide/optional-keys ["author" "journal" "booktitle" "year" "month"
                          "doi" "url"]
    :guide/entities doc-only-entities
    :guide/tips ["@misc maps to :document/type :talk; others :publication."
                 "Separate multiple authors with 'and'."
                 "Nested braces in titles like {Easy} are preserved."]
    :guide/example-payload bibtex-starter-example}
   :csl-json
   {:guide/format :csl-json
    :guide/label "CSL-JSON (Zotero / Citation.js)"
    :guide/importable? true
    :guide/summary
    "Imports Zotero CSL-JSON array, single item, or {items: [...]}."
    :guide/required-keys ["title"]
    :guide/optional-keys ["id" "citation-key" "type" "author"
                          "container-title" "issued" "DOI" "URL"]
    :guide/entities doc-only-entities
    :guide/tips ["type \"speech\" maps to :talk; others map to :publication."
                 "Use issued.date-parts: [[YYYY, MM]] for ISO dates."
                 "Both {\"given\",\"family\"} and {\"literal\"} authors work."]
    :guide/example-payload csl-json-starter-example}
   :latex
   {:guide/format :latex
    :guide/label "LaTeX (Export Preview Only)"
    :guide/importable? false
    :guide/summary
    "Read-only LaTeX source preview; import data via EDN, YAML, or JSON-LD."
    :guide/required-keys [":cv/person" ":cv/sections" ":cv/style"]
    :guide/optional-keys [":document/bibtex"]
    :guide/entities core-entities
    :guide/tips ["Click 'Load Active CV' to inspect generated LaTeX source."
                 "Switch to Preview & Export to compile PDF via BusyTeX."
                 "Switch format to EDN, YAML, or JSON-LD to import new facts."]
    :guide/example-payload latex-starter-example}})
