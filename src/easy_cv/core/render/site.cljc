(ns easy-cv.core.render.site
  "Pure .cljc static portfolio website generator for multi-file export."
  (:require [easy-cv.core.io :as io]
            [easy-cv.core.render.html :as html]
            [easy-cv.core.render.latex :as latex]))

(defn- nav-bar
  "Build shared HTML navigation links for generated static site pages."
  []
  (str "<nav class=\"cv-nav\">"
       "<a href=\"index.html\">Overview</a> | "
       "<a href=\"projects.html\">Projects</a> | "
       "<a href=\"publications.html\">Publications</a> | "
       "<a href=\"cv.json\">JSON-LD</a> | "
       "<a href=\"cv.tex\">LaTeX</a>"
       "</nav>"))

(defn- wrap-subpage
  "Wrap a filtered CanonicalCV section page in standalone HTML5."
  [title heading body-html]
  (str "<!doctype html><html lang=\"en\"><head><meta charset=\"utf-8\">"
       "<title>" (html/escape-html title) "</title>"
       "<link rel=\"stylesheet\" href=\"style.css\"></head>"
       "<body><main id=\"cv-sheet\">"
       (nav-bar)
       "<h1>" (html/escape-html heading) "</h1>"
       body-html
       "</main></body></html>"))

(defn- subpage-section-html
  "Render a single named section from CanonicalCV for subpages."
  [canonical sec-id]
  (let [sec (some #(when (= sec-id (:section/id %)) %)
                  (:cv/sections canonical))]
    (if sec
      (html/render-section-html sec)
      "")))

(defn render-site
  "Render CanonicalCV into a filename->content map for static deployment."
  ([canonical] (render-site canonical {}))
  ([canonical opts]
   (let [style (:cv/style canonical)
         css (html/render-css style)
         {:keys [tex bib]} (latex/render-latex canonical opts)
         pname (get-in canonical [:cv/person :person/name])
         proj-html (subpage-section-html canonical :projects)
         pub-html (subpage-section-html canonical :documents)]
     {"index.html" (html/render-html canonical opts)
      "projects.html" (wrap-subpage (str pname " - Projects")
                                    "Projects" proj-html)
      "publications.html" (wrap-subpage (str pname " - Publications")
                                        "Publications" pub-html)
      "cv.json" (io/export-output :json-ld canonical)
      "cv.tex" tex
      "cv.bib" bib
      "style.css" css})))
