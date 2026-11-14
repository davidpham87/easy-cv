(ns easy-cv.ui.components.editor
  "Polymorphic code editor component with line numbers and diagnostics."
  (:require [clojure.string :as str]
            [easy-cv.ui.components.common :as common]
            [easy-cv.ui.theme :as t]))

(def editor-formats
  "Supported source formats for CV editing."
  [{:fmt/key :edn :fmt/label "EDN"}
   {:fmt/key :yaml :fmt/label "YAML"}
   {:fmt/key :json-ld :fmt/label "JSON-LD"}
   {:fmt/key :bibtex :fmt/label "BibTeX"}
   {:fmt/key :latex :fmt/label "LaTeX"}])

(defn- cls
  [& parts]
  (str/join " " (remove str/blank? parts)))

(defn- line-count
  [text]
  (max 1 (count (str/split (or text "") #"\n" -1))))

(defn- line-numbers-gutter
  [text]
  [:div {:class (cls "select-none text-right pr-3 py-3 font-mono text-xs"
                     "leading-6 border-r shrink-0"
                     t/bg-sidebar t/text-muted t/border-subtle)}
   (for [n (range 1 (inc (line-count text)))]
     ^{:key n}
     [:div n])])

(defn- diagnostic-badge
  [diag]
  (let [sev (:diag/severity diag)
        line (:diag/line diag)
        msg (:diag/message diag)
        tone (if (= :error sev)
               "bg-red-950/60 text-red-300 border-red-700"
               "bg-amber-950/60 text-amber-200 border-amber-700")]
    [:div {:class (cls "px-2 py-1 rounded border text-xs font-mono" tone)}
     (str (if line (str "L" line " ") "") msg)]))

(defn- diagnostics-bar
  [diagnostics]
  (if (seq diagnostics)
    [:div {:class (cls "flex flex-wrap gap-2 p-2 border-t"
                       t/bg-toolbar t/border-subtle)}
     (for [[idx d] (map-indexed vector diagnostics)]
       ^{:key idx}
       [diagnostic-badge d])]
    [:div {:class (cls "px-3 py-1.5 border-t text-xs font-mono"
                       "text-emerald-400" t/bg-toolbar t/border-subtle)}
     "OK: Valid"]))

(defn- editor-toolbar
  [{:editor/keys [format on-format-change on-apply apply-label]}]
  [:div {:class (cls "flex items-center justify-between px-3 py-2 border-b"
                     t/bg-toolbar t/border-subtle)}
   [common/select
    (assoc {:on-change #(when on-format-change
                          (on-format-change
                           (keyword (.. % -target -value))))}
           :value (name (or format :edn)))
    (for [{:fmt/keys [key label]} editor-formats]
      ^{:key key}
      [:option (assoc {} :value (name key)) label])]
   (when on-apply
     [common/button {:variant :primary :size :sm :on-click on-apply}
      (or apply-label "Apply")])])

(defmulti render-editor
  "Polymorphic editor view dispatching on :editor/engine (default :textarea)."
  (fn [props]
    (or (:editor/engine props) :textarea)))

(defmethod render-editor :default
  [{:editor/keys [text on-change diagnostics read-only?] :as props}]
  [:div {:class (cls "flex flex-col border rounded overflow-hidden"
                     t/bg-input t/border-default)}
   [editor-toolbar props]
   [:div {:class "flex flex-1 min-h-64 overflow-auto"}
    [line-numbers-gutter text]
    [:textarea (assoc {:class (cls "w-full p-3 font-mono text-xs leading-6"
                                   "resize-y focus:outline-none"
                                   t/bg-input t/text-primary)
                       :readOnly (boolean read-only?)
                       :spellCheck false
                       :on-change #(when on-change
                                     (on-change (.. % -target -value)))}
                      :value (or text ""))]]
   [diagnostics-bar diagnostics]])

(defn editor-panel
  "Renders the configured polymorphic CV code editor."
  [props]
  [render-editor props])
