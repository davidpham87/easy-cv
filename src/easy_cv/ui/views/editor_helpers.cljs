(ns easy-cv.ui.views.editor-helpers
  "Helper, Schema Spec, and LLM PDF->CV Prompt panels for the Code Editor."
  (:require [clojure.string :as str]
            [easy-cv.core.importer-guide :as guide]
            [easy-cv.ui.components.common :as common]
            [easy-cv.ui.theme :as t]
            [re-frame.core :as rf]))

(def ^:private guide-tabs
  [{:tab/id :guide :tab/label "Input Guide & Examples"}
   {:tab/id :schema :tab/label "Schema Spec (Export)"}
   {:tab/id :prompt :tab/label "LLM PDF->CV Prompt"}])

(defn- cls
  [& parts]
  (str/join " " (remove str/blank? parts)))

(defn- entity-doc-card
  [{:entity/keys [label required optional notes]}]
  [:div {:class (cls "p-3 rounded-xl border transition-colors duration-150"
                     t/bg-input t/border-subtle)}
   [:div {:class "flex items-center justify-between gap-2 mb-1.5"}
    [:span {:class "text-xs font-semibold"} label]
    [:span {:class (cls "text-[11px]" t/text-muted)} notes]]
   [:div {:class "flex flex-wrap gap-1 mb-1"}
    (for [k required]
      ^{:key k}
      [common/badge {:class "font-mono text-[11px]"} (str "* " k)])]
   (when (seq optional)
     [:p {:class (cls "text-[11px] font-mono leading-relaxed" t/text-muted)}
      (str "Optional: " (str/join ", " optional))])])

(defn- guide-tab-header
  [g]
  [:div {:class "flex flex-wrap items-center justify-between gap-2"}
   [:div
    [:h3 {:class "text-sm font-bold"} (:guide/label g)]
    [:p {:class (cls "text-xs mt-0.5" t/text-muted)} (:guide/summary g)]]
   [common/badge {}
    (if (:guide/importable? g) "Import & Export" "Export Only")]])

(defn- guide-tab-actions
  [ex]
  [:div {:class "flex flex-wrap items-center gap-2"}
   [common/button
    {:size :sm
     :variant :primary
     :data-testid "load-example-btn"
     :on-click #(rf/dispatch [:easy-cv.ui.views.editor/load-starter-example])}
    "Load Starter Example"]
   [common/button
    {:size :sm
     :on-click #(rf/dispatch [:easy-cv.ui.views.editor/copy-text ex "example"])}
    "Copy Example"]])

(defn- guide-tab-panel
  [fmt]
  (let [g (guide/format-guide fmt)
        ex (:guide/example-payload g)]
    [:div {:class "space-y-3 transition-opacity duration-200"}
     [guide-tab-header g]
     [guide-tab-actions ex]
     [:div {:class "space-y-2 max-h-64 overflow-y-auto pr-1"}
      (for [ent (:guide/entities g)]
        ^{:key (:entity/type ent)}
        [entity-doc-card ent])]
     [:ul {:class (cls "text-xs list-disc pl-4 space-y-1" t/text-secondary)}
      (for [tip (:guide/tips g)]
        ^{:key tip}
        [:li tip])]
     [common/pre-block {:class "max-h-48 text-xs" :content ex}]]))

(defn- schema-mode-buttons
  [mode]
  [:div {:class "flex gap-1.5"}
   [common/button
    {:size :xs
     :variant (when (= mode :json-schema) :primary)
     :on-click #(rf/dispatch
                 [:easy-cv.ui.views.editor/set-schema-mode :json-schema])}
    "JSON Schema"]
   [common/button
    {:size :xs
     :variant (when (= mode :edn-malli) :primary)
     :on-click #(rf/dispatch
                 [:easy-cv.ui.views.editor/set-schema-mode :edn-malli])}
    "EDN Malli"]])

(defn- schema-action-buttons
  [mode json-str edn-str]
  [:div {:class "flex gap-2"}
   [common/button
    {:size :sm
     :on-click #(rf/dispatch
                 [:easy-cv.ui.views.editor/copy-text
                  (if (= mode :edn-malli) edn-str json-str)
                  "schema"])}
    "Copy"]
   [common/button
    {:size :sm
     :variant :primary
     :on-click #(rf/dispatch
                 [:easy-cv.ui.views.editor/download-file
                  "easy-cv-schema.json" "application/json" json-str])}
    "Download Schema JSON"]])

(defn- schema-tab-toolbar
  [mode json-str edn-str]
  [:div {:class "flex flex-wrap items-center justify-between gap-2"}
   [schema-mode-buttons mode]
   [schema-action-buttons mode json-str edn-str]])

