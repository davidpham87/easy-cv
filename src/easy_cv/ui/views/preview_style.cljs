(ns easy-cv.ui.views.preview-style
  "Style and personalized color customizer subcomponents for preview view."
  (:require [clojure.string :as str]
            [easy-cv.ui.components.common :as common]
            [easy-cv.ui.styles :as styles]
            [easy-cv.ui.theme :as t]
            [re-frame.core :as rf]))

(def ^:private color-fields
  [{:color/key :header-bg :color/label "Header Background"
    :color/testid "cv-color-header-bg"}
   {:color/key :header-fg :color/label "Header Text"
    :color/testid "cv-color-header-fg"}
   {:color/key :accent :color/label "Accent / Headings"
    :color/testid "cv-color-accent"}
   {:color/key :chip-bg :color/label "Chip / Badge Background"
    :color/testid "cv-color-chip-bg"}
   {:color/key :chip-fg :color/label "Chip / Badge Text"
    :color/testid "cv-color-chip-fg"}])

(defn- cls
  [& parts]
  (str/join " " (remove str/blank? parts)))

(defn- style-select
  [label opt-key current-val options]
  [:div {:class "flex flex-col gap-1"}
   [:label {:class (cls "text-xs font-semibold uppercase" t/text-muted)} label]
   [common/select
    (assoc {:on-change #(rf/dispatch [:easy-cv.ui.views.preview/set-style-opt
                                      opt-key
                                      (keyword (.. % -target -value))])}
           (keyword "value") (name (or current-val (first options))))
    (for [k options]
      ^{:key k}
      [:option (assoc {} (keyword "value") (name k)) (name k)])]])

(defn- swatch-button
  [pk pal active?]
  [:button
   (assoc {:data-testid (str "cv-palette-" (name pk))
           :on-click #(rf/dispatch [:easy-cv.ui.views.preview/apply-palette pk])
           :class (cls "inline-flex items-center gap-1.5 px-2 py-1 rounded-lg"
                       "text-xs border transition-all cursor-pointer"
                       (if active?
                         (cls "border-indigo-500 ring-1"
                              "ring-indigo-500 font-semibold")
                         t/border-subtle))}
          (keyword "type") "button")
   [:span {:class "w-2.5 h-2.5 rounded-full border"
           :style {:backgroundColor (:header-bg pal)}}]
   [:span {:class "w-2.5 h-2.5 rounded-full border"
           :style {:backgroundColor (:accent pal)}}]
   [:span {:class "w-2.5 h-2.5 rounded-full border"
           :style {:backgroundColor (:chip-bg pal)}}]
   [:span (name pk)]])

(defn- color-swatch-bar
  [active-palette]
  [:div {:class "space-y-1.5"}
   [:span {:class (cls "text-xs font-semibold uppercase block" t/text-muted)}
    "Color Palettes"]
   [:div {:class "flex flex-wrap gap-1.5"}
    (for [[pk pal] styles/color-palettes]
      ^{:key pk} [swatch-button pk pal (= pk active-palette)])]])

(defn- color-input
  [testid key hex]
  [:input (assoc {:data-testid testid
                  :on-change #(rf/dispatch
                               [:easy-cv.ui.views.preview/set-color-opt
                                key
                                (.. % -target -value)])
                  :class (cls "w-7 h-6 rounded border cursor-pointer"
                              "bg-transparent")}
                 (keyword "type") "color"
                 (keyword "value") hex)])

(defn- color-picker-row
  [resolved-colors {:color/keys [key label testid]}]
  (let [hex (get resolved-colors key "#1e293b")]
    [:div {:class "flex items-center justify-between gap-2"}
     [:label {:class (cls "text-xs" t/text-secondary)} label]
     [:div {:class "flex items-center gap-1.5"}
      [:span {:class (cls "text-[11px] font-mono px-1.5 py-0.5 rounded border"
                          t/border-subtle t/text-muted)}
       hex]
      [color-input testid key hex]]]))

(defn- personalized-colors-panel
  [style-cfg]
  (let [resolved (styles/resolve-cv-colors style-cfg)
        custom? (boolean (seq (:colors style-cfg)))]
    [:div {:class "space-y-2 pt-2 border-t border-slate-200/30"}
     [color-swatch-bar (:palette style-cfg)]
     [:div {:class "flex items-center justify-between pt-1"}
      [:span {:class (cls "text-xs font-semibold uppercase" t/text-muted)}
       "Custom Colors"]
      (when custom?
        [common/button
         {:size :xs :data-testid "cv-color-reset"
          :on-click #(rf/dispatch [:easy-cv.ui.views.preview/reset-colors])}
         "Reset"])]
     [:div {:class "space-y-1.5"}
      (for [cf color-fields]
        ^{:key (:color/key cf)}
        [color-picker-row resolved cf])]]))

(defn- bundle-buttons-bar
  []
  [:div {:class "flex flex-wrap gap-1.5"}
   (for [bk (keys styles/style-bundles)]
     ^{:key bk}
     [common/button
      {:size :xs
       :on-click #(rf/dispatch [:easy-cv.ui.views.preview/apply-bundle bk])}
      (name bk)])])

(defn style-customizer-panel
  "Renders design, layout, font, density, chip, and color controls."
  [style-cfg]
  [common/card {:class "p-4 space-y-3"}
   [:h3 {:class "text-sm font-bold"} "Style Customizer"]
   [bundle-buttons-bar]
   [style-select "Design" :design (:design style-cfg)
    (keys styles/design-presets)]
   [style-select "Layout" :layout (:layout style-cfg)
    (keys styles/layout-presets)]
   [style-select "Font" :font (:font style-cfg) (keys styles/font-presets)]
   [style-select "Density" :density (:density style-cfg)
    (keys styles/density-presets)]
   [style-select "Chip Style" :chip-style (:chip-style style-cfg)
    (keys styles/chip-presets)]
   [personalized-colors-panel style-cfg]])
