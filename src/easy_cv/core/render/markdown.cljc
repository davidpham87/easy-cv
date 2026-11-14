(ns easy-cv.core.render.markdown
  "Pure .cljc GitHub-Flavored Markdown CV renderer with YAML frontmatter."
  (:require [clojure.string :as str]))

(defn- escape-md-line
  "Collapse newlines in single-line Markdown headings and bullets."
  [s]
  (-> (str (or s ""))
      (str/replace #"[\r\n]+" " ")
      str/trim))

(defn- format-dates
  "Format start and optional end date for Markdown output."
  [{:history/keys [start-date end-date] :as item}]
  (let [sd (or start-date (:project/start-date item) (:document/date item))
        ed (or end-date (:project/end-date item))]
    (when sd
      (str (escape-md-line sd)
           (when (or start-date (:project/start-date item))
             (str " -- " (if ed (escape-md-line ed) "Present")))))))

(defn- render-history-md
  "Format one work/education/volunteer item as a Markdown bullet."
  [item]
  (let [org (get-in item [:history/organization :org/name])
        dates (format-dates item)]
    (str "- **" (escape-md-line (:history/title item)) "**"
         (when org (str " - " (escape-md-line org)))
         (when dates (str " (" dates ")"))
         (when-let [d (:history/description item)]
           (str ": " (escape-md-line d)))
         "\n")))

(defn- render-project-md
  "Format one Project item as a Markdown bullet."
  [item]
  (str "- **" (escape-md-line (:project/name item)) "**"
       (when-let [d (format-dates item)] (str " (" d ")"))
       (when-let [desc (:project/description item)]
         (str ": " (escape-md-line desc)))
       "\n"))

(defn- render-skill-md
  "Format one Skill item as a Markdown bullet."
  [item]
  (str "- **" (escape-md-line (:skill/name item)) "**"
       (when-let [lvl (:skill/level item)]
         (str " (" (escape-md-line lvl) ")"))
       "\n"))

(defn- render-doc-md
  "Format one Document item as a Markdown bullet."
  [item]
  (str "- **" (escape-md-line (:document/name item)) "**"
       (when (seq (:document/authors item))
         (str " - " (escape-md-line (str/join ", " (:document/authors item)))))
       (when-let [v (:document/venue item)]
         (str " *" (escape-md-line v) "*"))
       (when-let [d (:document/date item)]
         (str " (" (escape-md-line d) ")"))
       "\n"))

(defn- render-hobby-md
  "Format one Hobby item as a Markdown bullet."
  [item]
  (str "- **" (escape-md-line (:hobby/name item)) "**"
       (when-let [d (:hobby/description item)]
         (str ": " (escape-md-line d)))
       "\n"))

(defn- render-generic-md
  "Fallback Markdown bullet formatter for custom sections."
  [item]
  (str "- " (escape-md-line (pr-str item)) "\n"))

(defn- section-md-fn
  "Select Markdown item formatter by section id."
  [sec-id]
  (case sec-id
    (:work :education :volunteer) render-history-md
    :projects render-project-md
    :skills render-skill-md
    :documents render-doc-md
    :hobbies render-hobby-md
    render-generic-md))

(defn- render-section-md
  "Render one CanonicalCV section as Markdown."
  [{:section/keys [id items]}]
  (if (empty? items)
    ""
    (let [heading (-> (clojure.core/name id)
                      (str/replace "-" " ")
                      str/capitalize)
          rf (section-md-fn id)]
      (str "## " heading "\n\n"
           (apply str (map rf items))
           "\n"))))

(defn- render-frontmatter
  "Build YAML frontmatter header for Markdown CV export."
  [canonical]
  (let [p (:cv/person canonical)
        style (:cv/style canonical)]
    (str "---\n"
         "title: " (pr-str (escape-md-line (:cv/title canonical))) "\n"
         "author: " (pr-str (escape-md-line (:person/name p))) "\n"
         "theme: " (name (:theme style)) "\n"
         "layout: " (name (:layout style)) "\n"
         "---\n\n")))

(defn render-markdown
  "Render CanonicalCV into a GFM Markdown string with YAML frontmatter."
  ([canonical] (render-markdown canonical {}))
  ([canonical _opts]
   (let [p (:cv/person canonical)
         body (apply str (map render-section-md (:cv/sections canonical)))]
     (str (render-frontmatter canonical)
          "# " (escape-md-line (:person/name p)) "\n\n"
          (escape-md-line (:person/email p))
          (when-let [loc (:person/location p)]
            (str " | " (escape-md-line loc)))
          "\n\n"
          (when-let [sum (:person/summary p)]
            (str (escape-md-line sum) "\n\n"))
          body))))