(defn- schema-tab-panel
  []
  (let [mode @(rf/subscribe [:easy-cv.ui.views.editor/schema-mode])
        bundle (guide/exposed-schema-spec)
        edn-str (:schema/edn-spec bundle)
        json-str (guide/export-json-schema-str bundle)
        active-str (if (= mode :edn-malli) edn-str json-str)]
    [:div {:class "space-y-3 transition-opacity duration-200"
           :data-testid "schema-spec-panel"}
     [:p {:class (cls "text-xs" t/text-muted)}
      (str "Version " (:schema/version bundle)
           " Malli & Draft 2020-12 JSON Schema with Schema.org mapping.")]
     [schema-tab-toolbar mode json-str edn-str]
     [common/pre-block {:class "max-h-96 text-xs" :content active-str}]]))

(defn- prompt-format-buttons
  [active-fmt]
  [:div {:class "flex flex-wrap items-center gap-1.5"}
   [:span {:class (cls "text-xs font-medium mr-1" t/text-secondary)}
    "Target:"]
   (for [fmt guide/prompt-target-formats]
     ^{:key fmt}
     [common/button
      {:size :xs
       :variant (when (= active-fmt fmt) :primary)
       :on-click #(rf/dispatch
                   [:easy-cv.ui.views.editor/set-prompt-format fmt])}
      (str/upper-case (name fmt))])])

(defn- prompt-inputs-row
  [role notes]
  [:div {:class "grid grid-cols-1 sm:grid-cols-2 gap-2"}
   [:div
    [common/label "Target Role Focus (optional)"]
    [common/input
     {:value role
      :placeholder "e.g. Staff Clojure Engineer"
      :on-change-event [:easy-cv.ui.views.editor/set-prompt-role]}]]
   [:div
    [common/label "Extra Notes (optional)"]
    [common/input
     {:value notes
      :placeholder "e.g. Emphasize open-source"
      :on-change-event [:easy-cv.ui.views.editor/set-prompt-notes]}]]])

(defn- prompt-action-buttons
  [prompt]
  [:div {:class "flex flex-wrap gap-2"}
   [common/button
    {:size :sm
     :on-click #(rf/dispatch
                 [:easy-cv.ui.views.editor/copy-text prompt "prompt"])}
    "Copy Prompt"]
   [common/button
    {:size :sm
     :variant :primary
     :on-click #(rf/dispatch
                 [:easy-cv.ui.views.editor/download-file
                  "easy-cv-pdf-prompt.md" "text/markdown" prompt])}
    "Download Prompt .md"]])

(defn- prompt-tab-panel
  []
  (let [fmt @(rf/subscribe [:easy-cv.ui.views.editor/prompt-format])
        role @(rf/subscribe [:easy-cv.ui.views.editor/prompt-role])
        notes @(rf/subscribe [:easy-cv.ui.views.editor/prompt-notes])
        prompt @(rf/subscribe [:easy-cv.ui.views.editor/llm-prompt])]
    [:div {:class "space-y-3 transition-opacity duration-200"}
     [prompt-format-buttons fmt]
     [prompt-inputs-row role notes]
     [prompt-action-buttons prompt]
     [:pre {:data-testid "llm-prompt-output"
            :class (cls "w-full border rounded-xl p-3 font-mono text-xs"
                        "overflow-auto max-h-80 whitespace-pre-wrap"
                        t/bg-input t/text-code t/border-subtle)}
      prompt]]))

(defn- helper-tab-bar
  [active-tab copy-msg]
  [:div {:class "flex flex-wrap items-center justify-between gap-2 mb-3"}
   [:div {:class (cls "flex flex-wrap gap-1 p-1 rounded-xl border"
                      t/bg-toolbar t/border-subtle)}
    (for [{:tab/keys [id label]} guide-tabs]
      ^{:key id}
      [common/button
       {:size :xs
        :class "rounded-lg transition-all duration-150"
        :variant (when (= active-tab id) :primary)
        :on-click #(rf/dispatch [:easy-cv.ui.views.editor/set-guide-tab id])}
       label])]
   (when copy-msg
     [common/badge {} copy-msg])])

(defn helper-workspace-panel
  "Renders the 3-tab Helper, Schema Spec, and LLM PDF->CV Prompt panel."
  [fmt]
  (let [active-tab @(rf/subscribe [:easy-cv.ui.views.editor/guide-tab])
        copy-msg @(rf/subscribe [:easy-cv.ui.views.editor/copy-msg])]
    [common/card {:class "rounded-2xl transition-all duration-200"}
     [helper-tab-bar active-tab copy-msg]
     (case active-tab
       :schema [schema-tab-panel]
       :prompt [prompt-tab-panel]
       [guide-tab-panel fmt])]))
