(ns easy-cv.ui.theme
  "Semantic CSS custom-property tokens, MD3 vars, presets, and Re-frame slice."
  (:require [re-frame.core :as rf]))

(def colors
  "Semantic CSS variable references for app chrome and MD3 surfaces."
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
   :ring-focus "var(--ring-focus)"
   :md-surface "var(--md-surface)"
   :md-surface-container "var(--md-surface-container)"
   :md-surface-container-high "var(--md-surface-container-high)"
   :md-primary "var(--md-primary)"
   :md-on-primary "var(--md-on-primary)"
   :md-secondary-container "var(--md-secondary-container)"
   :md-on-secondary-container "var(--md-on-secondary-container)"
   :md-outline "var(--md-outline)"
   :md-outline-variant "var(--md-outline-variant)"})

;; Static Tailwind v4 utility classes (literal strings for Oxide scanner).
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

(def md-surface "bg-[var(--md-surface)]")
(def md-surface-container "bg-[var(--md-surface-container)]")
(def md-surface-container-high "bg-[var(--md-surface-container-high)]")
(def md-secondary-container "bg-[var(--md-secondary-container)]")
(def md-on-secondary-container "text-[var(--md-on-secondary-container)]")
(def md-outline-variant "border-[var(--md-outline-variant)]")

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
  "Built-in app chrome palettes (12 themes, preserving :dark #2e3440)."
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
    :portal.colors/exception "#dc322f"}
   :zenburn
   {:portal.colors/text "#dcdccc"
    :portal.colors/background "#383838"
    :portal.colors/background2 "#303030"
    :portal.colors/boolean "#bfebbf"
    :portal.colors/string "#cc9393"
    :portal.colors/keyword "#afd8af"
    :portal.colors/namespace "#5c888b"
    :portal.colors/tag "#f0dfaf"
    :portal.colors/symbol "#dcdccc"
    :portal.colors/number "#8cd0d3"
    :portal.colors/uri "#dfaf8f"
    :portal.colors/border "#4f4f4f"
    :portal.colors/package "#8cd0d3"
    :portal.colors/exception "#cc9393"
    :portal.colors/diff-add "#9fc59f"
    :portal.colors/diff-remove "#cc9393"}
   :nord
   {:portal.colors/text "#d8dee9"
    :portal.colors/background "#2e3440"
    :portal.colors/background2 "#272c36"
    :portal.colors/boolean "#5e81ac"
    :portal.colors/string "#a3be8c"
    :portal.colors/keyword "#5e81ac"
    :portal.colors/namespace "#8fbcbb"
    :portal.colors/tag "#ebcb8b"
    :portal.colors/symbol "#d8dee9"
    :portal.colors/number "#b48ead"
    :portal.colors/uri "#d08770"
    :portal.colors/border "#4c566a"
    :portal.colors/package "#88c0d0"
    :portal.colors/exception "#bf616a"
    :portal.colors/diff-add "#a3be8c"
    :portal.colors/diff-remove "#bf616a"}
   :nord-light
   {:portal.colors/text "#4c566a"
    :portal.colors/background "#ffffff"
    :portal.colors/background2 "#eceff4"
    :portal.colors/boolean "#5e81ac"
    :portal.colors/string "#a3be8c"
    :portal.colors/keyword "#5e81ac"
    :portal.colors/namespace "#8fbcbb"
    :portal.colors/tag "#ebcb8b"
    :portal.colors/symbol "#4c566a"
    :portal.colors/number "#b48ead"
    :portal.colors/uri "#d08770"
    :portal.colors/border "#d8dee9"
    :portal.colors/package "#88c0d0"
    :portal.colors/exception "#bf616a"
    :portal.colors/diff-add "#a3be8c"
    :portal.colors/diff-remove "#bf616a"}
   :solarized-dark
   {:portal.colors/text "#93a1a1"
    :portal.colors/background "#073642"
    :portal.colors/background2 "#002b36"
    :portal.colors/boolean "#268bd2"
    :portal.colors/string "#859900"
    :portal.colors/keyword "#268bd2"
    :portal.colors/namespace "#2aa198"
    :portal.colors/tag "#b58900"
    :portal.colors/symbol "#93a1a1"
    :portal.colors/number "#d33682"
    :portal.colors/uri "#cb4b16"
    :portal.colors/border "#586e75"
    :portal.colors/package "#2aa198"
    :portal.colors/exception "#dc322f"
    :portal.colors/diff-add "#859900"
    :portal.colors/diff-remove "#dc322f"}
   :solarized-light
   {:portal.colors/text "#93a1a1"
    :portal.colors/background "#fdf6e3"
    :portal.colors/background2 "#eee8d5"
    :portal.colors/boolean "#268bd2"
    :portal.colors/string "#859900"
    :portal.colors/keyword "#268bd2"
    :portal.colors/namespace "#2aa198"
    :portal.colors/tag "#b58900"
    :portal.colors/symbol "#93a1a1"
    :portal.colors/number "#d33682"
    :portal.colors/uri "#cb4b16"
    :portal.colors/border "#d2cdbb"
    :portal.colors/package "#2aa198"
    :portal.colors/exception "#dc322f"
    :portal.colors/diff-add "#859900"
    :portal.colors/diff-remove "#dc322f"}
   :material-ui
   {:portal.colors/text "#d8dee9"
    :portal.colors/background "#2c2d3b"
    :portal.colors/background2 "#21232f"
    :portal.colors/boolean "#5e81ac"
    :portal.colors/string "#c3e887"
    :portal.colors/keyword "#c792ea"
    :portal.colors/namespace "#ffb563"
    :portal.colors/tag "#ebcb8b"
    :portal.colors/symbol "#92bdec"
    :portal.colors/number "#f77669"
    :portal.colors/uri "#d08770"
    :portal.colors/border "#797979"
    :portal.colors/package "#ffb563"
    :portal.colors/exception "#bf616a"
    :portal.colors/diff-add "#a3be8c"
    :portal.colors/diff-remove "#bf616a"}
   :zerodark
   {:portal.colors/text "#abb2bf"
    :portal.colors/background "#282c34"
    :portal.colors/background2 "#22252c"
    :portal.colors/boolean "#da8548"
    :portal.colors/string "#98be65"
    :portal.colors/keyword "#61afef"
    :portal.colors/namespace "#61afef"
    :portal.colors/tag "#ccd4e3"
    :portal.colors/symbol "#abb2bf"
    :portal.colors/number "#da8548"
    :portal.colors/uri "#da8548"
    :portal.colors/border "#4c566a"
    :portal.colors/package "#88c0d0"
    :portal.colors/exception "#ff6c6b"
    :portal.colors/diff-add "#a3be8c"
    :portal.colors/diff-remove "#ff6c6b"}
   :gruvbox
   {:portal.colors/text "#ebdbb2"
    :portal.colors/background "#282828"
    :portal.colors/background2 "#1e1e1e"
    :portal.colors/boolean "#d3869b"
    :portal.colors/string "#b8bb26"
    :portal.colors/keyword "#d3869b"
    :portal.colors/namespace "#fabd2f"
    :portal.colors/tag "#ebdbb2"
    :portal.colors/symbol "#ebdbb2"
    :portal.colors/number "#d3869b"
    :portal.colors/uri "#da8548"
    :portal.colors/border "#504945"
    :portal.colors/package "#ffffff"
    :portal.colors/exception "#a3be8c"
    :portal.colors/diff-add "#b8bb26"
    :portal.colors/diff-remove "#fb4934"}
   :catppuccin
   {:portal.colors/text "#cdd6f4"
    :portal.colors/background "#1e1e2e"
    :portal.colors/background2 "#181825"
    :portal.colors/boolean "#a6e3a1"
    :portal.colors/string "#f38ba8"
    :portal.colors/keyword "#a6e3a1"
    :portal.colors/namespace "#74c7ec"
    :portal.colors/tag "#f9e2af"
    :portal.colors/symbol "#cdd6f4"
    :portal.colors/number "#89dceb"
    :portal.colors/uri "#fab387"
    :portal.colors/border "#313244"
    :portal.colors/package "#89dceb"
    :portal.colors/exception "#f38ba8"
    :portal.colors/diff-add "#a6e3a1"
    :portal.colors/diff-remove "#f38ba8"}})

