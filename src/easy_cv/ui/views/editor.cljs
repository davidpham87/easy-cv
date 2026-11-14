(ns easy-cv.ui.views.editor
  "Code Editor and multi-format I/O view with isolated [:editor] slice."
  (:require [clojure.string :as str]
            [easy-cv.core.db :as db]
            [easy-cv.core.io :as io]
            [easy-cv.core.render :as render]
            [easy-cv.core.solver :as solver]
            [easy-cv.fx.datascript :as fx-ds]
            [easy-cv.ui.components.common :as common]
            [easy-cv.ui.components.editor :as editor-comp]
            [easy-cv.ui.components.layout :as layout]
            [easy-cv.ui.theme :as t]
            [re-frame.core :as rf]))

(def editor-formats
  "Supported editor formats including CSL-JSON and LaTeX preview."
  [{:fmt/key :edn :fmt/label "EDN"}
   {:fmt/key :yaml :fmt/label "YAML"}
   {:fmt/key :json-ld :fmt/label "JSON-LD"}
   {:fmt/key :bibtex :fmt/label "BibTeX"}
   {:fmt/key :csl-json :fmt/label "CSL-JSON"}
   {:fmt/key :latex :fmt/label "LaTeX"}])

(defn- cls
  [& parts]
  (str/join " " (remove str/blank? parts)))

