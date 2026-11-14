(ns e2e.latex-test
  "Playwright E2E tests verifying in-browser BusyTeX LaTeX WASM PDF builds."
  (:require [cljs.test :refer [async deftest is testing]]
            [clojure.string :as str]
            [clojure.test.check.clojure-test :refer [defspec]]
            [clojure.test.check.generators :as gen]
            [clojure.test.check.properties :as prop]
            [e2e.support :as support]
            [easy-cv.core.latex-wasm :as lw]
            [malli.core :as m]))

(defn- read-compiled-pdf-info
  "Fetches the blob iframe URL in-page and inspects its %PDF- header."
  [^js page]
  (.evaluate page
             (fn []
               (let [el (.querySelector js/document
                                        "[data-testid='compiled-pdf-frame']")
                     src (when el (.getAttribute el "src"))]
                 (if-not src
                   (js/Promise.resolve #js {:src nil :head nil :bytes 0})
                   (-> (js/fetch src)
                       (.then #(.arrayBuffer %))
                       (.then (fn [buf]
                                (let [u8 (js/Uint8Array. buf)
                                      head (.decode (js/TextDecoder.)
                                                    (.slice u8 0 5))]
                                  #js {:src src
                                       :head head
                                       :bytes (.-length u8)})))))))))

(defn- wait-for-latex-status!
  "Waits up to 90s for either compiled PDF status or error element."
  [^js page]
  (let [ok (.locator page "[data-testid='latex-compile-status']")
        err (.locator page "[data-testid='latex-compile-error']")]
    (js/Promise.race
     #js [(.waitFor ok #js {:timeout 90000})
          (-> (.waitFor err #js {:timeout 90000})
              (.then #(.textContent err))
              (.then #(throw (js/Error. (str "LaTeX compile error: " %)))))])))

(defn- trigger-compile-flow!
  "Opens Preview view and clicks Compile PDF (BusyTeX), returning PDF info."
  [^js page url]
  (let [^js compile-btn (.locator page "[data-testid='compile-latex-pdf-btn']")
        ^js status-el (.locator page "[data-testid='latex-compile-status']")]
    (-> (.goto page (str url "#/preview") #js {:waitUntil "domcontentloaded"})
        (.then #(.waitFor compile-btn #js {:timeout 10000}))
        (.then #(.click compile-btn))
        (.then #(wait-for-latex-status! page))
        (.then #(.textContent status-el))
        (.then (fn [status-txt]
                 (-> (read-compiled-pdf-info page)
                     (.then (fn [info]
                              {:status-txt status-txt
                               :src (.-src info)
                               :head (.-head info)
                               :bytes (.-bytes info)}))))))))

(deftest compile-cv-latex-to-pdf-e2e-test
  (testing "BusyTeX WASM compiles rendered Easy CV LaTeX into a valid PDF"
    (async done
           (-> (support/with-app-page
                 (fn [page url errors]
                   (-> (trigger-compile-flow! page url)
                       (.then (fn [{:keys [status-txt src head bytes]}]
                                (is (str/includes? status-txt "Compiled PDF"))
                                (is (str/starts-with? (str src) "blob:"))
                                (is (= "%PDF-" head))
                                (is (> bytes 1000))
                                (is (empty? @errors)))))))
               (.catch (fn [err] (is (nil? err))))
               (.finally done)))))

(defspec compiled-outcome-satisfies-schema-pbt 40
  (prop/for-all [ok? gen/boolean
                 bytes (gen/choose 0 500000)
                 log gen/string-ascii]
                (m/validate
                 lw/CompileOutcome
                 (lw/summarize-result
                  {:success ok? :pdf-bytes bytes :log log}))))
