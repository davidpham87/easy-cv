(ns e2e.support-test
  "Property-based tests for E2E static server path & MIME helpers."
  (:require ["path" :as path]
            [clojure.string :as str]
            [clojure.test.check.clojure-test :refer [defspec]]
            [clojure.test.check.generators :as gen]
            [clojure.test.check.properties :as prop]
            [e2e.support :as support]))

(defspec safe-resolve-blocks-traversal-pbt 50
  (prop/for-all [seg (gen/elements [".." "../etc/passwd" "/../secret"
                                    "css/app.css" "js/main.js" "/"])]
                (let [root (path/resolve "public")
                      resolved (support/safe-resolve "public" seg)]
                  (if (str/includes? seg "..")
                    (nil? resolved)
                    (and (string? resolved)
                         (str/starts-with? resolved root))))))

(defspec mime-for-known-extensions-pbt 30
  (prop/for-all [ext (gen/elements [".html" ".js" ".css" ".wasm" ".json"])]
                (let [ct (support/mime-for (str "asset" ext))]
                  (and (string? ct)
                       (not= "application/octet-stream" ct)))))