(defn- flatten-errors
  "Turns Malli humanized errors or strings into diagnostic entries."
  [errs]
  (cond
    (nil? errs) []
    (string? errs) [{:diag/severity :error :diag/message errs}]
    (sequential? errs) (vec (mapcat flatten-errors errs))
    (map? errs)
    (vec (mapcat (fn [[k v]]
                   (for [d (flatten-errors v)]
                     (update d :diag/message #(str (pr-str k) ": " %))))
                 errs))
    :else [{:diag/severity :error :diag/message (pr-str errs)}]))

(defn- parse-via-io
  [fmt text]
  (let [opts (if (= fmt :json-ld) {:target :facts} {})
        res (io/parse-input fmt text opts)]
    (if (:ok? res)
      {:editor/ok? true
       :editor/data (:io/data res)
       :editor/diagnostics []}
      {:editor/ok? false
       :editor/errors (:errors res)
       :editor/diagnostics (flatten-errors (:errors res))})))

(defn parse-editor-source
  "Purely parses and validates source text in the selected format."
  [fmt text]
  (cond
    (str/blank? text)
    {:editor/ok? false
     :editor/diagnostics [{:diag/severity :warning
                           :diag/message "Editor buffer is empty"}]}
    (= fmt :latex)
    {:editor/ok? false
     :editor/diagnostics [{:diag/severity :warning
                           :diag/message "LaTeX is export-only"}]}
    :else (parse-via-io fmt text)))

(defn- canonical->tx-items
  [canonical]
  (let [person (:cv/person canonical)
        items (mapcat :section/items (:cv/sections canonical))]
    (vec (keep identity (cons person items)))))

(defn parsed->tx-data
  "Normalizes parsed I/O payload into DataScript transaction vector."
  [payload]
  (cond
    (vector? payload) payload
    (and (map? payload) (contains? payload :cv/sections))
    (canonical->tx-items payload)
    (map? payload) [payload]
    :else []))

(defn serialize-archive-source
  "Serializes current archive facts or solved CV into the target format."
  [dval fmt target cv-id]
  (when dval
    (let [facts (vec (db/all-facts dval))
          canonical (when cv-id
                      (try (solver/solve-cv dval cv-id)
                           (catch :default _ nil)))]
      (case fmt
        (:edn :yaml) (io/export-output
                      fmt (if (= target :cv) (or canonical facts) facts))
        :json-ld (if canonical (io/export-output :json-ld canonical) "{}")
        :bibtex (io/export-output
                 :bibtex (filterv #(= :document (:item/type %)) facts))
        :csl-json (io/export-output
                   :csl-json (filterv #(= :document (:item/type %)) facts))
        :latex (if canonical
                 (:tex (render/render-cv :latex canonical {}))
                 "% No CVConfig available")
        ""))))

(rf/reg-event-db
 ::set-format
 (fn [db [_ fmt]]
   (let [text (get-in db [:editor :text] "")
         res (parse-editor-source fmt text)]
     (update db :editor merge
             {:format fmt
              :diagnostics (:editor/diagnostics res)
              :errors (:editor/errors res)
              :import-msg nil}))))

(rf/reg-event-db
 ::set-text
 (fn [db [_ text]]
   (let [fmt (get-in db [:editor :format] :edn)
         res (parse-editor-source fmt text)]
     (update db :editor merge
             {:text text
              :diagnostics (:editor/diagnostics res)
              :errors (:editor/errors res)
              :import-msg nil}))))

(rf/reg-event-fx
 ::apply-import
 (fn [{:keys [db]} _]
   (let [fmt (get-in db [:editor :format] :edn)
         text (get-in db [:editor :text] "")
         res (parse-editor-source fmt text)]
     (if (:editor/ok? res)
       (let [tx-data (parsed->tx-data (:editor/data res))]
         {:db (assoc-in db [:editor :import-msg]
                        (str "Imported " (count tx-data) " item(s)"))
          :ds/transact tx-data})
       {:db (update db :editor merge
                    {:diagnostics (:editor/diagnostics res)
                     :errors (:editor/errors res)
                     :import-msg "Fix validation errors before importing"})}))))

(rf/reg-sub ::format (fn [db _] (get-in db [:editor :format] :edn)))
(rf/reg-sub ::text (fn [db _] (get-in db [:editor :text] "")))
(rf/reg-sub ::diagnostics (fn [db _] (get-in db [:editor :diagnostics] [])))
(rf/reg-sub ::errors (fn [db _] (get-in db [:editor :errors])))
(rf/reg-sub ::import-msg (fn [db _] (get-in db [:editor :import-msg])))

(rf/reg-sub
 ::active-cv-id
 (fn [db _]
   (get-in db [:solver :active-cv-id])))

(rf/reg-sub
 ::serialized-source
 :<- [::fx-ds/db]
 :<- [::format]
 :<- [::active-cv-id]
 (fn [[dval fmt active-id] [_ target]]
   (let [cv-id (or active-id
                   (when dval (:cv/id (first (db/list-cv-configs dval)))))]
     (serialize-archive-source dval fmt target cv-id))))

(defn- source-load-buttons
  [archive-src cv-src]
  [:div {:class "flex flex-wrap gap-2"}
   [common/button
    {:size :sm
     :on-click #(rf/dispatch [::set-text (or archive-src "")])}
    "Load Archive"]
   [common/button
    {:size :sm
     :on-click #(rf/dispatch [::set-text (or cv-src "")])}
    "Load Active CV"]])

(defn- format-selector-bar
  [active-fmt archive-src cv-src]
  [:div {:class (cls "flex flex-wrap items-center justify-between gap-2"
                     "pb-3 border-b" t/border-subtle)}
   [:div {:class "flex flex-wrap gap-1.5"}
    (for [{:fmt/keys [key label]} editor-formats]
      ^{:key key}
      [common/button
       {:size :xs
        :variant (when (= active-fmt key) :primary)
        :on-click #(rf/dispatch [::set-format key])}
       label])]
   [source-load-buttons archive-src cv-src]])

(defn- malli-error-card
  [errors import-msg]
  [common/card {:class "mt-4"}
   [:div {:class "flex items-center justify-between mb-2"}
    [:h3 {:class "text-sm font-semibold"} "Validation & Import Status"]
    (when import-msg
      [common/badge {} import-msg])]
   (if errors
     [common/pre-block {:content (pr-str errors)}]
     [:p {:class (cls "text-xs" t/text-muted)}
      "No Malli validation errors."])])

(defn- editor-body
  [fmt text diags]
  [:div {:class "mt-4"}
   [editor-comp/editor-panel
    {:editor/format fmt
     :editor/text text
     :editor/diagnostics diags
     :editor/read-only? (= fmt :latex)
     :editor/on-format-change #(rf/dispatch [::set-format %])
     :editor/on-change #(rf/dispatch [::set-text %])
     :editor/on-apply #(rf/dispatch [::apply-import])
     :editor/apply-label "Import to Archive"}]])

(defn view
  "Code Editor and multi-format I/O view."
  []
  (let [fmt @(rf/subscribe [::format])
        text @(rf/subscribe [::text])
        diags @(rf/subscribe [::diagnostics])
        errors @(rf/subscribe [::errors])
        import-msg @(rf/subscribe [::import-msg])
        archive-src @(rf/subscribe [::serialized-source :archive])
        cv-src @(rf/subscribe [::serialized-source :cv])]
    [layout/container {:class "py-6 overflow-auto h-full"}
     [layout/header {}
      [:h2 {:class "text-xl font-bold"} "Code Editor & I/O"]
      [:p {:class (cls "text-sm mt-1" t/text-muted)}
       "Edit EDN, YAML, JSON-LD, BibTeX, or CSL-JSON with live checks."]]
     [format-selector-bar fmt archive-src cv-src]
     [editor-body fmt text diags]
     [malli-error-card errors import-msg]]))