(def theme-catalog
  "Ordered catalog of all 12 built-in app chrome themes."
  [{:theme/key :light :theme/label "Light" :theme/quick? true}
   {:theme/key :dark :theme/label "Dark" :theme/quick? true}
   {:theme/key :paper :theme/label "Paper" :theme/quick? true}
   {:theme/key :zenburn :theme/label "Zenburn" :theme/quick? true}
   {:theme/key :nord :theme/label "Nord"}
   {:theme/key :nord-light :theme/label "Nord Light"}
   {:theme/key :solarized-dark :theme/label "Solarized Dark"}
   {:theme/key :solarized-light :theme/label "Solarized Light"}
   {:theme/key :material-ui :theme/label "Material UI"}
   {:theme/key :zerodark :theme/label "Zerodark"}
   {:theme/key :gruvbox :theme/label "Gruvbox"}
   {:theme/key :catppuccin :theme/label "Catppuccin"}])

(defn theme-by-id
  "Returns the palette map for theme-key, defaulting to :light."
  [theme-key]
  (get theme-presets theme-key (:light theme-presets)))

(def ^:private md3-elevations
  {"--md-elevation-1"
   "0 1px 2px rgba(0,0,0,0.12), 0 1px 3px 1px rgba(0,0,0,0.08)"
   "--md-elevation-2"
   "0 2px 6px 2px rgba(0,0,0,0.12), 0 1px 2px rgba(0,0,0,0.16)"
   "--md-elevation-3"
   "0 4px 12px 3px rgba(0,0,0,0.14), 0 2px 4px rgba(0,0,0,0.18)"})

