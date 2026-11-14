(ns e2e.react-pdf-test
  "Playwright E2E tests verifying in-browser @react-pdf/renderer PDF builds."
  (:require [cljs.test :refer [async deftest is testing]]
            [clojure.string :as str]
            [clojure.test.check.clojure-test :refer [defspec]]
            [clojure.test.check.generators :as gen]
            [clojure.test.check.properties :as prop]
            [e2e.support :as support]
            [easy-cv.core.react-pdf :as rp]
            [malli.core :as m]))

(defn- trigger-react-pdf-flow!
  "Opens Preview view, clicks Instant PDF (React-PDF), and checks PDF blob."
  [^js page url]
  (let [^js btn (.locator page "[data-testid='compile-react-pdf-btn']")
        ^js status-el (.locator page "[data-testid='latex-compile-status']")]
    (-> (.goto page (str url "#/preview") #js {:waitUntil "domcontentloaded"})
        (.then #(.waitFor btn #js {:timeout 10000}))
        (.then #(.click btn))
        (.then #(support/wait-for-pdf-status! page 20000))
        (.then #(.textContent status-el))
        (.then (fn [status-txt]
                 (-> (support/read-pdf-blob-info page)
                     (.then (fn [info]
                              {:status-txt status-txt
                               :src (.-src info)
                               :head (.-head info)
                               :bytes (.-bytes info)}))))))))

(deftest compile-cv-react-pdf-e2e-test
  (testing "React-PDF renders Easy CV into a valid %PDF- blob in Chromium"
    (async done
           (-> (support/with-app-page
                 (fn [page url errors]
                   (-> (trigger-react-pdf-flow! page url)
                       (.then (fn [{:keys [status-txt src head bytes]}]
                                (is (str/includes? status-txt "Compiled PDF"))
                                (is (str/starts-with? (str src) "blob:"))
                                (is (= "%PDF-" head))
                                (is (> bytes 500))
                                (is (empty? @errors)))))))
               (.catch (fn [err] (is (nil? err))))
               (.finally done)))))

(defspec react-pdf-section-id-keyword-pbt 40
  (prop/for-all [sname (gen/not-empty gen/string-alphanumeric)
                 title (gen/not-empty gen/string-alphanumeric)]
                (let [canon {:cv/id "c1"
                             :cv/person {:person/name sname
                                         :person/email "a@b.org"}
                             :cv/sections
                             [{:section/id :projects
                               :section/items [{:project/name title}]}]}
                      cfg {:font :mono :design :nordic}
                      doc (rp/canonical->pdf-model canon cfg)]
                  (and (m/validate rp/ReactPdfDoc doc)
                       (= "Courier" (:pdf/font-family doc))))))
