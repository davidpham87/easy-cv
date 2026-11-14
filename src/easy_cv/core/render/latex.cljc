(ns easy-cv.core.render.latex
  "Pure .cljc LaTeX (.tex + .bib) CV renderer with full special-char escaping."
  (:require [clojure.string :as str]
            [easy-cv.core.bibtex :as bibtex]))

(def ^:private latex-char-escapes
  {\\ "\\textbackslash{}"
   \{ "\\{"
   \} "\\}"
   \$ "\\$"
   \& "\\&"
   \# "\\#"
   \_ "\\_"
   \% "\\%"
   \~ "\\textasciitilde{}"
   \^ "\\textasciicircum{}"})

(defn escape-latex
  "Escape all LaTeX special characters (%&$_{}~^\\) in a single pass."
  [s]
  (str/escape (str (or s "")) latex-char-escapes))

(defn- format-dates
  "Format start and optional end dates for LaTeX output."
  [{:history/keys [start-date end-date] :as item}]
  (let [sd (or start-date (:project/start-date item) (:document/date item))
        ed (or end-date (:project/end-date item))]
    (when sd
      (str (escape-latex sd)
           (when (or start-date (:project/start-date item))
             (str " -- " (if ed (escape-latex ed) "Present")))))))

(defn- render-history-tex
  "Format one work/education/volunteer item as a LaTeX \\item."
  [item]
  (let [org (get-in item [:history/organization :org/name])
        dates (format-dates item)
        desc (:history/description item)]
    (str "\\item \\textbf{" (escape-latex (:history/title item)) "}"
         (when org (str " --- " (escape-latex org)))
         (when dates (str " \\hfill " dates))
         (when desc (str "\\\\\n  " (escape-latex desc)))
         "\n")))

(defn- render-project-tex
  "Format one Project item as a LaTeX \\item."
  [item]
  (str "\\item \\textbf{" (escape-latex (:project/name item)) "}"
       (when-let [d (format-dates item)] (str " \\hfill " d))
       (when-let [desc (:project/description item)]
         (str "\\\\\n  " (escape-latex desc)))
       "\n"))

(defn- render-skill-tex
  "Format one Skill item as a LaTeX \\item."
  [item]
  (str "\\item \\textbf{" (escape-latex (:skill/name item)) "}"
       (when-let [lvl (:skill/level item)]
         (str " (" (escape-latex lvl) ")"))
       "\n"))

(defn- render-doc-tex
  "Format one Document item as a LaTeX \\item."
  [item]
  (str "\\item \\textbf{" (escape-latex (:document/name item)) "}"
       (when (seq (:document/authors item))
         (str " --- " (escape-latex (str/join ", " (:document/authors item)))))
       (when-let [v (:document/venue item)]
         (str " \\textit{" (escape-latex v) "}"))
       (when-let [d (:document/date item)]
         (str " (" (escape-latex d) ")"))
       "\n"))

(defn- render-hobby-tex
  "Format one Hobby item as a LaTeX \\item."
  [item]
  (str "\\item \\textbf{" (escape-latex (:hobby/name item)) "}"
       (when-let [d (:hobby/description item)]
         (str ": " (escape-latex d)))
       "\n"))

(defn- render-generic-tex
  "Fallback LaTeX item formatter for custom section entries."
  [item]
  (str "\\item " (escape-latex (pr-str item)) "\n"))

(defn- section-tex-fn
  "Select LaTeX item formatter by section id."
  [sec-id]
  (case sec-id
    (:work :education :volunteer) render-history-tex
    :projects render-project-tex
    :skills render-skill-tex
    :documents render-doc-tex
    :hobbies render-hobby-tex
    render-generic-tex))

(defn- render-section-tex
  "Render one CanonicalCV section as a LaTeX \\section* with itemize."
  [{:section/keys [id items]}]
  (if (empty? items)
    ""
    (let [heading (-> (clojure.core/name id)
                      (str/replace "-" " ")
                      str/capitalize)
          rf (section-tex-fn id)]
      (str "\\section*{" (escape-latex heading) "}\n"
           "\\begin{itemize}\n"
           (apply str (map rf items))
           "\\end{itemize}\n\n"))))

(defn render-bib
  "Extract :documents section items and export as a BibTeX string."
  [canonical]
  (let [docs (->> (:cv/sections canonical)
                  (filter #(= :documents (:section/id %)))
                  (mapcat :section/items)
                  vec)]
    (if (seq docs)
      (bibtex/facts->bibtex docs)
      "")))

(defn- render-preamble
  "Build LaTeX document preamble."
  []
  (str "\\documentclass[11pt,a4paper]{article}\n"
       "\\usepackage[utf8]{inputenc}\n"
       "\\usepackage[T1]{fontenc}\n"
       "\\usepackage[margin=18mm]{geometry}\n"
       "\\begin{document}\n\n"))

(defn render-tex
  "Render CanonicalCV into a compilable LaTeX .tex string."
  ([canonical] (render-tex canonical {}))
  ([canonical _opts]
   (let [p (:cv/person canonical)
         body (apply str (map render-section-tex (:cv/sections canonical)))]
     (str (render-preamble)
          "\\begin{center}\n"
          "{\\LARGE\\textbf{" (escape-latex (:person/name p)) "}}\\\\\n"
          (escape-latex (:person/email p))
          (when-let [loc (:person/location p)]
            (str " \\textbar{} " (escape-latex loc)))
          "\n\\end{center}\n\n"
          (when-let [sum (:person/summary p)]
            (str (escape-latex sum) "\n\n"))
          body
          "\\end{document}\n"))))

(defn render-latex
  "Render CanonicalCV into {:tex <string> :bib <string>}."
  ([canonical] (render-latex canonical {}))
  ([canonical opts]
   {:tex (render-tex canonical opts)
    :bib (render-bib canonical)}))
