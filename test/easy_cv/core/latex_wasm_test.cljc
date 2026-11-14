(ns easy-cv.core.latex-wasm-test
  "Unit and property-based tests for BusyTeX LaTeX WASM payload logic."
  (:require [clojure.string :as str]
            [clojure.test :refer [deftest is testing]]
            [clojure.test.check.clojure-test :refer [defspec]]
            [clojure.test.check.generators :as gen]
            [clojure.test.check.properties :as prop]
            [easy-cv.core.latex-wasm :as lw]
            [easy-cv.core.render.latex :as render-tex]
            [malli.core :as m]))

(def ^:private sample-canonical
  {:cv/id "cv-test"
   :cv/person {:person/name "Ada Lovelace"
               :person/email "ada@example.org"
               :person/location "London"
               :person/summary "Mathematician & pioneer."}
   :cv/sections
   [{:section/id :work
     :section/items [{:history/title "Analytical Engine Lead"
                      :history/start-date "1842"
                      :history/organization {:org/name "Babbage Lab"}}]}
    {:section/id :skills
     :section/items [{:skill/name "Clojure" :skill/level "Expert"}]}]})

(def ^:private gen-engine
  (gen/elements [:pdflatex :xelatex :lualatex :unknown nil]))

(def ^:private gen-request
  (gen/hash-map
   :tex gen/string-ascii
   :bib (gen/one-of [(gen/return nil) (gen/return "") gen/string-ascii])
   :engine gen-engine
   :bibtex? gen/boolean))

(defspec payload-always-satisfies-malli-schema-pbt 60
  (prop/for-all [req gen-request]
                (let [payload (lw/build-compile-payload req)]
                  (and (m/validate lw/BusyTexPayload payload)
                       (m/validate lw/EngineKey (:driver payload))
                       (= (:input payload) (:tex req))))))

(defspec bib-file-included-iff-non-blank-pbt 60
  (prop/for-all [tex gen/string-ascii
                 bib (gen/one-of [(gen/return "  ") gen/string-ascii])]
                (let [payload (lw/build-compile-payload {:tex tex :bib bib})
                      files (:additional-files payload)]
                  (if (str/blank? bib)
                    (empty? files)
                    (and (= 1 (count files))
                         (= "references.bib" (:path (first files))))))))

(deftest fixture-cv-produces-valid-compile-payload
  (testing "Rendered Easy CV .tex + .bib converts into BusyTexPayload"
    (let [rendered (render-tex/render-latex sample-canonical {})
          payload (lw/build-compile-payload
                   (assoc rendered :engine :pdflatex))]
      (is (m/validate lw/BusyTexPayload payload))
      (is (str/includes? (:input payload) "\\begin{document}"))
      (is (= :pdflatex (:driver payload))))))

(deftest pdf-magic-header-and-summary-checks
  (testing "Validates %PDF- prefix bytes and CompileOutcome schema"
    (is (lw/pdf-header? [37 80 68 70 45 49 46 55]))
    (is (not (lw/pdf-header? [37 80 68 70])))
    (is (m/validate lw/CompileOutcome
                    (lw/summarize-result
                     {:success true :exit-code 0 :log "ok"
                      :pdf-bytes 1024})))))
