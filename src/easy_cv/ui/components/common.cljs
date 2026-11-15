(ns easy-cv.ui.components.common
  "Reusable Reagent UI primitives styled with MD3 surfaces and theme vars."
  (:require [clojure.string :as str]
            [easy-cv.ui.theme :as t]
            [re-frame.core :as rf]))

(defn- cls
  [& parts]
  (str/join " " (remove str/blank? parts)))

(def ^:private button-sizes
  {:xs (cls "text-xs px-3 py-1 rounded-full font-medium m3-interactive"
            t/bg-button-xs t/bg-button-xs-hover t/text-button)
   :sm (cls "py-1.5 px-4 text-sm font-medium rounded-full shadow-sm"
            "hover:shadow m3-interactive")
   :md (cls "py-2 px-5 text-sm font-semibold rounded-full shadow-sm"
            "hover:shadow-md m3-interactive")})

(def ^:private button-variants
  {:primary (cls t/bg-button-primary t/bg-button-primary-hover
                 t/text-button-primary)
   :danger (cls t/bg-button-danger t/bg-button-danger-hover "text-white")
   :tonal (cls t/md-secondary-container t/md-on-secondary-container
               t/bg-button-hover)
   :default (cls t/bg-button t/bg-button-hover t/text-button)})

(defn- button-classes
  [variant disabled? size]
  (let [sz (get button-sizes (or size :md) (:md button-sizes))
        v-cls (if disabled?
                (cls t/bg-button-disabled t/text-disabled
                     "cursor-not-allowed opacity-60")
                (if (and (= size :xs) (nil? variant))
                  ""
                  (get button-variants (or variant :default)
                       (:default button-variants))))]
    (cls sz v-cls)))

(defn button
  "Renders a MD3 pill button (:variant :primary/:danger/:tonal, :size)."
  [props & children]
  (let [{:keys [variant disabled class size]} props
        clean (dissoc props :variant :class :size)
        final-cls (cls (button-classes variant disabled size) class)]
    (into [:button (assoc clean :class final-cls)] children)))

(defn button-info
  "Renders a compact informational pill action button."
  [props & children]
  (let [extra (cls "!bg-blue-600 hover:!bg-blue-700 !text-white"
                   (:class props))]
    (into [button (assoc props :size :xs :class extra)] children)))

(defn badge
  "Renders a MD3 tonal status or tag chip."
  [props & children]
  (let [base (cls "inline-flex items-center px-2.5 py-0.5 rounded-full"
                  "text-xs font-medium border m3-tonal-surface"
                  t/text-secondary t/md-outline-variant (:class props))]
    (into [:span (assoc (dissoc props :class) :class base)] children)))

