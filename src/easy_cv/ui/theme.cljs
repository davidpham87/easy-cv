(ns easy-cv.ui.theme
  "Semantic CSS custom-property tokens, presets, and Re-frame theme slice."
  (:require [re-frame.core :as rf]))

(def colors
  "Semantic CSS variable references for app chrome."
  {:bg-page "var(--bg-page)"
   :bg-sidebar "var(--bg-sidebar)"
   :bg-card "var(--bg-card)"
   :bg-input "var(--bg-input)"
   :bg-toolbar "var(--bg-toolbar)"
   :bg-table-head "var(--bg-table-head)"
   :bg-table-body "var(--bg-table-body)"
   :bg-table-row-hover "var(--bg-table-row-hover)"
   :bg-item-hover "var(--bg-item-hover)"
   :bg-button "var(--bg-button)"
   :bg-button-hover "var(--bg-button-hover)"
   :bg-button-primary "var(--bg-button-primary)"
   :bg-button-primary-hover "var(--bg-button-primary-hover)"
   :bg-button-danger "var(--bg-button-danger)"
   :bg-button-danger-hover "var(--bg-button-danger-hover)"
   :bg-button-xs "var(--bg-button-xs)"
   :bg-button-xs-hover "var(--bg-button-xs-hover)"
   :bg-button-disabled "var(--bg-button-disabled)"
   :text-primary "var(--text-primary)"
   :text-secondary "var(--text-secondary)"
   :text-accent "var(--text-accent)"
   :text-code "var(--text-code)"
   :text-muted "var(--text-muted)"
   :text-danger "var(--text-danger)"
   :text-disabled "var(--text-disabled)"
   :text-button "var(--text-button)"
   :text-button-primary "var(--text-button-primary)"
   :border-main "var(--border-main)"
   :border-subtle "var(--border-subtle)"
   :border-default "var(--border-default)"
   :border-hover "var(--border-hover)"
   :border-focus "var(--border-focus)"
   :border-focus-accent "var(--border-focus-accent)"
   :border-accent "var(--border-accent)"
   :ring-focus "var(--ring-focus)"})

;; Static Tailwind v4 utility classes (must remain literal strings for Oxide).
(def bg-page "bg-[var(--bg-page)]")
(def bg-sidebar "bg-[var(--bg-sidebar)]")
(def bg-card "bg-[var(--bg-card)]")
(def bg-input "bg-[var(--bg-input)]")
(def bg-toolbar "bg-[var(--bg-toolbar)]")
(def bg-table-head "bg-[var(--bg-table-head)]")
(def bg-table-body "bg-[var(--bg-table-body)]")
(def bg-table-row-hover "hover:bg-[var(--bg-table-row-hover)]")
(def bg-item-hover "hover:bg-[var(--bg-item-hover)]")

(def bg-button "bg-[var(--bg-button)]")
(def bg-button-hover "hover:bg-[var(--bg-button-hover)]")
(def bg-button-primary "bg-[var(--bg-button-primary)]")
(def bg-button-primary-hover "hover:bg-[var(--bg-button-primary-hover)]")
(def bg-button-danger "bg-[var(--bg-button-danger)]")
(def bg-button-danger-hover "hover:bg-[var(--bg-button-danger-hover)]")
(def bg-button-xs "bg-[var(--bg-button-xs)]")
(def bg-button-xs-hover "hover:bg-[var(--bg-button-xs-hover)]")
(def bg-button-disabled "disabled:bg-[var(--bg-button-disabled)]")

(def text-primary "text-[var(--text-primary)]")
(def text-secondary "text-[var(--text-secondary)]")
(def text-accent "text-[var(--text-accent)]")
(def text-code "text-[var(--text-code)]")
(def text-muted "text-[var(--text-muted)]")
(def text-danger "text-[var(--text-danger)]")
(def text-disabled "disabled:text-[var(--text-disabled)]")
(def text-button "text-[var(--text-button)]")
(def text-button-primary "!text-[var(--text-button-primary)]")

(def border-main "border-[var(--border-main)]")
(def border-subtle "border-[var(--border-subtle)]")
(def border-default "border-[var(--border-default)]")
(def border-hover "hover:border-[var(--border-hover)]")
(def border-focus "focus:border-[var(--border-focus)]")
(def border-focus-accent "focus:border-[var(--border-focus-accent)]")
(def border-accent "border-[var(--border-accent)]")

(def ring-focus "focus:ring-[var(--ring-focus)]")
(def outline-none "focus:outline-none")

