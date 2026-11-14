(ns easy-cv.core.react-pdf
  "Pure .cljc document model mapping CanonicalCV into @react-pdf/renderer data."
  (:require [clojure.string :as str]))

(def PdfItemLine
  "Malli schema for one formatted resume entry row."
  [:map
   [:pdf/title :string]
   [:pdf/subtitle {:optional true} [:maybe :string]]
   [:pdf/meta {:optional true} [:maybe :string]]
   [:pdf/detail {:optional true} [:maybe :string]]])

(def PdfSection
  "Malli schema for one rendered CV section block."
  [:map
   [:pdf/section-id :keyword]
   [:pdf/heading :string]
   [:pdf/items [:vector PdfItemLine]]])

(def ReactPdfDoc
  "Malli schema for pure document spec consumed by fx.react-pdf."
  [:map
   [:pdf/page-size [:enum "A4" "LETTER"]]
   [:pdf/accent-hex :string]
   [:pdf/font-family [:enum "Helvetica" "Times-Roman" "Courier"]]
   [:pdf/person-name :string]
   [:pdf/contact-line :string]
   [:pdf/summary {:optional true} [:maybe :string]]
   [:pdf/sections [:vector PdfSection]]])

(defn- theme->accent-hex
  "Map design preset keyword to a hex accent color string."
  [design]
  (case design
    :academic "#1e3a8a"
    :nordic "#2e3440"
    :solarized "#b58900"
    :minimal "#111827"
    "#0f172a"))

(defn- font->pdf-family
  "Map Easy CV font preset keyword to built-in standard PDF font family."
  [font]
  (case font
    :serif "Times-Roman"
    :mono "Courier"
    "Helvetica"))

(defn- format-dates
  "Format item start/end date range when available."
  [item]
  (let [sd (or (:history/start-date item)
               (:project/start-date item)
               (:document/date item))
        ed (or (:history/end-date item) (:project/end-date item))]
    (when sd
      (if (or (:history/start-date item) (:project/start-date item))
        (str sd " - " (or ed "Present"))
        sd))))

(defn format-item-lines
  "Convert a CanonicalCV section item into a PdfItemLine map."
  [sec-id item]
  (case sec-id
    (:work :education :volunteer)
    {:pdf/title (str (or (:history/title item) ""))
     :pdf/subtitle (get-in item [:history/organization :org/name])
     :pdf/meta (format-dates item)
     :pdf/detail (:history/description item)}
    :projects
    {:pdf/title (str (or (:project/name item) ""))
     :pdf/meta (format-dates item)
     :pdf/detail (:project/description item)}
    :skills
    {:pdf/title (str (or (:skill/name item) ""))
     :pdf/meta (:skill/level item)}
    :documents
    {:pdf/title (str (or (:document/name item) ""))
     :pdf/subtitle (when (seq (:document/authors item))
                     (str/join ", " (:document/authors item)))
     :pdf/meta (:document/date item)
     :pdf/detail (:document/venue item)}
    :hobbies
    {:pdf/title (str (or (:hobby/name item) ""))
     :pdf/detail (:hobby/description item)}
    {:pdf/title (pr-str item)}))

(defn- section->pdf-section
  "Convert a non-empty CanonicalCV section into a PdfSection map."
  [{:section/keys [id items]}]
  (when (seq items)
    {:pdf/section-id id
     :pdf/heading (-> (name id) (str/replace "-" " ") str/upper-case)
     :pdf/items (mapv #(format-item-lines id %) items)}))

(defn- contact-summary
  "Build compact single-line header metadata from Person map."
  [person]
  (->> [(:person/email person) (:person/location person) (:person/phone person)]
       (remove str/blank?)
       (str/join " | ")))

(defn canonical->pdf-model
  "Purely transform CanonicalCV and UI style-cfg into a ReactPdfDoc map."
  [canonical style-cfg]
  (when canonical
    (let [person (:cv/person canonical)
          design (or (:design style-cfg) (get-in canonical [:cv/style :theme]))
          font (or (:font style-cfg) :sans)
          secs (->> (:cv/sections canonical)
                    (keep section->pdf-section)
                    vec)]
      {:pdf/page-size (if (= :letter (:page-size style-cfg)) "LETTER" "A4")
       :pdf/accent-hex (theme->accent-hex design)
       :pdf/font-family (font->pdf-family font)
       :pdf/person-name (str (or (:person/name person) "Curriculum Vitae"))
       :pdf/contact-line (contact-summary person)
       :pdf/summary (:person/summary person)
       :pdf/sections secs})))
