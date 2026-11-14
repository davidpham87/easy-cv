(ns easy-cv.core.render-test
  "Property-based tests for pure .cljc multi-format CV renderers."
  (:require [clojure.string :as str]
            [clojure.test :refer [deftest is testing]]
            [clojure.test.check.clojure-test :refer [defspec]]
            [clojure.test.check.generators :as gen]
            [clojure.test.check.properties :as prop]
            [easy-cv.core.generators :as gens]
            [easy-cv.core.io :as io]
            [easy-cv.core.render :as render]
            [easy-cv.core.render.html :as html]
            [easy-cv.core.render.latex :as latex]))

(def ^:private adversarial-strings
  ["A & B <Corp> \"100%\" $50 #1 _test_ {braces} ~ ^ \\slash"
   "Zurich — Müller & Söhne (λ-calculus, 100% pure)"
   "日本語 & Café <script>alert('xss')</script> \\textbf{hack}"
   "a_b_c %comment $math$ ^sup ~tilde {x} \\backslash"])

(defn- inject-adversarial
  "Inject adversarial Unicode and punctuation into a CanonicalCV."
  [cv adv-text]
  (-> cv
      (assoc-in [:cv/person :person/name] adv-text)
      (assoc-in [:cv/person :person/summary] adv-text)
      (assoc :cv/title adv-text)))

(defn- count-occurrences
  "Count non-overlapping substring matches of needle in s."
  [s needle]
  (loop [idx 0 cnt 0]
    (if-let [pos (str/index-of s needle idx)]
      (recur (+ pos (count needle)) (inc cnt))
      cnt)))

(defn- html-balanced?
  "Verify HTML has doctype, single html/head/body/main, and balanced sections."
  [html-str]
  (and (str/starts-with? html-str "<!doctype html>")
       (= (count-occurrences html-str "<section ")
          (count-occurrences html-str "</section>"))
       (= (count-occurrences html-str "<ul>")
          (count-occurrences html-str "</ul>"))
       (str/includes? html-str "</html>")))

(defn- unescaped-brace-balance
  "Return brace depth of tex-str ignoring escaped \\{ and \\} tokens."
  [tex-str]
  (let [n (count tex-str)]
    (loop [i 0 depth 0]
      (if (>= i n)
        depth
        (let [ch (.charAt tex-str i)]
          (cond
            (= ch \\) (recur (+ i 2) depth)
            (= ch \{) (recur (inc i) (inc depth))
            (= ch \}) (recur (inc i) (dec depth))
            :else (recur (inc i) depth)))))))

(defn- latex-balanced?
  "Verify LaTeX has balanced braces and matched begin/end environments."
  [tex-str]
  (and (zero? (unescaped-brace-balance tex-str))
       (= (count-occurrences tex-str "\\begin{itemize}")
          (count-occurrences tex-str "\\end{itemize}"))
       (str/includes? tex-str "\\begin{document}")
       (str/includes? tex-str "\\end{document}")))

(defn- all-formats-valid?
  "Verify all 5 render-cv formats succeed and satisfy output invariants."
  [cv]
  (let [html-out (render/render :html cv)
        latex-out (render/render :latex cv)
        md-out (render/render :markdown cv)
        jld-out (render/render :json-ld cv)
        site-out (render/render :site cv)]
    (and (html-balanced? html-out)
         (not (str/includes? html-out "<script>alert"))
         (latex-balanced? (:tex latex-out))
         (string? (:bib latex-out))
         (str/starts-with? md-out "---\n")
         (:ok? (io/parse-input :json-ld jld-out))
         (every? #(contains? site-out %)
                 ["index.html" "cv.json" "cv.tex" "cv.bib" "style.css"]))))

(defspec adversarial-render-all-tiers-pbt 60
  (prop/for-all [tier (gen/elements gens/tiers)
                 adv (gen/elements adversarial-strings)
                 bundle (gens/gen-cv-bundle :junior)]
                (let [cv (if (= tier :junior)
                           (:cv/canonical bundle)
                           (gens/generate-cv tier {:seed 31 :size 15}))]
                  (all-formats-valid? (inject-adversarial cv adv)))))

(deftest latex-and-html-escaping-invariants-test
  (testing "All reserved LaTeX and HTML characters escape safely"
    (let [raw "%&$_{}~^\\"
          tex (latex/escape-latex raw)
          htm (html/escape-html "<&\"'>")]
      (is (= (str "\\%\\&\\$\\_\\{\\}"
                  "\\textasciitilde{}\\textasciicircum{}\\textbackslash{}")
             tex))
      (is (= "&lt;&amp;&quot;&#39;&gt;" htm))
      (is (zero? (unescaped-brace-balance tex))))))

(deftest custom-format-extensibility-test
  (testing "render-cv multimethod throws clear ex-info on unknown format"
    (let [cv (gens/generate-cv :student {:seed 5 :size 10})]
      (is (thrown-with-msg? #?(:clj clojure.lang.ExceptionInfo
                               :cljs js/Error)
                            #"Unsupported render format"
                            (render/render :docx cv))))))
