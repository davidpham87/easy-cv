(ns easy-cv.core.io-test
  "Round-trip PBT and invalid-input tests for EDN, YAML, JSON-LD, BibTeX, CSL."
  (:require [clojure.test :refer [deftest is testing]]
            [clojure.test.check.clojure-test :refer [defspec]]
            [clojure.test.check.properties :as prop]
            [easy-cv.core.generators :as gens]
            [easy-cv.core.io :as io]
            [easy-cv.core.schema :as schema]
            [malli.core :as m]))

(defn- edn-roundtrip-ok?
  "Verify CanonicalCV survives EDN export and parse with exact equality."
  [bundle]
  (let [cv (:cv/canonical bundle)
        out (io/export-output :edn cv)
        res (io/parse-input :edn out)]
    (and (:ok? res)
         (= cv (:io/data res))
         (m/validate schema/CanonicalCV (:io/data res)))))

(defspec edn-roundtrip-pbt 40
  (prop/for-all [bundle (gens/gen-cv-bundle :junior)]
                (edn-roundtrip-ok? bundle)))

(defn- yaml-roundtrip-ok?
  "Verify CanonicalCV survives YAML export and parse with exact equality."
  [bundle]
  (let [cv (:cv/canonical bundle)
        out (io/export-output :yaml cv)
        res (io/parse-input :yaml out)]
    (and (:ok? res)
         (= cv (:io/data res))
         (m/validate schema/CanonicalCV (:io/data res)))))

(defspec yaml-roundtrip-pbt 40
  (prop/for-all [bundle (gens/gen-cv-bundle :junior)]
                (yaml-roundtrip-ok? bundle)))

(defn- json-ld-roundtrip-ok?
  "Verify CanonicalCV exports to Schema.org JSON-LD and re-imports validly."
  [bundle]
  (let [cv (:cv/canonical bundle)
        jld-str (io/export-output :json-ld cv)
        res (io/parse-input :json-ld jld-str)
        facts-res (io/parse-input :json-ld jld-str {:target :facts})
        imported (:io/data res)]
    (and (:ok? res)
         (:ok? facts-res)
         (m/validate schema/CanonicalCV imported)
         (= (get-in cv [:cv/person :person/name])
            (get-in imported [:cv/person :person/name]))
         (seq (:io/data facts-res)))))

(defspec json-ld-roundtrip-pbt 40
  (prop/for-all [bundle (gens/gen-cv-bundle :mid)]
                (json-ld-roundtrip-ok? bundle)))

(defn- bibtex-and-csl-roundtrip-ok?
  "Verify generated Document facts round-trip through BibTeX and CSL-JSON."
  [archive]
  (let [docs (:archive/documents archive)]
    (if (empty? docs)
      true
      (let [bib-str (io/export-output :bibtex docs)
            bib-res (io/parse-input :bibtex bib-str)
            csl-str (io/export-output :csl-json docs)
            csl-res (io/parse-input :csl-json csl-str)]
        (and (:ok? bib-res)
             (:ok? csl-res)
             (= (mapv :document/citekey docs)
                (mapv :document/citekey (:io/data bib-res)))
             (= (mapv :document/name docs)
                (mapv :document/name (:io/data csl-res)))
             (every? #(m/validate schema/Document %) (:io/data bib-res))
             (every? #(m/validate schema/Document %) (:io/data csl-res)))))))

(defspec bibtex-and-csl-roundtrip-pbt 40
  (prop/for-all [archive (gens/gen-archive :senior)]
                (bibtex-and-csl-roundtrip-ok? archive)))

(deftest bibtex-nested-braces-and-zotero-csl-test
  (testing "Pure .cljc BibTeX handles nested braces and Zotero CSL-JSON"
    (let [bib (str "@inproceedings{hickey2011simple,\n"
                   "  author = {Rich Hickey and Jane Doe},\n"
                   "  title = {Simple Made {Easy}},\n"
                   "  booktitle = {Strange Loop},\n"
                   "  year = {2011},\n"
                   "  month = {oct}\n}")
          res (io/parse-input :bibtex bib)
          doc (first (:io/data res))
          csl-json (io/export-output :csl-json [doc])
          csl-res (io/parse-input :csl-json csl-json)]
      (is (:ok? res))
      (is (= "Simple Made Easy" (:document/name doc)))
      (is (= ["Rich Hickey" "Jane Doe"] (:document/authors doc)))
      (is (= "2011-10" (:document/date doc)))
      (is (:ok? csl-res))
      (is (= "hickey2011simple"
             (:document/citekey (first (:io/data csl-res))))))))

(deftest invalid-inputs-return-structured-errors-test
  (testing "Malformed inputs yield {:ok? false :errors ...} without throwing"
    (let [bad-edn (io/parse-input :edn "{:unclosed")
          bad-yaml (io/parse-input :yaml "cv/id: primary\ncv/title: \"\"")
          bad-jld (io/parse-input :json-ld "{\"@type\":\"Organization\"}")
          bad-bib (io/parse-input :bibtex "@article{unclosed, title={x}")
          bad-csl (io/parse-input :csl-json "[]")
          bad-fmt (io/parse-input :xml "<cv/>")]
      (doseq [r [bad-edn bad-yaml bad-jld bad-bib bad-csl bad-fmt]]
        (is (false? (:ok? r)))
        (is (some? (:errors r)))))))
