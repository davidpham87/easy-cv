(ns easy-cv.ui.styles
  "Pure Clojure static Tailwind v4 class maps and CV style presets."
  (:require [clojure.string :as str]
            [malli.core :as m]))

(def HexColor
  "Malli schema for a 6-digit hex color string."
  [:and :string [:re #"^#[0-9a-fA-F]{6}$"]])

(def ColorMap
  "Malli schema for personalized CV section and chip colors."
  [:map
   [:header-bg {:optional true} HexColor]
   [:header-fg {:optional true} HexColor]
   [:accent {:optional true} HexColor]
   [:chip-bg {:optional true} HexColor]
   [:chip-fg {:optional true} HexColor]
   [:chip-border {:optional true} HexColor]
   [:sheet-bg {:optional true} HexColor]])

(def StyleConfig
  "Malli schema for CV style preset and color selection."
  [:map
   [:design {:optional true} :keyword]
   [:layout {:optional true} :keyword]
   [:font {:optional true} :keyword]
   [:density {:optional true} :keyword]
   [:chip-style {:optional true} :keyword]
   [:palette {:optional true} :keyword]
   [:colors {:optional true} ColorMap]
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
    :divider "border-gray-300"}
   :material-tonal
   {:sheet
    (str "bg-slate-50 text-slate-900 p-8 rounded-2xl shadow-md "
         "border border-indigo-100")
    :header "bg-indigo-50 text-indigo-950 p-5 rounded-xl mb-5"
    :title "text-3xl font-bold tracking-tight text-indigo-950"
    :subtitle "text-sm font-medium text-indigo-700 mt-1"
    :section-title
    "text-xs font-bold uppercase tracking-wider text-indigo-800 mb-2"
    :item-title "font-semibold text-slate-900"
    :badge
    "bg-indigo-100 text-indigo-900 rounded-lg px-2.5 py-0.5 text-xs font-medium"
    :muted "text-slate-600 text-xs"
    :divider "border-indigo-200"}
   :zenburn-cv
   {:sheet
    (str "bg-[#3f3f3f] text-[#dcdccc] p-8 rounded-lg "
         "border border-[#5f5f5f] shadow-md")
    :header "pb-4 mb-5 border-b border-[#6f6f6f]"
    :title "text-3xl font-bold tracking-tight text-[#f0dfaf]"
    :subtitle "text-sm text-[#8cd0d3] mt-1"
    :section-title
    "text-xs font-bold uppercase tracking-widest text-[#dfaf8f] mb-2"
    :item-title "font-semibold text-[#efdcbc]"
    :badge
    "bg-[#4f4f4f] text-[#cc9393] border border-[#6f6f6f] rounded px-2 py-0.5"
    :muted "text-[#9fc59f] text-xs"
    :divider "border-[#5f5f5f]"}
   :nordic-slate
   {:sheet "bg-slate-50 text-slate-900 p-8 border border-slate-300 shadow-sm"
    :header "pb-4 mb-5 border-b-2 border-slate-700"
    :title "text-3xl font-bold tracking-tight text-slate-900"
    :subtitle "text-sm font-medium text-sky-800 mt-1"
    :section-title
    "text-xs font-bold uppercase tracking-widest text-sky-900 mb-2"
    :item-title "font-semibold text-slate-900"
    :badge
    "bg-sky-100 text-sky-900 border border-sky-200 rounded px-2 py-0.5 text-xs"
    :muted "text-slate-600 text-xs"
    :divider "border-slate-300"}
   :swiss-editorial
   {:sheet "bg-white text-neutral-950 p-8 border-t-8 border-red-600 shadow-sm"
    :header "pb-4 mb-6 border-b-2 border-neutral-950"
    :title "text-4xl font-black tracking-tight text-neutral-950"
    :subtitle "text-sm font-medium text-red-700 mt-1"
    :section-title
    "text-xs font-black uppercase tracking-widest text-red-700 mb-2"
    :item-title "font-bold text-neutral-950"
    :badge "bg-neutral-950 text-white px-2 py-0.5 text-xs font-medium"
    :muted "text-neutral-600 text-xs"
    :divider "border-neutral-300"}})

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
    :side-col "md:col-span-1 border-t md:border-t-0 md:border-l md:pl-5"}
   :header-banner
   {:body "flex flex-col gap-5"
    :main-col "w-full"
    :side-col "w-full"}
   :compact-grid
   {:body "grid grid-cols-1 md:grid-cols-3 gap-4"
    :main-col "md:col-span-2 grid grid-cols-1 gap-3"
    :side-col "md:col-span-1 border-t md:border-t-0 md:border-l md:pl-4"}})

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
    :font-family "'Computer Modern Serif', Georgia, serif"}
   :humanist
   {:font-class "font-sans tracking-normal antialiased"
    :font-family "'Plus Jakarta Sans', 'Gill Sans', system-ui, sans-serif"}
   :mono-tech
   {:font-class "font-mono antialiased"
    :font-family "'JetBrains Mono', ui-monospace, SFMono-Regular, monospace"}})

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

