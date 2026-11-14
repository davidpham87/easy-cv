(ns easy-cv.ui.components.common
  "Reusable Reagent UI primitives styled with theme CSS variables."
  (:require [clojure.string :as str]
            [easy-cv.ui.theme :as t]
            [re-frame.core :as rf]))

(defn- cls
  [& parts]
  (str/join " " (remove str/blank? parts)))

(def ^:private button-sizes
  {:xs (cls "text-xs px-2 py-1 rounded transition-colors"
            t/bg-button-xs t/bg-button-xs-hover t/text-button)
   :sm "py-1 px-3 text-sm font-medium rounded shadow-sm transition-all"
   :md "py-2 px-4 text-sm font-bold rounded shadow-sm transition-all"})

(def ^:private button-variants
  {:primary (cls t/bg-button-primary t/bg-button-primary-hover
                 t/text-button-primary)
   :danger (cls t/bg-button-danger t/bg-button-danger-hover "text-white")
   :default (cls t/bg-button t/bg-button-hover t/text-button)})

(defn- button-classes
  [variant disabled? size]
  (let [sz (get button-sizes (or size :md) (:md button-sizes))
        v-cls (if disabled?
                (cls t/bg-button-disabled t/text-disabled "cursor-not-allowed")
                (if (and (= size :xs) (nil? variant))
                  ""
                  (get button-variants (or variant :default)
                       (:default button-variants))))]
    (cls sz v-cls)))

(defn button
  "Renders a styled button (:variant :primary/:danger, :size :xs/:sm/:md)."
  [props & children]
  (let [{:keys [variant disabled class size]} props
        clean (dissoc props :variant :class :size)
        final-cls (cls (button-classes variant disabled size) class)]
    (into [:button (assoc clean :class final-cls)] children)))

(defn button-info
  "Renders a compact informational action button."
  [props & children]
  (let [extra (cls "!bg-blue-600 hover:!bg-blue-700 !text-white"
                   (:class props))]
    (into [button (assoc props :size :xs :class extra)] children)))

(defn badge
  "Renders a compact status or tag badge."
  [props & children]
  (let [base (cls "inline-flex items-center px-2 py-0.5 rounded text-xs"
                  "font-medium border" t/bg-card t/text-secondary
                  t/border-subtle (:class props))]
    (into [:span (assoc (dissoc props :class) :class base)] children)))

(defn help-button
  "Renders a subtle help icon link."
  [{:keys [href title class]}]
  [:a {:href href
       :target "_blank"
       :rel "noopener noreferrer"
       :class (cls "inline-flex items-center justify-center rounded p-1"
                   "opacity-60 hover:opacity-100" t/text-secondary class)
       :title (or title "Help")}
   [:span {:class "text-xs font-bold"} "?"]])

(defn- emit-change!
  [on-change on-change-event input-type e]
  (when on-change (on-change e))
  (when on-change-event
    (let [target (.-target e)
          v (if (= input-type "checkbox")
              (.-checked target)
              (.-value target))]
      (rf/dispatch (conj on-change-event v)))))

(defn input
  "Renders a styled input element with optional :on-change-event."
  [{:keys [value on-change on-change-event placeholder class checked]
    input-type :type :as props}]
  (let [base (cls "w-full border rounded px-2 py-1 transition-colors"
                  t/bg-input t/text-primary t/border-default
                  t/border-focus t/outline-none t/ring-focus class)
        clean (dissoc props :class :value :on-change :on-change-event
                      :placeholder :type :checked)
        on-chg #(emit-change! on-change on-change-event input-type %)]
    [:input (assoc clean :class base :type (or input-type "text")
                   :value value :checked checked
                   :placeholder placeholder :on-change on-chg)]))

(defn select
  "Renders a styled select dropdown."
  [props & children]
  (let [{:keys [on-change on-change-event class value]} props
        base (cls "p-2 rounded border" t/bg-input t/text-primary
                  t/border-default class)
        clean (dissoc props :class :value :on-change :on-change-event)]
    (into [:select (assoc clean
                          :class base
                          :value value
                          :on-change #(emit-change! on-change
                                                    on-change-event
                                                    "select" %))]
          children)))

(defn textarea
  "Renders a monospace styled textarea."
  [{:keys [value on-change on-change-event placeholder class] :as props}]
  (let [base (cls "w-full border rounded p-3 font-mono text-sm"
                  "transition-colors" t/bg-input t/text-primary
                  t/border-default t/border-focus t/outline-none
                  "focus:ring-1" t/ring-focus class)
        clean (dissoc props :class :value :placeholder
                      :on-change :on-change-event)]
    [:textarea (assoc clean
                      :class base
                      :value value
                      :placeholder placeholder
                      :on-change #(emit-change! on-change
                                                on-change-event
                                                "textarea" %))]))

(defn pre-block
  "Renders a preformatted scrollable code block."
  [{:keys [content class]}]
  [:pre {:class (cls "w-full border rounded p-4 font-mono text-sm"
                     "overflow-auto" t/bg-input t/text-code
                     t/border-subtle class)}
   content])

