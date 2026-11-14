(ns easy-cv.core.bibtex
  "Pure .cljc BibTeX parser, Zotero CSL-JSON normalizer, and exporter."
  (:require [clojure.string :as str]))

(def ^:private month-map
  {"jan" "01" "feb" "02" "mar" "03" "apr" "04" "may" "05" "jun" "06"
   "jul" "07" "aug" "08" "sep" "09" "oct" "10" "nov" "11" "dec" "12"
   "1" "01" "2" "02" "3" "03" "4" "04" "5" "05" "6" "06"
   "7" "07" "8" "08" "9" "09" "10" "10" "11" "11" "12" "12"})

(defn- pad2
  "Zero-pad integers < 10 to two digits."
  [n]
  (if (< n 10) (str "0" n) (str n)))

(defn- clean-bib-str
  "Normalize whitespace and strip protective BibTeX braces."
  [s]
  (-> (str/trim (or s ""))
      (str/replace #"[\{\}]" "")
      (str/replace #"\s+" " ")))

(defn- scan-balanced-end
  "Find index of matching closing brace starting inside depth=1."
  [s start-idx]
  (let [n (count s)]
    (loop [i start-idx depth 1 in-q? false]
      (cond
        (zero? depth) (dec i)
        (>= i n) nil
        :else
        (let [ch (.charAt s i)
              prev (when (pos? i) (.charAt s (dec i)))]
          (cond
            (and (= ch \") (not= prev \\) (= depth 1))
            (recur (inc i) depth (not in-q?))
            (and (= ch \{) (not in-q?))
            (recur (inc i) (inc depth) in-q?)
            (and (= ch \}) (not in-q?))
            (recur (inc i) (dec depth) in-q?)
            :else
            (recur (inc i) depth in-q?)))))))

(defn- read-one-entry
  "Read one @type{...} entry starting at at-idx, returning [next-idx map]."
  [text at-idx]
  (let [sub (subs text at-idx)
        m (re-find #"^@([A-Za-z]+)\s*\{" sub)]
    (if-not m
      [(inc at-idx) nil]
      (let [etype (str/lower-case (second m))
            open-pos (+ at-idx (count (first m)))
            end-pos (scan-balanced-end text open-pos)]
        (when-not end-pos
          (throw (ex-info "Unbalanced braces in BibTeX entry"
                          {:errors ["Unbalanced braces in BibTeX"]})))
        [(inc end-pos)
         {:entry/kind etype
          :entry/body (subs text open-pos end-pos)
          :entry/raw (subs text at-idx (min (count text) (inc end-pos)))}]))))

(defn- extract-raw-entries
  "Scan text for @type{...} BibTeX entries, throwing on unclosed braces."
  [text]
  (loop [idx 0 acc []]
    (if-let [at-idx (str/index-of text "@" idx)]
      (let [[next-idx entry] (read-one-entry text at-idx)]
        (recur next-idx (if entry (conj acc entry) acc)))
      acc)))

(defn- step-comma-scan
  "Advance one char in top-level BibTeX comma scanner."
  [body i start depth in-q? parts]
  (let [ch (.charAt body i)
        prev (when (pos? i) (.charAt body (dec i)))]
    (cond
      (and (= ch \") (not= prev \\) (zero? depth))
      [(inc i) start depth (not in-q?) parts]
      (and (= ch \{) (not in-q?))
      [(inc i) start (inc depth) in-q? parts]
      (and (= ch \}) (not in-q?) (pos? depth))
      [(inc i) start (dec depth) in-q? parts]
      (and (= ch \,) (zero? depth) (not in-q?))
      [(inc i) (inc i) depth in-q? (conj parts (subs body start i))]
      :else
      [(inc i) start depth in-q? parts])))

(defn- split-top-commas
  "Split BibTeX body string on commas at brace depth 0 outside quotes."
  [body]
  (let [n (count body)]
    (loop [i 0 start 0 depth 0 in-q? false parts []]
      (if (>= i n)
        (conj parts (subs body start))
        (let [[ni ns nd nq np] (step-comma-scan body i start depth in-q? parts)]
          (recur ni ns nd nq np))))))

(defn- unwrap-bib-val
  "Remove outer {} or \"\" wrapper from a BibTeX field value."
  [raw-val]
  (let [v (str/trim raw-val)]
    (cond
      (and (str/starts-with? v "{") (str/ends-with? v "}"))
      (clean-bib-str (subs v 1 (dec (count v))))
      (and (str/starts-with? v "\"") (str/ends-with? v "\""))
      (clean-bib-str (subs v 1 (dec (count v))))
      :else (clean-bib-str v))))

(defn- parse-fields-map
  "Parse key = value BibTeX segments into lowercase string map."
  [segments]
  (reduce (fn [acc seg]
            (if-let [eq-idx (str/index-of seg "=")]
              (let [k (-> (subs seg 0 eq-idx) str/trim str/lower-case)
                    v (unwrap-bib-val (subs seg (inc eq-idx)))]
                (if (str/blank? k) acc (assoc acc k v)))
              acc))
          {}
          segments))

(defn- parse-bib-date
  "Format BibTeX year and optional month into ISO partial date."
  [{:strs [year month]}]
  (when (and year (re-matches #"\d{4}" (str/trim year)))
    (let [y (str/trim year)
          m-key (some-> month str/trim str/lower-case
                        (subs 0 (min 3 (count (str/trim (or month ""))))))
          mm (or (get month-map (some-> month str/trim str/lower-case))
                 (get month-map m-key))]
      (if mm (str y "-" mm) y))))

(defn- parse-authors
  "Split BibTeX 'and'-delimited author string into vector of names."
  [author-str]
  (when-not (str/blank? author-str)
    (->> (str/split author-str #"(?i)\s+and\s+")
         (map str/trim)
         (remove str/blank?)
         vec)))

(defn- entry->csl
  "Build normalized CSL-JSON map from a document fact map."
  [doc]
  (let [authors (mapv (fn [a] {"literal" a}) (:document/authors doc))
        dparts (when-let [d (:document/date doc)]
                 [(mapv parse-long (str/split d #"-"))])]
    (cond-> {"id" (:document/citekey doc)
             "type" (if (= :talk (:document/type doc))
                      "speech"
                      "article-journal")
             "title" (:document/name doc)}
      (seq authors) (assoc "author" authors)
      (:document/venue doc) (assoc "container-title" (:document/venue doc))
      (:document/doi doc) (assoc "DOI" (:document/doi doc))
      (:document/url doc) (assoc "URL" (:document/url doc))
      dparts (assoc "issued" {"date-parts" dparts}))))

(defn fact->csl-json
  "Convert a :document/* fact map into normalized CSL-JSON."
  [doc]
  (or (:document/csl-json doc) (entry->csl doc)))

(defn- build-bib-fact
  "Assemble :document/* fact map from parsed BibTeX metadata."
  [kind citekey raw flds]
  (let [venue (some #(get flds %) ["journal" "booktitle" "publisher"
                                   "school" "institution"])
        authors (parse-authors (get flds "author"))
        pub-d (parse-bib-date flds)]
    (cond-> {:item/uuid (random-uuid) :item/type :document
             :document/citekey citekey
             :document/name (or (not-empty (get flds "title")) citekey)
             :document/type (if (= "misc" kind) :talk :publication)
             :document/bibtex raw}
      (seq authors) (assoc :document/authors authors)
      (not (str/blank? venue)) (assoc :document/venue venue)
      (not (str/blank? (get flds "doi"))) (assoc :document/doi (get flds "doi"))
      (not (str/blank? (get flds "url"))) (assoc :document/url (get flds "url"))
      pub-d (assoc :document/date pub-d))))

(defn- entry->fact
  "Convert parsed BibTeX entry record into a :document/* fact map."
  [{:entry/keys [kind body raw]}]
  (let [segs (split-top-commas body)
        citekey (str/trim (or (first segs) ""))]
    (when (str/blank? citekey)
      (throw (ex-info "BibTeX entry missing citekey"
                      {:errors ["BibTeX entry missing citekey"]})))
    (let [base (build-bib-fact kind citekey raw (parse-fields-map (rest segs)))]
      (assoc base :document/csl-json (entry->csl base)))))

(defn parse-bibtex
  "Parse raw BibTeX text into a vector of :document/* fact maps."
  [text]
  (when (str/blank? text)
    (throw (ex-info "BibTeX input is blank"
                    {:errors ["BibTeX input is blank"]})))
  (let [entries (->> (extract-raw-entries text)
                     (remove #(#{"comment" "preamble" "string"}
                               (:entry/kind %))))]
    (when (empty? entries)
      (throw (ex-info "No valid BibTeX entries found"
                      {:errors ["No valid BibTeX entries found"]})))
    (mapv entry->fact entries)))

(defn- str-keys
  "Normalize shallow keyword keys in a CSL-JSON map to string keys."
  [m]
  (if (map? m)
    (reduce-kv (fn [acc k v]
                 (assoc acc (if (keyword? k) (subs (str k) 1) (str k)) v))
               {}
               m)
    m))

(defn- format-csl-author
  "Format a CSL-JSON author map into a single display string."
  [a]
  (let [m (str-keys a)]
    (or (get m "literal")
        (let [parts (remove str/blank? [(get m "given") (get m "family")])]
          (when (seq parts) (str/join " " parts))))))

(defn- format-csl-date
  "Format CSL-JSON issued date-parts into YYYY, YYYY-MM, or YYYY-MM-DD."
  [issued]
  (let [parts (first (get (str-keys issued) "date-parts"))]
    (when (and (sequential? parts) (integer? (first parts)))
      (let [[y m d] parts]
        (cond
          (and y m d) (str y "-" (pad2 m) "-" (pad2 d))
          (and y m) (str y "-" (pad2 m))
          :else (str y))))))

(defn- build-csl-fact
  "Build normalized :document/* fact map from validated CSL-JSON item."
  [item title]
  (let [citekey (or (get item "citation-key") (some-> (get item "id") str)
                    (str "ref-" (subs (str (random-uuid)) 0 8)))
        authors (vec (keep format-csl-author (get item "author")))
        venue (or (get item "container-title") (get item "publisher")
                  (get item "event-title"))
        dtype (if (= "speech" (get item "type")) :talk :publication)
        pub-d (format-csl-date (get item "issued"))]
    (cond-> {:item/uuid (random-uuid) :item/type :document
             :document/name title :document/citekey citekey
             :document/type dtype :document/csl-json item}
      (seq authors) (assoc :document/authors authors)
      (not (str/blank? venue)) (assoc :document/venue venue)
      (not (str/blank? (get item "DOI"))) (assoc :document/doi (get item "DOI"))
      (not (str/blank? (get item "URL"))) (assoc :document/url (get item "URL"))
      pub-d (assoc :document/date pub-d))))

(defn csl-item->fact
  "Normalize a single Zotero CSL-JSON item map into a :document/* fact."
  [raw-item]
  (let [item (str-keys raw-item)
        title (or (get item "title") (get item "container-title"))]
    (when (str/blank? title)
      (throw (ex-info "CSL-JSON item missing title"
                      {:errors ["CSL-JSON item missing title"]})))
    (build-csl-fact item title)))

(defn csl-json->facts
  "Normalize parsed Zotero CSL-JSON (array, map, or {:items [...]}) to facts."
  [csl-data]
  (let [items (cond
                (sequential? csl-data) csl-data
                (map? csl-data) (or (:items csl-data)
                                    (get csl-data "items")
                                    [csl-data])
                :else [])]
    (when (empty? items)
      (throw (ex-info "Empty CSL-JSON payload"
                      {:errors ["Expected CSL-JSON item or array"]})))
    (mapv csl-item->fact items)))

(defn- format-bib-entry
  "Format one :document/* fact map into a valid @article/@misc BibTeX string."
  [doc]
  (let [etype (if (= :talk (:document/type doc)) "misc" "article")
        key (or (:document/citekey doc) "citekey")
        year (some-> (:document/date doc) (str/split #"-") first)
        month (some-> (:document/date doc) (str/split #"-") second)
        flds (cond-> [["title" (:document/name doc)]]
               (seq (:document/authors doc))
               (conj ["author" (str/join " and " (:document/authors doc))])
               (:document/venue doc)
               (conj ["journal" (:document/venue doc)])
               year (conj ["year" year])
               month (conj ["month" month])
               (:document/doi doc) (conj ["doi" (:document/doi doc)])
               (:document/url doc) (conj ["url" (:document/url doc)]))
        lines (map (fn [[k v]] (str "  " k " = {" (clean-bib-str v) "}"))
                   flds)]
    (str "@" etype "{" key ",\n" (str/join ",\n" lines) "\n}")))

(defn facts->bibtex
  "Export a collection of :document/* fact maps as a BibTeX string."
  [docs]
  (let [items (if (map? docs) [docs] docs)]
    (str/join "\n\n" (map format-bib-entry items))))
