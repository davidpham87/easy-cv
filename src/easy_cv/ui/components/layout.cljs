(ns easy-cv.ui.components.layout
  "Reusable Reagent layout containers and MD3 split-view primitives."
  (:require [clojure.string :as str]
            [easy-cv.ui.theme :as t]
            [re-frame.core :as rf]
            [reagent.core :as r]))

(defn- cls
  [& parts]
  (str/join " " (remove str/blank? parts)))

(defn page-container
  "Renders the full-viewport top-level MD3 surface container."
  [props & children]
  (let [base (cls "h-screen w-full flex overflow-hidden antialiased"
                  t/bg-page t/text-primary (:class props))]
    (into [:div (assoc (dissoc props :class) :class base)] children)))

(defn container
  "Renders a centered horizontal container."
  [props & children]
  (let [base (cls "container mx-auto px-4 md:px-6" (:class props))]
    (into [:div (assoc (dissoc props :class) :class base)] children)))

(defn section
  "Renders a padded vertical section element."
  [props & children]
  (let [base (cls "py-8" (:class props))]
    (into [:section (assoc (dissoc props :class) :class base)] children)))

(defn grid
  "Renders a responsive CSS grid container with staggered MD3 enter."
  [props & children]
  (let [base (cls "grid gap-6 m3-stagger" (:class props))]
    (into [:div (assoc (dissoc props :class) :class base)] children)))

(defn flex-row
  "Renders a vertically centered flex row."
  [props & children]
  (let [base (cls "flex items-center" (:class props))]
    (into [:div (assoc (dissoc props :class) :class base)] children)))

(defn flex-col
  "Renders a vertical flex column."
  [props & children]
  (let [base (cls "flex flex-col" (:class props))]
    (into [:div (assoc (dissoc props :class) :class base)] children)))

(defn header
  "Renders a view header element."
  [props & children]
  (let [base (cls "mb-6 m3-enter" (:class props))]
    (into [:header (assoc (dissoc props :class) :class base)] children)))

(defn main
  "Renders the primary scrollable MD3 content area."
  [props & children]
  (let [base (cls "flex-grow flex flex-col overflow-hidden relative"
                  "m3-panel-enter" (:class props))]
    (into [:main (assoc (dissoc props :class) :class base)] children)))

(def ^:private split-ratios
  {:1-1 ["w-full md:w-1/2" "w-full md:w-1/2"]
   :2-1 ["w-full md:w-2/3" "w-full md:w-1/3"]
   :1-2 ["w-full md:w-1/3" "w-full md:w-2/3"]
   :1-3 ["w-full md:w-1/4" "w-full md:w-3/4"]})

(defn split-view
  "Renders a two-pane MD3 split view (:ratio :1-1/:2-1/:1-2/:1-3)."
  [{:keys [ratio] :or {ratio :1-2}} left right]
  (let [[left-w right-w] (get split-ratios ratio (:1-2 split-ratios))]
    [:div {:class "flex flex-col md:flex-row h-full w-full overflow-hidden"}
     [:div {:class (cls "h-1/2 md:h-full overflow-auto border-b"
                        "md:border-b-0 md:border-r m3-panel-enter"
                        t/md-surface-container t/md-outline-variant left-w)}
      left]
     [:div {:class (cls "h-1/2 md:h-full overflow-auto m3-panel-enter"
                        right-w)}
      right]]))

(defn create-panel
  "Creates a Reagent component that dispatches :init-event on mount."
  [{:keys [display-name init-event render-fn]}]
  (r/create-class
   {:display-name (or display-name "panel")
    :component-did-mount (when init-event #(rf/dispatch init-event))
    :reagent-render render-fn}))
