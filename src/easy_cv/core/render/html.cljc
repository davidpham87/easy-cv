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

(defn- safe-hex
  "Return hex color string when valid #rgb/#rrggbb, else fallback."
  [v fallback]
  (if (and (string? v) (re-matches #"^#[0-9a-fA-F]{3,8}$" v))
    v
    fallback))

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

(defn- render-project-tags
  [skills]
  (when (seq skills)
    (str "<div class=\"cv-tags\">"
         (apply str (map #(str "<span class=\"cv-chip\">"
                               (escape-html %) "</span>")
                         skills))
         "</div>")))

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
         (render-project-tags skills)
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

(defn- chip-radius
  [chip-style]
  (case chip-style
    :pill "9999px"
    :tonal "8px"
    :outlined "6px"
    :squared "0px"
    :zenburn "4px"
    "9999px"))

(defn- css-vars-block
  [style]
  (let [font (get-in style [:font :body] "Inter")
        hfont (get-in style [:font :heading] font)
        colors (:colors style)]
    (str ":root{--cv-font-body:\"" (escape-html font) "\",sans-serif;"
         "--cv-font-head:\"" (escape-html hfont) "\",sans-serif;"
         "--cv-accent:" (safe-hex (:accent colors) "#1e3a8a") ";"
         "--cv-header-bg:" (safe-hex (:header-bg colors) "transparent") ";"
         "--cv-header-fg:" (safe-hex (:header-fg colors) "#0f172a") ";"
         "--cv-chip-bg:" (safe-hex (:chip-bg colors) "#f1f5f9") ";"
         "--cv-chip-fg:" (safe-hex (:chip-fg colors) "#1e293b") ";"
         "--cv-chip-border:" (safe-hex (:chip-border colors) "#cbd5e1") ";"
         "--cv-sheet-bg:" (safe-hex (:sheet-bg colors) "#ffffff") ";}")))

(defn- header-and-chip-css
  [style]
  (let [has-hdr-bg? (some? (get-in style [:colors :header-bg]))
        rad (chip-radius (:chip-style style))]
    (str ".cv-header{background:var(--cv-header-bg);color:var(--cv-header-fg);"
         (when has-hdr-bg? "padding:1.25rem 1.5rem;border-radius:8px;")
         "margin-bottom:1.25rem}"
         ".cv-header h1,.cv-header .cv-contact,.cv-header .cv-summary"
         "{color:var(--cv-header-fg)}"
         ".cv-tags{display:flex;flex-wrap:wrap;gap:.35rem;margin-top:.35rem}"
         ".cv-section[data-section=\"skills\"] ul{list-style:none;"
         "padding:0;display:flex;flex-wrap:wrap;gap:.4rem}"
         ".cv-skill,.cv-chip{display:inline-block;padding:.15rem .55rem;"
         "font-size:.82em;border-radius:" rad ";"
         "background:var(--cv-chip-bg);color:var(--cv-chip-fg);"
         "border:1px solid var(--cv-chip-border)}")))

(defn render-css
  "Generate self-contained CSS rules from CVStyle."
  [style]
  (str (css-vars-block style)
       "body{font-family:var(--cv-font-body);max-width:820px;"
       "margin:2rem auto;padding:1.5rem;color:#111;line-height:1.5;"
       "background:var(--cv-sheet-bg)}"
       "h1,h2{font-family:var(--cv-font-head)}"
       "h2{color:var(--cv-accent);"
       "border-bottom:1.5px solid var(--cv-accent);padding-bottom:.25rem}"
       ".cv-dates{float:right;color:#475569;font-size:.9em}"
       (header-and-chip-css style)
       "@media print{@page{size:A4;margin:14mm}body{margin:0}}"))

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