(def chip-presets
  "Badge and skill chip shape/style Tailwind v4 presets."
  {:pill
   (str "inline-flex items-center rounded-full px-2.5 py-0.5 "
        "text-xs font-medium bg-indigo-50 text-indigo-800 "
        "border border-indigo-200")
   :tonal
   (str "inline-flex items-center rounded-lg px-2.5 py-0.5 "
        "text-xs font-medium bg-slate-100 text-slate-800")
   :outlined
   (str "inline-flex items-center rounded-md px-2 py-0.5 "
        "text-xs font-medium bg-transparent text-slate-800 "
        "border border-slate-400")
   :squared
   (str "inline-flex items-center rounded-none px-2 py-0.5 "
        "text-xs font-semibold uppercase tracking-wide "
        "bg-neutral-100 text-neutral-900 border border-neutral-300")
   :zenburn
   (str "inline-flex items-center rounded px-2 py-0.5 "
        "text-xs font-mono bg-[#4f4f4f] text-[#f0dfaf] "
        "border border-[#6f6f6f]")})

(def color-palettes
  "Curated one-click CV color schemes for headers, headings, and chips."
  {:indigo-material
   {:header-bg "#eef2ff" :header-fg "#1e1b4b" :accent "#3730a3"
    :chip-bg "#e0e7ff" :chip-fg "#312e81" :chip-border "#a5b4fc"
    :sheet-bg "#ffffff"}
   :zenburn
   {:header-bg "#3f3f3f" :header-fg "#f0dfaf" :accent "#8c5353"
    :chip-bg "#4f4f4f" :chip-fg "#efdcbc" :chip-border "#6f6f6f"
    :sheet-bg "#faf8f2"}
   :emerald-sage
   {:header-bg "#ecfdf5" :header-fg "#064e3b" :accent "#047857"
    :chip-bg "#d1fae5" :chip-fg "#065f46" :chip-border "#6ee7b7"
    :sheet-bg "#ffffff"}
   :nord-frost
   {:header-bg "#2e3440" :header-fg "#eceff4" :accent "#5e81ac"
    :chip-bg "#e5e9f0" :chip-fg "#2e3440" :chip-border "#88c0d0"
    :sheet-bg "#f8fafc"}
   :terracotta
   {:header-bg "#fff7ed" :header-fg "#7c2d12" :accent "#c2410c"
    :chip-bg "#ffedd5" :chip-fg "#9a3412" :chip-border "#fdba74"
    :sheet-bg "#fffbf7"}
   :monochrome
   {:header-bg "#18181b" :header-fg "#fafafa" :accent "#27272a"
    :chip-bg "#f4f4f5" :chip-fg "#18181b" :chip-border "#d4d4d8"
    :sheet-bg "#ffffff"}})

(def design->palette
  "Default curated color palette associated with each design preset."
  {:academic :monochrome
   :minimal :monochrome
   :modern :indigo-material
   :classic :monochrome
   :material-tonal :indigo-material
   :zenburn-cv :zenburn
   :nordic-slate :nord-frost
   :swiss-editorial :terracotta})

(def style-bundles
  "Curated one-click CV style combinations."
  {:student-tech {:design :modern :layout :single-col
                  :font :sans :density :balanced :chip-style :pill}
   :academic-cv {:design :academic :layout :single-col
                 :font :academic :density :compact :chip-style :outlined}
   :exec-brief {:design :classic :layout :sidebar
                :font :serif :density :balanced :chip-style :squared}
   :minimal-cv {:design :minimal :layout :two-col
                :font :humanist :density :airy :chip-style :tonal}
   :material-pro {:design :material-tonal :layout :header-banner
                  :font :humanist :density :balanced :chip-style :tonal}
   :zenburn-dev {:design :zenburn-cv :layout :compact-grid
                 :font :mono-tech :density :compact :chip-style :zenburn}})

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

(defn resolve-cv-colors
  "Resolves palette, design fallback, and custom color overrides."
  [{:keys [palette design colors]}]
  (let [pal-k (or palette (get design->palette design :indigo-material))
        base (get color-palettes pal-k (:indigo-material color-palettes))
        valid-colors (into {} (filter #(string? (val %))) colors)]
    (merge base valid-colors)))

(defn resolve-cv-classes
  "Resolves design, layout, font, density, chip-style, and overrides."
  [{:keys [design layout font density chip-style class-overrides]
    :or {design :academic layout :single-col font :sans density :balanced}}]
  (let [d (get design-presets design (:academic design-presets))
        l (get layout-presets layout (:single-col layout-presets))
        f (get font-presets font (:sans font-presets))
        sp (get density-presets density (:balanced density-presets))
        badge-cls (or (get chip-presets chip-style) (:badge d))]
    (-> (merge (select-keys d [:header :title :subtitle :section-title
                               :item-title :muted :divider])
               (select-keys l [:body :main-col :side-col])
               (select-keys sp [:section :item])
               {:badge badge-cls
                :sheet (join-classes (:sheet d) (:font-class f))})
        (merge-overrides class-overrides))))

(defn valid-resolved-classes?
  "Validates resolved class map against ResolvedClasses."
  [m]
  (m/validate ResolvedClasses m))