(defn help-button
  "Renders a subtle circular MD3 help icon link."
  [{:keys [href title class]}]
  [:a {:href href
       :target "_blank"
       :rel "noopener noreferrer"
       :class (cls "inline-flex items-center justify-center rounded-full"
                   "w-7 h-7 opacity-70 hover:opacity-100 m3-interactive"
                   t/text-secondary class)
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
  "Renders an MD3 outlined input with optional :on-change-event."
  [{:keys [value on-change on-change-event placeholder class checked]
    input-type :type :as props}]
  (let [base (cls "w-full border rounded-xl px-3 py-1.5 transition-colors"
                  "focus:ring-1" t/bg-input t/text-primary t/border-default
                  t/border-focus t/outline-none t/ring-focus class)
        clean (dissoc props :class :value :on-change :on-change-event
                      :placeholder :type :checked)
        on-chg #(emit-change! on-change on-change-event input-type %)]
    [:input (assoc clean :class base :type (or input-type "text")
                   :value value :checked checked
                   :placeholder placeholder :on-change on-chg)]))

(defn select
  "Renders an MD3 outlined select dropdown."
  [props & children]
  (let [{:keys [on-change on-change-event class value]} props
        base (cls "px-3 py-1.5 rounded-xl border transition-colors"
                  "cursor-pointer" t/bg-input t/text-primary
                  t/border-default t/border-focus t/outline-none class)
        clean (dissoc props :class :value :on-change :on-change-event)]
    (into [:select (assoc clean
                          :class base
                          :value value
                          :on-change #(emit-change! on-change
                                                    on-change-event
                                                    "select" %))]
          children)))

(defn textarea
  "Renders a monospace MD3 outlined textarea."
  [{:keys [value on-change on-change-event placeholder class] :as props}]
  (let [base (cls "w-full border rounded-2xl p-3.5 font-mono text-sm"
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
  "Renders a preformatted scrollable MD3 code surface."
  [{:keys [content class]}]
  [:pre {:class (cls "w-full border rounded-2xl p-4 font-mono text-sm"
                     "overflow-auto m3-tonal-surface" t/text-code
                     t/md-outline-variant class)}
   content])

(defn card
  "Renders a MD3 elevated tonal card container."
  [props & children]
  (let [base (cls "rounded-2xl border p-5 m3-card-elevated m3-enter"
                  "m3-hover-lift" t/text-primary t/md-outline-variant
                  (:class props))]
    (into [:div (assoc (dissoc props :class) :class base)] children)))

(defn table-container
  "Renders a horizontally scrollable MD3 table container."
  [props & children]
  (let [base (cls "overflow-x-auto rounded-2xl border m3-card-elevated"
                  "m3-enter" t/bg-table-body t/md-outline-variant
                  (:class props))]
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
  "Renders a table row with smooth hover highlight."
  [props & children]
  (let [base (cls "transition-colors" t/bg-table-row-hover
                  (:class props))]
    (into [:tr (assoc (dissoc props :class) :class base)] children)))

(defn th
  "Renders a table header cell."
  [props & children]
  (let [base (cls "px-4 py-2.5 text-left text-xs font-semibold uppercase"
                  "tracking-wider" t/text-accent (:class props))]
    (into [:th (assoc (dissoc props :class) :class base)] children)))

(defn td
  "Renders a table data cell."
  [props & children]
  (let [base (cls "px-4 py-2 whitespace-nowrap text-sm"
                  t/text-primary (:class props))]
    (into [:td (assoc (dissoc props :class) :class base)] children)))

(defn page-header
  "Renders an MD3 headline page header with optional right actions."
  [text & children]
  (into [:div {:class (cls "flex items-center justify-between mb-6 pb-3"
                           "border-b" t/md-outline-variant)}
         [:h2 {:class (cls "text-2xl font-bold tracking-tight"
                           t/text-accent)} text]]
        children))

(defn section-header
  "Renders an MD3 section header with optional right actions."
  [text & children]
  (into [:div {:class "flex items-center justify-between mb-4"}
         [:h3 {:class (cls "text-lg font-semibold tracking-tight"
                           t/text-accent)} text]]
        children))

(defn label
  "Renders an MD3 form field label."
  [text]
  [:label {:class (cls "block text-xs font-semibold uppercase tracking-wide"
                       "mb-1.5" t/text-secondary)}
   text])

(defn- tab-item-view
  [tab active-tab-id on-change]
  (let [tid (or (:tab/id tab) (:id tab))
        lbl (or (:tab/label tab) (:label tab))
        on-close (or (:tab/on-close tab) (:on-close tab))
        item-cls (cls "flex items-center px-4 py-2 cursor-pointer"
                      "border-t border-l border-r rounded-t-xl min-w-fit"
                      "m3-interactive"
                      (if (= tid active-tab-id)
                        (cls t/bg-card t/border-default t/text-accent
                             "-mb-px pb-2.5 z-10 shadow-sm")
                        (cls t/bg-input "border-transparent"
                             t/text-secondary "pb-2 opacity-75")))]
    [:div {:class item-cls :on-click #(when on-change (on-change tid))}
     [:span {:class "whitespace-nowrap font-medium text-sm"} lbl]
     (when on-close
       [:button {:class (cls "ml-2 p-0.5 rounded-full hover:bg-white/10"
                             t/text-muted "hover:text-red-400")
                 :on-click (fn [e] (.stopPropagation e) (on-close tid))}
        [:span {:class "text-xs font-bold leading-none"} "x"]])]))

(defn tabs
  "Renders a horizontal MD3 tab bar with optional close and add actions."
  [{:keys [tabs active-tab-id on-change on-add class]}]
  [:div {:class (cls "flex items-center space-x-2 border-b"
                     t/md-outline-variant class)}
   [:div {:class "flex-grow flex items-center space-x-1 overflow-x-auto px-1"}
    (for [tab tabs]
      ^{:key (str (or (:tab/id tab) (:id tab)))}
      [tab-item-view tab active-tab-id on-change])]
   (when on-add
     [:button {:class (cls "px-3 py-1.5 rounded-full font-bold text-sm"
                           "leading-none mb-1 m3-interactive" t/bg-button-xs
                           t/bg-button-xs-hover t/text-button)
               :on-click on-add
               :title "Add New"}
      "+"])])

(defn nav-tabs
  "Renders secondary horizontal MD3 navigation tabs."
  [{:keys [tabs active-tab-id on-change class]}]
  [:div {:class (cls "flex flex-row space-x-2 border-b px-4 py-1.5"
                     "shrink-0" t/md-outline-variant t/bg-toolbar class)}
   (for [tab tabs]
     (let [tid (or (:tab/id tab) (:id tab))
           lbl (or (:tab/label tab) (:label tab))
           active? (= tid active-tab-id)
           btn-cls (cls "px-3.5 py-2 rounded-full text-sm font-medium"
                        "m3-interactive"
                        (if active?
                          (cls t/md-secondary-container
                               t/md-on-secondary-container "shadow-sm")
                          (cls "bg-transparent opacity-80 hover:opacity-100"
                               t/text-secondary)))]
       ^{:key (str tid)}
       [:button {:class btn-cls
                 :on-click #(when on-change (on-change tid))}
        lbl]))])
