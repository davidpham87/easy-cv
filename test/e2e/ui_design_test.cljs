(ns e2e.ui-design-test
  "Playwright E2E and PBT tests covering themes, archive graph, editor, style."
  (:require [cljs.test :refer [async deftest is testing]]
            [clojure.string :as str]
            [clojure.test.check.clojure-test :refer [defspec]]
            [clojure.test.check.generators :as gen]
            [clojure.test.check.properties :as prop]
            [e2e.support :as support]
            [easy-cv.core.archive-graph :as ag]
            [easy-cv.ui.styles :as styles]
            [easy-cv.ui.theme :as theme]))

(defn- read-bg-var
  "Reads --bg-page CSS custom property from documentElement."
  [^js page]
  (.evaluate page
             (fn []
               (.. js/document -documentElement -style
                   (getPropertyValue "--bg-page")))))

(defn- wait-for-bg-var!
  "Waits until --bg-page equals target hex string."
  [^js page hex]
  (.waitForFunction page
                    (fn [expected]
                      (= expected
                         (.. js/document -documentElement -style
                             (getPropertyValue "--bg-page"))))
                    hex
                    #js {:timeout 5000}))

(defn- switch-theme-select!
  "Selects theme value in [data-testid='theme-select'] and awaits hex."
  [^js page theme-val expected-hex]
  (let [^js sel (.locator page "[data-testid='theme-select']")]
    (-> (.selectOption sel theme-val)
        (.then #(wait-for-bg-var! page expected-hex))
        (.then #(read-bg-var page)))))

(defn- click-zenburn-theme!
  "Clicks the Zenburn quick theme pill and returns resulting --bg-page."
  [^js page]
  (let [^js zen-btn (.getByRole page "button" #js {:name "Zenburn"})]
    (-> (.waitFor zen-btn #js {:timeout 8000})
        (.then #(.click zen-btn))
        (.then #(wait-for-bg-var! page "#383838"))
        (.then #(read-bg-var page)))))

(defn- run-themes-flow!
  "Tests Zenburn quick pill and Gruvbox/Catppuccin select themes."
  [^js page url]
  (-> (.goto page url #js {:waitUntil "domcontentloaded"})
      (.then #(click-zenburn-theme! page))
      (.then (fn [zen-bg]
               (-> (switch-theme-select! page "gruvbox" "#282828")
                   (.then (fn [gruv-bg]
                            (-> (switch-theme-select!
                                 page "catppuccin" "#1e1e2e")
                                (.then (fn [cat-bg]
                                         {:zenburn zen-bg
                                          :gruvbox gruv-bg
                                          :catppuccin cat-bg}))))))))))

(deftest zenburn-and-bb-themes-e2e-test
  (testing "Zenburn button and theme-select switch CSS --bg-page vars"
    (async done
           (-> (support/with-app-page
                 (fn [page url errors]
                   (-> (run-themes-flow! page url)
                       (.then (fn [res]
                                (is (= "#383838" (:zenburn res)))
                                (is (= "#282828" (:gruvbox res)))
                                (is (= "#1e1e2e" (:catppuccin res)))
                                (is (empty? @errors)))))))
               (.catch (fn [err] (is (nil? err))))
               (.finally done)))))

(defn- read-table-row-count
  "Counts rendered rows in the Fact Archive table tbody."
  [^js page]
  (.count (.locator page "table tbody tr")))

(defn- read-first-row-text
  "Reads text content of the first row in the Fact Archive table."
  [^js page]
  (.textContent (.first (.locator page "table tbody tr"))))

(defn- wait-for-json-inspector!
  "Waits until [data-testid='fact-inspector'] pre switches to JSON object."
  [^js page]
  (.waitForFunction page
                    (fn []
                      (let [el (.querySelector
                                js/document
                                "[data-testid='fact-inspector'] pre")
                            txt (when el (.trim (.-textContent el)))]
                        (if (and txt (> (.-length txt) 2))
                          (and (= 123 (.charCodeAt txt 0))
                               (>= (.indexOf txt "\":") 0))
                          false)))
                    nil
                    #js {:timeout 5000}))

(defn- wait-for-sort-dir-desc!
  "Waits until [data-testid='sort-dir-toggle'] text updates to Desc."
  [^js page]
  (.waitForFunction page
                    (fn []
                      (let [el (.querySelector
                                js/document
                                "[data-testid='sort-dir-toggle']")]
                        (if el
                          (>= (.indexOf (.-textContent el) "Desc") 0)
                          false)))
                    nil
                    #js {:timeout 5000}))

(defn- toggle-archive-sort!
  "Clicks sort-by-kind and sort-dir-toggle and returns button label."
  [^js page]
  (let [^js sort-kind (.locator page "[data-testid='sort-by-kind']")
        ^js sort-dir (.locator page "[data-testid='sort-dir-toggle']")]
    (-> (.click sort-kind)
        (.then #(.click sort-dir))
        (.then #(wait-for-sort-dir-desc! page))
        (.then #(.textContent sort-dir)))))

(defn- filter-by-first-tag!
  "Clicks first tag facet chip, checks row count, and toggles it off."
  [^js page dir-txt]
  (let [sel "[data-testid='tag-facet-chips'] button"
        ^js chip (.first (.locator page sel))]
    (-> (read-table-row-count page)
        (.then (fn [before-cnt]
                 (-> (.click chip)
                     (.then #(read-table-row-count page))
                     (.then (fn [after-cnt]
                              (-> (.click chip)
                                  (.then #(read-first-row-text page))
                                  (.then (fn [row-txt]
                                           {:dir-txt dir-txt
                                            :before before-cnt
                                            :after after-cnt
                                            :row row-txt})))))))))))

(defn- inspect-graph-node!
  "Clicks first graph node, checks EDN inspector, then toggles JSON."
  [^js page]
  (let [nsel "[data-testid='archive-network-graph'] g.graph-node"
        ^js node (.first (.locator page nsel))
        ^js pre (.locator page "[data-testid='fact-inspector'] pre")
        ^js json-btn (.locator page "[data-testid='inspector-fmt-json']")]
    (-> (.waitFor node #js {:timeout 8000})
        (.then #(.click node))
        (.then #(.waitFor pre #js {:timeout 5000}))
        (.then #(.textContent pre))
        (.then (fn [edn-txt]
                 (-> (.click json-btn)
                     (.then #(wait-for-json-inspector! page))
                     (.then #(.textContent pre))
                     (.then (fn [json-txt]
                              {:edn edn-txt :json json-txt}))))))))

(defn- run-archive-flow!
  "Navigates to #/archive and verifies sorting, filtering, graph, inspector."
  [^js page url]
  (let [^js graph (.locator page "[data-testid='archive-network-graph']")]
    (-> (.goto page (str url "#/archive") #js {:waitUntil "domcontentloaded"})
        (.then #(.waitFor graph #js {:timeout 8000}))
        (.then #(toggle-archive-sort! page))
        (.then #(filter-by-first-tag! page %))
        (.then (fn [tbl-res]
                 (-> (inspect-graph-node! page)
                     (.then #(merge tbl-res %))))))))

(deftest archive-graph-and-inspector-e2e-test
  (testing "Archive sorting, filtering, SVG graph click, and EDN/JSON inspect"
    (async done
           (-> (support/with-app-page
                 (fn [page url errors]
                   (-> (run-archive-flow! page url)
                       (.then (fn [{:keys [dir-txt before after row edn json]}]
                                (is (= "Desc ↓" dir-txt))
                                (is (> before 0))
                                (is (<= after before))
                                (is (not (str/blank? row)))
                                (is (str/includes? edn ":"))
                                (is (object? (js/JSON.parse json)))
                                (is (empty? @errors)))))))
               (.catch (fn [err] (is (nil? err))))
               (.finally done)))))

(defn- wait-for-textarea-populated!
  "Waits until the editor textarea value is non-empty."
  [^js page]
  (.waitForFunction page
                    (fn []
                      (let [ta (.querySelector js/document "textarea")]
                        (if ta
                          (> (.-length (.trim (.-value ta))) 0)
                          false)))
                    nil
                    #js {:timeout 5000}))

(defn- load-editor-example!
  "Clicks load-example-btn and reads editor buffer + validation text."
  [^js page]
  (let [^js load-btn (.locator page "[data-testid='load-example-btn']")
        ^js valid-el (.getByText page "No Malli validation errors.")
        ^js ta (.locator page "textarea")]
    (-> (.waitFor load-btn #js {:timeout 8000})
        (.then #(.click load-btn))
        (.then #(wait-for-textarea-populated! page))
        (.then #(.inputValue ta))
        (.then (fn [buf]
                 (-> (.isVisible valid-el)
                     (.then (fn [vis?] {:buf buf :valid? vis?}))))))))

(defn- inspect-schema-tab!
  "Clicks Schema Spec (Export) tab and returns panel text."
  [^js page]
  (let [^js sch-tab (.getByRole page "button"
                                #js {:name "Schema Spec (Export)"})
        ^js sch-panel (.locator page "[data-testid='schema-spec-panel']")]
    (-> (.click sch-tab)
        (.then #(.waitFor sch-panel #js {:timeout 5000}))
        (.then #(.textContent sch-panel)))))

(defn- wait-for-prompt-substr!
  "Waits until [data-testid='llm-prompt-output'] contains target substring."
  [^js page needle]
  (.waitForFunction page
                    (fn [s]
                      (let [el (.querySelector
                                js/document
                                "[data-testid='llm-prompt-output']")]
                        (if el (>= (.indexOf (.-textContent el) s) 0) false)))
                    needle
                    #js {:timeout 5000}))

(defn- inspect-llm-prompt-tab!
  "Clicks LLM PDF->CV Prompt tab and reads JSON-LD and EDN prompts."
  [^js page]
  (let [^js prm-tab (.getByRole page "button"
                                #js {:name "LLM PDF->CV Prompt"})
        ^js edn-btn (.getByRole page "button" #js {:name "EDN" :exact true})
        ^js prm-out (.locator page "[data-testid='llm-prompt-output']")]
    (-> (.click prm-tab)
        (.then #(.waitFor prm-out #js {:timeout 5000}))
        (.then #(.textContent prm-out))
        (.then (fn [jld-prm]
                 (-> (.click (.last edn-btn))
                     (.then #(wait-for-prompt-substr! page ":person/name"))
                     (.then #(.textContent prm-out))
                     (.then (fn [edn-prm]
                              {:json-ld-prompt jld-prm
                               :edn-prompt edn-prm}))))))))

(defn- run-editor-flow!
  "Navigates to #/editor and exercises starter example, schema, and prompt."
  [^js page url]
  (-> (.goto page (str url "#/editor") #js {:waitUntil "domcontentloaded"})
      (.then #(load-editor-example! page))
      (.then (fn [ex-res]
               (-> (inspect-schema-tab! page)
                   (.then (fn [sch-txt]
                            (-> (inspect-llm-prompt-tab! page)
                                (.then #(assoc (merge ex-res %)
                                               :schema sch-txt))))))))))

(deftest editor-guide-schema-and-llm-prompt-e2e-test
  (testing "Editor loads example, renders Schema Spec and LLM PDF->CV Prompt"
    (async done
           (-> (support/with-app-page
                 (fn [page url errors]
                   (-> (run-editor-flow! page url)
                       (.then (fn [{:keys [buf valid? schema
                                           json-ld-prompt edn-prompt]}]
                                (is (not (str/blank? buf)))
                                (is (true? valid?))
                                (is (str/includes? schema "Schema.org"))
                                (is (str/includes? schema "JSON Schema"))
                                (is (str/includes? json-ld-prompt "JSON-LD"))
                                (is (str/includes? json-ld-prompt "YYYY-MM"))
                                (is (str/includes? edn-prompt ":person/name"))
                                (is (empty? @errors)))))))
               (.catch (fn [err] (is (nil? err))))
               (.finally done)))))

(defn- wait-for-header-bg!
  "Waits until #cv-sheet header computed background matches expected rgb."
  [^js page rgb-str]
  (.waitForFunction page
                    (fn [expected]
                      (let [hdr (.querySelector js/document "#cv-sheet header")]
                        (if hdr
                          (= expected
                             (.-backgroundColor (js/getComputedStyle hdr)))
                          false)))
                    rgb-str
                    #js {:timeout 5000}))

(defn- wait-for-chip-bg!
  "Waits until #cv-sheet styled chip computed background matches expected."
  [^js page rgb-str]
  (.waitForFunction page
                    (fn [expected]
                      (let [chip (.querySelector
                                  js/document "#cv-sheet article span[style]")]
                        (if chip
                          (= expected
                             (.-backgroundColor (js/getComputedStyle chip)))
                          false)))
                    rgb-str
                    #js {:timeout 5000}))

(defn- read-sheet-colors
  "Reads computed header and chip backgroundColor values from #cv-sheet."
  [^js page]
  (.evaluate page
             (fn []
               (let [hdr (.querySelector js/document "#cv-sheet header")
                     chip (.querySelector
                           js/document "#cv-sheet article span[style]")]
                 #js {:hdrBg (.-backgroundColor (js/getComputedStyle hdr))
                      :chipBg (.-backgroundColor
                               (js/getComputedStyle chip))}))))

(defn- apply-style-colors!
  "Clicks Zenburn CV palette, sets custom chip-bg, and reads #cv-sheet."
  [^js page]
  (let [^js pal-btn (.locator page "[data-testid='cv-palette-zenburn']")
        ^js chip-in (.locator page "[data-testid='cv-color-chip-bg']")]
    (-> (.waitFor pal-btn #js {:timeout 8000})
        (.then #(.click pal-btn))
        (.then #(wait-for-header-bg! page "rgb(63, 63, 63)"))
        (.then #(.fill chip-in "#123456"))
        (.then #(wait-for-chip-bg! page "rgb(18, 52, 86)"))
        (.then #(read-sheet-colors page)))))

(defn- compile-styled-react-pdf!
  "Clicks Instant PDF (React-PDF) and inspects generated %PDF- blob."
  [^js page]
  (let [^js btn (.locator page "[data-testid='compile-react-pdf-btn']")
        ^js status-el (.locator page "[data-testid='latex-compile-status']")]
    (-> (.click btn)
        (.then #(support/wait-for-pdf-status! page 20000))
        (.then #(.textContent status-el))
        (.then (fn [status-txt]
                 (-> (support/read-pdf-blob-info page)
                     (.then (fn [^js info]
                              {:status-txt status-txt
                               :src (.-src info)
                               :head (.-head info)
                               :bytes (.-bytes info)}))))))))

(defn- run-preview-style-flow!
  "Navigates to #/preview, personalizes colors, and builds React-PDF."
  [^js page url]
  (-> (.goto page (str url "#/preview") #js {:waitUntil "domcontentloaded"})
      (.then #(apply-style-colors! page))
      (.then (fn [^js style-res]
               (-> (compile-styled-react-pdf! page)
                   (.then (fn [pdf-res]
                            (assoc pdf-res
                                   :hdr-bg (.-hdrBg style-res)
                                   :chip-bg (.-chipBg style-res)))))))))

(deftest preview-style-personalization-and-pdf-e2e-test
  (testing "CV style palette & color pickers update #cv-sheet and build PDF"
    (async done
           (-> (support/with-app-page
                 (fn [page url errors]
                   (-> (run-preview-style-flow! page url)
                       (.then (fn [{:keys [hdr-bg chip-bg status-txt
                                           src head bytes]}]
                                (is (= "rgb(63, 63, 63)" hdr-bg))
                                (is (= "rgb(18, 52, 86)" chip-bg))
                                (is (str/includes? status-txt "Compiled PDF"))
                                (is (str/starts-with? (str src) "blob:"))
                                (is (= "%PDF-" head))
                                (is (> bytes 500))
                                (is (empty? @errors)))))))
               (.catch (fn [err] (is (nil? err))))
               (.finally done)))))

(defspec theme-resolve-colors-pbt 30
  (prop/for-all [item (gen/elements theme/theme-catalog)]
                (let [k (:theme/key item)
                      pal (theme/theme-by-id k)
                      vars (theme/resolve-theme-colors pal)]
                  (and (string? (get vars "--bg-page"))
                       (string? (get vars "--md-surface"))
                       (= (get vars "--bg-page")
                          (:portal.colors/background pal))))))

(defspec archive-sort-and-inspect-pbt 30
  (prop/for-all [sname (gen/not-empty gen/string-alphanumeric)
                 sort-k (gen/elements [:kind :label :date :tags-count])
                 sort-d (gen/elements [:asc :desc])]
                (let [f1 {:item/uuid (random-uuid) :skill/name sname
                          :item/tags #{:clojure}}
                      f2 {:item/uuid (random-uuid) :project/name "EasyCV"
                          :project/start-date "2024-01"}
                      sorted (ag/sort-facts [f1 f2] sort-k sort-d)
                      insp (ag/inspect-entity->formats f1)]
                  (and (= 2 (count sorted))
                       (not (str/blank? (:edn insp)))
                       (object? (js/JSON.parse (:json insp)))))))

(defspec cv-style-personalization-pbt 30
  (prop/for-all [pal-k (gen/elements (keys styles/color-palettes))
                 des-k (gen/elements (keys styles/design-presets))
                 lay-k (gen/elements (keys styles/layout-presets))]
                (let [cols (styles/resolve-cv-colors
                            {:palette pal-k
                             :colors {:chip-bg "#123456"}})
                      classes (styles/resolve-cv-classes
                               {:design des-k :layout lay-k})]
                  (and (= "#123456" (:chip-bg cols))
                       (styles/valid-resolved-classes? classes)))))
