(ns easy-cv.ui.styles
  "Pure Clojure static Tailwind v4 class maps and CV style presets."
  (:require [clojure.string :as str]
            [malli.core :as m]))

(def StyleConfig
  "Malli schema for CV style preset selection."
  [:map
   [:design {:optional true} :keyword]
   [:layout {:optional true} :keyword]
   [:font {:optional true} :keyword]
   [:density {:optional true} :keyword]
   [:class-overrides {:optional true} [:map-of :keyword :string]]])

(def ResolvedClasses
  "Malli schema for resolved CV Tailwind v4 class map."
  [:map
   [:sheet :string]
   [:header :string]
   [:title :string]
   [:subtitle :string]
   [:body :string]
   [:main-col :string]
   [:side-col :string]
   [:section :string]
   [:section-title :string]
   [:item :string]
   [:item-title :string]
   [:badge :string]
   [:muted :string]
   [:divider :string]])

;; All Tailwind v4 utilities below MUST remain static string literals.
(def design-presets
  "Visual design presets for rendered CV sheets."
  {:academic
   {:sheet "bg-white text-stone-900 p-8 shadow-sm border border-stone-200"
    :header "pb-4 mb-5 border-b-2 border-stone-800"
    :title "text-3xl font-bold tracking-tight text-stone-950"
    :subtitle "text-sm text-stone-600 mt-1"
    :section-title
    "text-xs font-bold uppercase tracking-widest text-stone-800 mb-2"
    :item-title "font-semibold text-stone-900"
    :badge "bg-stone-100 text-stone-800 border border-stone-300 px-2 py-0.5"
    :muted "text-stone-600 text-xs"
    :divider "border-stone-300"}
   :minimal
   {:sheet "bg-white text-neutral-900 p-8 border border-neutral-200"
    :header "pb-4 mb-6 border-b border-neutral-200"
    :title "text-2xl font-semibold tracking-tight text-neutral-950"
    :subtitle "text-sm text-neutral-500 mt-1"
    :section-title
    "text-xs font-medium uppercase tracking-wider text-neutral-500 mb-2"
    :item-title "font-medium text-neutral-900"
    :badge "bg-neutral-100 text-neutral-700 rounded px-2 py-0.5"
    :muted "text-neutral-500 text-xs"
    :divider "border-neutral-200"}
   :modern
   {:sheet "bg-white text-slate-900 p-8 shadow-md border-t-4 border-indigo-600"
    :header "pb-4 mb-5 border-b border-slate-200"
    :title "text-3xl font-extrabold tracking-tight text-slate-950"
    :subtitle "text-sm font-medium text-indigo-700 mt-1"
    :section-title
    "text-xs font-bold uppercase tracking-wider text-indigo-800 mb-2"
    :item-title "font-semibold text-slate-900"
    :badge "bg-indigo-50 text-indigo-800 rounded-full px-2.5 py-0.5"
    :muted "text-slate-500 text-xs"
    :divider "border-indigo-200"}
   :classic
   {:sheet "bg-white text-gray-900 p-8 border border-gray-300"
    :header "text-center pb-4 mb-5 border-b border-gray-400"
    :title "text-3xl font-bold text-gray-950"
    :subtitle "text-sm text-gray-700 mt-1"
    :section-title
    "text-sm font-bold uppercase tracking-wide text-gray-900 border-b mb-2"
    :item-title "font-bold text-gray-900"
    :badge "bg-gray-100 text-gray-800 border border-gray-300 px-2 py-0.5"
    :muted "text-gray-600 text-xs"
    :divider "border-gray-300"}})

(def layout-presets
  "Column and grid layout presets for CV sheets."
  {:single-col
   {:body "flex flex-col gap-4"
    :main-col "w-full"
    :side-col "w-full"}
   :two-col
   {:body "grid grid-cols-1 md:grid-cols-2 gap-6"
    :main-col "col-span-1"
    :side-col "col-span-1"}
   :sidebar
   {:body "grid grid-cols-1 md:grid-cols-3 gap-6"
    :main-col "md:col-span-2"
    :side-col "md:col-span-1 border-t md:border-t-0 md:border-l md:pl-5"}})

(def font-presets
  "Typography presets pairing Tailwind font classes with CSS stacks."
  {:sans
   {:font-class "font-sans antialiased"
    :font-family "Inter, system-ui, -apple-system, sans-serif"}
   :serif
   {:font-class "font-serif antialiased"
    :font-family "Merriweather, Georgia, Cambria, serif"}
   :academic
   {:font-class "font-serif tracking-normal"
    :font-family "'Computer Modern Serif', Georgia, serif"}})

(def density-presets
  "Vertical spacing presets for section and item density."
  {:compact
   {:section "mb-2"
    :item "py-1 text-xs leading-snug"}
   :balanced
   {:section "mb-4"
    :item "py-1.5 text-sm leading-normal"}
   :airy
   {:section "mb-6"
    :item "py-2 text-base leading-relaxed"}})

(def style-bundles
  "Curated one-click CV style combinations."
  {:student-tech {:design :modern :layout :single-col
                  :font :sans :density :balanced}
   :academic-cv {:design :academic :layout :single-col
                 :font :academic :density :compact}
   :exec-brief {:design :classic :layout :sidebar
                :font :serif :density :balanced}
   :minimal-cv {:design :minimal :layout :two-col
                :font :sans :density :airy}})

(defn- join-classes
  [a b]
  (str/trim (str/join " " (remove str/blank? [a b]))))

(defn- merge-overrides
  [base class-overrides]
  (reduce-kv (fn [acc slot extra]
               (if (contains? acc slot)
                 (update acc slot join-classes extra)
                 acc))
             base
             (or class-overrides {})))

(defn resolve-cv-classes
  "Resolves design, layout, font, density, and overrides into class map."
  [{:keys [design layout font density class-overrides]
    :or {design :academic layout :single-col font :sans density :balanced}}]
  (let [d (get design-presets design (:academic design-presets))
        l (get layout-presets layout (:single-col layout-presets))
        f (get font-presets font (:sans font-presets))
        sp (get density-presets density (:balanced density-presets))]
    (-> (merge (select-keys d [:header :title :subtitle :section-title
                               :item-title :badge :muted :divider])
               (select-keys l [:body :main-col :side-col])
               (select-keys sp [:section :item])
               {:sheet (join-classes (:sheet d) (:font-class f))})
        (merge-overrides class-overrides))))

(defn valid-resolved-classes?
  "Validates resolved class map against ResolvedClasses."
  [m]
  (m/validate ResolvedClasses m))