(def mapping
  "Maps semantic app color keys to Portal color keys."
  {:bg-page :portal.colors/background
   :bg-sidebar :portal.colors/background2
   :bg-card :portal.colors/background2
   :bg-input :portal.colors/background
   :bg-toolbar :portal.colors/background2
   :bg-table-head :portal.colors/background2
   :bg-table-body :portal.colors/background
   :bg-table-row-hover :portal.colors/background2
   :bg-item-hover :portal.colors/border
   :bg-button :portal.colors/border
   :bg-button-hover :portal.colors/namespace
   :bg-button-primary :portal.colors/boolean
   :bg-button-primary-hover :portal.colors/keyword
   :bg-button-danger :portal.colors/exception
   :bg-button-danger-hover :portal.colors/uri
   :bg-button-xs :portal.colors/border
   :bg-button-xs-hover :portal.colors/namespace
   :bg-button-disabled :portal.colors/background2
   :text-primary :portal.colors/text
   :text-secondary :portal.colors/symbol
   :text-accent :portal.colors/tag
   :text-code :portal.colors/number
   :text-muted :portal.colors/namespace
   :text-danger :portal.colors/exception
   :text-disabled :portal.colors/border
   :text-button :portal.colors/text
   :text-button-primary :portal.colors/background
   :border-main :portal.colors/border
   :border-subtle :portal.colors/border
   :border-default :portal.colors/border
   :border-hover :portal.colors/number
   :border-focus :portal.colors/boolean
   :border-focus-accent :portal.colors/tag
   :border-accent :portal.colors/tag
   :ring-focus :portal.colors/boolean})

(def theme-presets
  "Built-in app chrome palettes (:light, :dark, :paper)."
  {:light
   {:portal.colors/text "#0f172a"
    :portal.colors/background "#f8fafc"
    :portal.colors/background2 "#ffffff"
    :portal.colors/boolean "#2563eb"
    :portal.colors/keyword "#1d4ed8"
    :portal.colors/namespace "#64748b"
    :portal.colors/tag "#1e40af"
    :portal.colors/symbol "#334155"
    :portal.colors/number "#7c3aed"
    :portal.colors/uri "#b91c1c"
    :portal.colors/border "#cbd5e1"
    :portal.colors/exception "#dc2626"}
   :dark
   {:portal.colors/text "#d8dee9"
    :portal.colors/background "#2e3440"
    :portal.colors/background2 "#272c36"
    :portal.colors/boolean "#5e81ac"
    :portal.colors/keyword "#81a1c1"
    :portal.colors/namespace "#8fbcbb"
    :portal.colors/tag "#ebcb8b"
    :portal.colors/symbol "#cbd5e1"
    :portal.colors/number "#b48ead"
    :portal.colors/uri "#d08770"
    :portal.colors/border "#4c566a"
    :portal.colors/exception "#bf616a"}
   :paper
   {:portal.colors/text "#1c1917"
    :portal.colors/background "#fdf6e3"
    :portal.colors/background2 "#f5edd6"
    :portal.colors/boolean "#268bd2"
    :portal.colors/keyword "#1d6fa5"
    :portal.colors/namespace "#57534e"
    :portal.colors/tag "#b58900"
    :portal.colors/symbol "#44403c"
    :portal.colors/number "#a16207"
    :portal.colors/uri "#cb4b16"
    :portal.colors/border "#d6ccb4"
    :portal.colors/exception "#dc322f"}})

(defn theme-by-id
  "Returns the palette map for theme-key, defaulting to :light."
  [theme-key]
  (get theme-presets theme-key (:light theme-presets)))

(defn resolve-theme-colors
  "Maps a palette into {--css-var value} pairs."
  [palette]
  (reduce-kv
   (fn [acc app-key portal-key]
     (if-let [hex (get palette portal-key)]
       (let [var-ref (get colors app-key)
             css-var (subs var-ref 4 (dec (count var-ref)))]
         (assoc acc css-var hex))
       acc))
   {}
   mapping))

(defn- apply-css-var!
  [style-obj css-var hex]
  (let [set-prop (.-setProperty style-obj)]
    (.call set-prop style-obj css-var hex)))

(defn apply-theme-fx!
  "Applies resolved CSS custom properties onto document root and body."
  [{:theme/keys [css-vars]}]
  (when (exists? js/document)
    (let [root-style (.-style (.-documentElement js/document))
          body-style (.-style (.-body js/document))]
      (doseq [[k v] css-vars]
        (apply-css-var! root-style k v))
      (apply-css-var! body-style "background-color" "var(--bg-page)")
      (apply-css-var! body-style "color" "var(--text-primary)"))))

(rf/reg-fx :theme/apply apply-theme-fx!)

(rf/reg-event-fx
 ::set-theme
 (fn [{:keys [db]} [_ theme-key]]
   (let [resolved-key (if (contains? theme-presets theme-key)
                        theme-key
                        :light)
         palette (theme-by-id resolved-key)]
     {:db (assoc-in db [:ui :theme] resolved-key)
      :theme/apply {:theme/id resolved-key
                    :theme/palette palette
                    :theme/css-vars (resolve-theme-colors palette)}})))

(rf/reg-sub
 ::current-theme
 (fn [db _]
   (get-in db [:ui :theme] :light)))
