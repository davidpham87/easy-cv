(ns easy-cv.core.honeysql-test
  (:require [clojure.string :as str]
            [clojure.test :refer [testing]]
            [clojure.test.check.clojure-test :refer [defspec]]
            [clojure.test.check.generators :as gen]
            [clojure.test.check.properties :as prop]
            [easy-cv.core.honeysql :as hsql]))

(defn- count-placeholders
  [sql-str]
  (count (re-seq #"\?" sql-str)))

(def ^:private gen-hostile-str
  (gen/one-of [gen/string-ascii
               (gen/elements ["'; DROP TABLE facts; --"
                              "Ada \"O'Connor\""
                              "a?b?c"
                              ""])]))

(defspec ddl-statements-valid-sql-test 10
  (testing "DDL statements generate IF NOT EXISTS CREATE TABLE SQL"
    (prop/for-all [_ (gen/return nil)]
                  (let [stmts (hsql/ddl-statements)]
                    (and (= 3 (count stmts))
                         (every? #(and (= 1 (count %))
                                       (str/includes?
                                        (first %)
                                        "CREATE TABLE IF NOT EXISTS"))
                                 stmts))))))

(defspec upsert-fact-parameterized-test 50
  (testing "Fact upserts parameterize hostile strings without injection"
    (prop/for-all [uuid gen/uuid
                   etype (gen/elements [:person :history :skill :project])
                   transit gen-hostile-str
                   ts gen/nat
                   arch (gen/elements [0 1])]
                  (let [[sql-str & params]
                        (hsql/upsert-fact-query
                         {:fact/uuid uuid
                          :fact/entity-type etype
                          :fact/attrs-transit transit
                          :fact/updated-at ts
                          :fact/archived arch})]
                    (and (str/starts-with? sql-str "INSERT INTO facts")
                         (= (count-placeholders sql-str) (count params))
                         (= 5 (count params)))))))

(defspec upsert-config-and-snapshot-parameterized-test 50
  (testing "Config and snapshot queries parameterize all values"
    (prop/for-all [uuid gen/uuid
                   title gen-hostile-str
                   cfg-str gen-hostile-str
                   ts gen/nat]
                  (let [[c-sql & c-params]
                        (hsql/upsert-cv-config-query
                         {:cv/uuid uuid
                          :cv/title title
                          :cv/config-transit cfg-str
                          :cv/updated-at ts})
                        [s-sql & s-params]
                        (hsql/insert-snapshot-query
                         {:snapshot/id uuid
                          :snapshot/parent-id nil
                          :snapshot/label title
                          :snapshot/db-transit cfg-str
                          :snapshot/created-at ts})]
                    (and (= (count-placeholders c-sql) (count c-params))
                         (= 4 (count c-params))
                         (= (count-placeholders s-sql) (count s-params))
                         (= 5 (count s-params)))))))

(defspec sql-export-round-trip-test 30
  (testing "db->sql-export produces valid SqlExportMap"
    (prop/for-all [fact-rows (gen/vector (gen/return {:fact/uuid "f1"}))
                   cfg-rows (gen/vector (gen/return {:cv/uuid "c1"}))
                   snap-rows (gen/vector (gen/return {:snapshot/id "s1"}))]
                  (let [rows-by-tbl {:facts fact-rows
                                     :cv_configs cfg-rows
                                     :snapshots snap-rows}
                        q->tbl (into {}
                                     (map (fn [[k v]] [v k])
                                          hsql/export-select-queries))
                        exported (hsql/db->sql-export
                                  #(get rows-by-tbl (get q->tbl %))
                                  "2026-11-14T20:00:00Z")]
                    (and (hsql/valid-export? exported)
                         (= fact-rows (get-in exported [:tables :facts]))
                         (= cfg-rows (get-in exported [:tables :cv_configs]))
                         (= snap-rows
                            (get-in exported [:tables :snapshots])))))))
