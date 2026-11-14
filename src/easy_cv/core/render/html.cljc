(ns easy-cv.core.render.html
  "Pure .cljc standalone HTML5 CV renderer with inline print CSS."
  (:require [clojure.string :as str]
            [easy-cv.core.io :as io]))

(def ^:private html-escapes
  {\& "&amp;"
   \< "&lt;"
   \> "&gt;"
   \" "&quot;"
   \' "&#39;"})

(defn escape-html
  "Escape HTML special characters in a single pass."
  [s]
  (str/escape (str (or s "")) html-escapes))

(defn- format-dates
  "Format start-date and optional end-date into an HTML-escaped range."
  [{:history/keys [start-date end-date] :as item}]
  (let [sd (or start-date (:project/start-date item) (:document/date item))
        ed (or end-date (:project/end-date item))]
    (when sd
      (str (escape-html sd)
           (when (or start-date (:project/start-date item))
             (str " &ndash; " (if ed (escape-html ed) "Present")))))))

(defn- render-history-li
  "Render one work, education, or volunteer HistoryItem as HTML."
  [item]
  (let [org-name (get-in item [:history/organization :org/name])
        dates (format-dates item)
        desc (:history/description item)]
    (str "<li class=\"cv-item\">"
         "<div class=\"cv-item-head\"><strong>"
         (escape-html (:history/title item)) "</strong>"
         (when org-name (str " &mdash; " (escape-html org-name)))
         (when dates (str "<span class=\"cv-dates\">" dates "</span>"))
         "</div>"
         (when desc (str "<p>" (escape-html desc) "</p>"))
         "</li>")))

(defn- render-project-li
  "Render one Project item as HTML."
  [item]
  (let [skills (mapv #(if (map? %) (:skill/name %) (str %))
                     (:project/skills item))]
    (str "<li class=\"cv-item\">"
         "<div class=\"cv-item-head\"><strong>"
         (escape-html (:project/name item)) "</strong>"
         (when-let [d (format-dates item)]
           (str "<span class=\"cv-dates\">" d "</span>"))
         "</div>"
         (when-let [d (:project/description item)]
           (str "<p>" (escape-html d) "</p>"))
         (when (seq skills)
           (str "<div class=\"cv-tags\">"
                (escape-html (str/join ", " skills)) "</div>"))
         "</li>")))

(defn- render-skill-li
  "Render one Skill item as an HTML badge/row."
  [item]
  (str "<li class=\"cv-skill\"><strong>"
       (escape-html (:skill/name item)) "</strong>"
       (when-let [lvl (:skill/level item)]
         (str " (" (escape-html lvl) ")"))
       "</li>"))

(defn- render-doc-li
  "Render one Document/Publication item as HTML."
  [item]
  (str "<li class=\"cv-item\"><strong>"
       (escape-html (:document/name item)) "</strong>"
       (when (seq (:document/authors item))
         (str " &mdash; "
              (escape-html (str/join ", " (:document/authors item)))))
       (when-let [v (:document/venue item)]
         (str " <em>" (escape-html v) "</em>"))
       (when-let [d (:document/date item)]
         (str " (" (escape-html d) ")"))
       "</li>"))

(defn- render-hobby-li
  "Render one Hobby item as HTML."
  [item]
  (str "<li class=\"cv-item\"><strong>"
       (escape-html (:hobby/name item)) "</strong>"
       (when-let [d (:hobby/description item)]
         (str ": " (escape-html d)))
       "</li>"))

(defn- render-generic-li
  "Fallback renderer for custom accreted section items."
  [item]
  (str "<li class=\"cv-item\">" (escape-html (pr-str item)) "</li>"))

(defn- item-renderer
  "Select HTML list-item renderer by section id."
  [sec-id]
  (case sec-id
    (:work :education :volunteer) render-history-li
    :projects render-project-li
    :skills render-skill-li
    :documents render-doc-li
    :hobbies render-hobby-li
    render-generic-li))

(defn render-section-html
  "Render one CanonicalCV section as an HTML <section> element."
  [{:section/keys [id items]}]
  (if (empty? items)
    ""
    (let [heading (-> (clojure.core/name id)
                      (str/replace "-" " ")
                      str/capitalize)
          rf (item-renderer id)]
      (str "<section class=\"cv-section\" data-section=\""
           (escape-html (clojure.core/name id)) "\">"
           "<h2>" (escape-html heading) "</h2>"
           "<ul>" (apply str (map rf items)) "</ul>"
           "</section>"))))

(defn render-css
  "Generate self-contained CSS rules from CVStyle."
  [style]
  (let [font (get-in style [:font :body] "Inter")
        hfont (get-in style [:font :heading] font)]
    (str ":root{--cv-font-body:\"" (escape-html font) "\",sans-serif;"
         "--cv-font-head:\"" (escape-html hfont) "\",sans-serif;}"
         "body{font-family:var(--cv-font-body);max-width:820px;"
         "margin:2rem auto;padding:0 1rem;color:#111;line-height:1.5}"
         "h1,h2{font-family:var(--cv-font-head)}"
         "h2{border-bottom:1px solid #cbd5e1;padding-bottom:.25rem}"
         ".cv-dates{float:right;color:#475569;font-size:.9em}"
         "@media print{@page{size:A4;margin:14mm}body{margin:0}}")))

(defn- safe-json-ld-script
  "Wrap Schema.org JSON-LD safely inside a <script> tag."
  [canonical]
  (let [raw (io/export-output :json-ld canonical)
        escaped (str/replace raw "<" "\\u003c")]
    (str "<script type=\"application/ld+json\">" escaped "</script>")))

(defn- render-header
  "Render CV header block from person map."
  [p]
  (str "<header class=\"cv-header\">"
       "<h1>" (escape-html (:person/name p)) "</h1>"
       "<p class=\"cv-contact\">"
       (escape-html (:person/email p))
       (when-let [loc (:person/location p)]
         (str " &middot; " (escape-html loc)))
       "</p>"
       (when-let [sum (:person/summary p)]
         (str "<p class=\"cv-summary\">" (escape-html sum) "</p>"))
       "</header>"))

(defn render-html
  "Render a CanonicalCV map into a standalone HTML5 string."
  ([canonical] (render-html canonical {}))
  ([canonical opts]
   (let [p (:cv/person canonical)
         css (or (:css opts) (render-css (:cv/style canonical)))
         body (apply str (map render-section-html (:cv/sections canonical)))]
     (str "<!doctype html><html lang=\"en\"><head><meta charset=\"utf-8\">"
          "<title>" (escape-html (:cv/title canonical)) "</title>"
          "<style>" css "</style>"
          (safe-json-ld-script canonical)
          "</head><body><main id=\"cv-sheet\">"
          (render-header p) body
          "</main></body></html>"))))