(defn card
  "Renders a themed card container."
  [props & children]
  (let [base (cls "rounded shadow-sm border p-4" t/bg-card
                  t/text-primary t/border-subtle (:class props))]
    (into [:div (assoc (dissoc props :class) :class base)] children)))

(defn table-container
  "Renders a horizontally scrollable table wrapper."
  [props & children]
  (let [base (cls "overflow-x-auto rounded shadow-sm border"
                  t/bg-table-body t/border-subtle (:class props))]
    (into [:div (assoc (dissoc props :class) :class base)] children)))

(defn table
  "Renders a styled table."
  [props & children]
  (let [base (cls "min-w-full divide-y" t/border-subtle (:class props))]
    (into [:table (assoc (dissoc props :class) :class base)] children)))

(defn thead
  "Renders a table header."
  [props & children]
  (let [base (cls t/bg-table-head (:class props))]
    (into [:thead (assoc (dissoc props :class) :class base)] children)))

(defn tbody
  "Renders a table body."
  [props & children]
  (let [base (cls "divide-y" t/bg-table-body t/border-subtle
                  (:class props))]
    (into [:tbody (assoc (dissoc props :class) :class base)] children)))

(defn tr
  "Renders a table row."
  [props & children]
  (let [base (cls "transition-colors" t/bg-table-row-hover
                  (:class props))]
    (into [:tr (assoc (dissoc props :class) :class base)] children)))

(defn th
  "Renders a table header cell."
  [props & children]
  (let [base (cls "px-3 py-2 text-left text-xs font-medium uppercase"
                  "tracking-wider" t/text-accent (:class props))]
    (into [:th (assoc (dissoc props :class) :class base)] children)))

(defn td
  "Renders a table data cell."
  [props & children]
  (let [base (cls "px-3 py-1.5 whitespace-nowrap text-sm"
                  t/text-primary (:class props))]
    (into [:td (assoc (dissoc props :class) :class base)] children)))

(defn page-header
  "Renders a top-of-view page header with optional right actions."
  [text & children]
  (into [:div {:class (cls "flex items-center justify-between mb-6 pb-2"
                           "border-b" t/border-main)}
         [:h2 {:class (cls "text-2xl font-bold" t/text-accent)} text]]
        children))

(defn section-header
  "Renders a section header with optional right actions."
  [text & children]
  (into [:div {:class "flex items-center justify-between mb-4"}
         [:h3 {:class (cls "text-xl font-semibold" t/text-accent)} text]]
        children))

(defn label
  "Renders a form field label."
  [text]
  [:label {:class (cls "block text-sm font-medium mb-1.5"
                       t/text-secondary)}
   text])

(defn- tab-item-view
  [tab active-tab-id on-change]
  (let [tid (or (:tab/id tab) (:id tab))
        lbl (or (:tab/label tab) (:label tab))
        on-close (or (:tab/on-close tab) (:on-close tab))
        item-cls (cls "flex items-center px-4 py-2 cursor-pointer"
                      "border-t border-l border-r rounded-t-md min-w-fit"
                      (if (= tid active-tab-id)
                        (cls t/bg-card t/border-default t/text-accent
                             "-mb-px pb-2.5 z-10")
                        (cls t/bg-input "border-transparent"
                             t/text-secondary "pb-2 opacity-75")))]
    [:div {:class item-cls :on-click #(when on-change (on-change tid))}
     [:span {:class "whitespace-nowrap font-medium text-sm"} lbl]
     (when on-close
       [:button {:class (cls "ml-2 p-0.5 rounded hover:bg-white/10"
                             t/text-muted "hover:text-red-400")
                 :on-click (fn [e] (.stopPropagation e) (on-close tid))}
        [:span {:class "text-xs font-bold leading-none"} "x"]])]))

(defn tabs
  "Renders a horizontal tab bar with optional close and add actions."
  [{:keys [tabs active-tab-id on-change on-add class]}]
  [:div {:class (cls "flex items-center space-x-2 border-b"
                     t/border-default class)}
   [:div {:class "flex-grow flex items-center space-x-1 overflow-x-auto px-1"}
    (for [tab tabs]
      ^{:key (str (or (:tab/id tab) (:id tab)))}
      [tab-item-view tab active-tab-id on-change])]
   (when on-add
     [:button {:class (cls "px-3 py-2 rounded-t-md font-bold text-lg"
                           "leading-none mb-0.5" t/bg-button-xs
                           t/bg-button-xs-hover t/text-button)
               :on-click on-add
               :title "Add New"}
      "+"])])

(defn nav-tabs
  "Renders secondary horizontal navigation tabs."
  [{:keys [tabs active-tab-id on-change class]}]
  [:div {:class (cls "flex flex-row space-x-6 border-b px-4 shrink-0"
                     t/border-default t/bg-toolbar class)}
   (for [tab tabs]
     (let [tid (or (:tab/id tab) (:id tab))
           lbl (or (:tab/label tab) (:label tab))
           active? (= tid active-tab-id)
           btn-cls (cls "py-3 font-medium transition-colors border-b-2"
                        (if active?
                          (cls t/border-accent t/text-accent)
                          (cls "border-transparent" t/text-secondary)))]
       ^{:key (str tid)}
       [:button {:class btn-cls
                 :on-click #(when on-change (on-change tid))}
        lbl]))])