(defn- mix-srgb
  [a pct b]
  (str "color-mix(in srgb, " a " " pct "%, " b ")"))

(defn derive-md3-vars
  "Derives Material Design 3 surface, container, and elevation CSS vars."
  [{:portal.colors/keys [background background2 boolean namespace text border]
    :or {background "#f8fafc" boolean "#2563eb" text "#0f172a"
         border "#cbd5e1"}}]
  (let [bg2 (or background2 background)
        ns-col (or namespace boolean)]
    (assoc md3-elevations
           "--md-surface" background
           "--md-surface-container" bg2
           "--md-surface-container-high" (mix-srgb bg2 88 (str boolean " 12%"))
           "--md-primary" boolean
           "--md-on-primary" background
           "--md-secondary-container" (mix-srgb bg2 82 (str ns-col " 18%"))
           "--md-on-secondary-container" text
           "--md-outline" border
           "--md-outline-variant" (mix-srgb border 65 "transparent")
           "--md-state-hover" (mix-srgb text 8 "transparent"))))

(defn resolve-theme-colors
  "Maps a palette into {--css-var value} pairs including MD3 tokens."
  [palette]
  (let [base (reduce-kv
              (fn [acc app-key portal-key]
                (if-let [hex (get palette portal-key)]
                  (let [var-ref (get colors app-key)
                        css-var (subs var-ref 4 (dec (count var-ref)))]
                    (assoc acc css-var hex))
                  acc))
              {}
              mapping)]
    (merge base (derive-md3-vars palette))))

(defn- apply-css-var!
  [style-obj css-var hex]
  (let [set-prop (.-setProperty style-obj)]
    (.call set-prop style-obj css-var hex)))

(defn apply-theme-fx!
  "Applies resolved CSS custom properties onto document root and body."
  [{:theme/keys [id css-vars]}]
  (when (exists? js/document)
    (let [root-el (.-documentElement js/document)
          root-style (.-style root-el)
          body-style (.-style (.-body js/document))
          set-attr (.-setAttribute root-el)]
      (.call set-attr root-el "data-theme" (name (or id :light)))
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
