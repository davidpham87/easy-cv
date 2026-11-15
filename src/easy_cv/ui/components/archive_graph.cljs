(ns easy-cv.ui.components.archive-graph
  "MD3 Network Graph SVG, Data Structure Inspector, filter/sort chips, table."
  (:require [clojure.string :as str]
            [easy-cv.core.archive-graph :as ag]
            [easy-cv.ui.components.common :as common]
            [easy-cv.ui.theme :as t]))

(def ^:private sort-options
  [[:kind "Kind"] [:label "Label"] [:date "Date"] [:tags-count "Tags"]])

(defn- truncate-label
  "Truncate s to at most max-len characters with ellipsis."
  [s max-len]
  (let [txt (str s)]
    (if (> (count txt) max-len)
      (str (subs txt 0 (- max-len 1)) "…")
      txt)))

(defn- kind-name
  "Format keyword kind without leading colon."
  [k]
  (if (keyword? k) (subs (str k) 1) (str k)))

(defn- chip-classes
  "Compute MD3 pill classes for active vs inactive filter/sort chips."
  [active?]
  (if active?
    (str "inline-flex items-center gap-1.5 px-2.5 py-1 rounded-full "
         "text-xs font-semibold m3-interactive shadow-sm cursor-pointer "
         t/md-secondary-container " " t/md-on-secondary-container)
    (str "inline-flex items-center gap-1.5 px-2.5 py-1 rounded-full "
         "text-xs font-medium border m3-interactive cursor-pointer "
         "m3-tonal-surface " t/text-secondary " " t/md-outline-variant)))

(defn- tag-chip-btn
  "Render a single clickable tag facet chip button."
  [tag count norm-active on-select-tag]
  (let [tname (name tag)
        active? (= norm-active (str/lower-case tname))]
    [:button {:key tname :type "button" :class (chip-classes active?)
              :data-testid (str "tag-chip-" tname)
              :on-click #(when on-select-tag
                           (on-select-tag (if active? "" tname)))}
     [:span (str "#" tname)]
     [:span {:class "opacity-70 text-[10px]"} count]]))

(defn tag-facet-chips
  "Render clickable tag facet filter chips from extract-tag-facets."
  [{:keys [facets active-tag on-select-tag]}]
  (let [norm (str/lower-case (str/trim (str active-tag)))]
    (when (seq facets)
      [:div {:class "mt-3 flex flex-wrap items-center gap-1.5"
             :data-testid "tag-facet-chips"}
       [:span {:class (str "text-xs font-semibold uppercase mr-1 "
                           t/text-muted)}
        "Tags:"]
       (for [{:keys [tag count]} (take 14 facets)]
         (tag-chip-btn tag count norm on-select-tag))
       (when-not (str/blank? norm)
         [common/button {:variant :tonal :size :xs
                         :on-click #(when on-select-tag (on-select-tag ""))}
          "Clear"])])))

(defn sort-controls-bar
  "Render MD3 sort key pills and asc/desc direction toggle."
  [{:keys [sort-by sort-dir on-sort-by on-toggle-dir]}]
  (let [cur-by (or sort-by :label)
        cur-dir (or sort-dir :asc)]
    [:div {:class "mb-4 flex flex-wrap items-center justify-between gap-2"
           :data-testid "archive-sort-controls"}
     [:div {:class "flex flex-wrap items-center gap-1.5"}
      [:span {:class (str "text-xs font-semibold uppercase mr-1 " t/text-muted)}
       "Sort:"]
      (for [[k lbl] sort-options]
        [:button {:key (name k) :type "button"
                  :class (chip-classes (= cur-by k))
                  :data-testid (str "sort-by-" (name k))
                  :on-click #(when on-sort-by (on-sort-by k))} lbl])]
     [common/button {:variant :tonal :size :xs :data-testid "sort-dir-toggle"
                     :on-click #(when on-toggle-dir (on-toggle-dir))}
      (if (= :desc cur-dir) "Desc ↓" "Asc ↑")]]))

(defn- legend-bar
  "Render kind color legend pills for nodes currently in the graph."
  [nodes]
  (let [kinds (->> nodes (map (juxt :node/kind :node/color)) distinct
                   (sort-by (comp kind-name first)))]
    (when (seq kinds)
      [:div {:class "flex flex-wrap items-center gap-2 mb-2"
             :data-testid "graph-kind-legend"}
       (for [[k color] kinds]
         [:span {:key (kind-name k)
                 :class (str "inline-flex items-center gap-1 text-[11px] px-2 "
                             "py-0.5 rounded-full border "
                             t/md-outline-variant " " t/text-secondary)}
          [:span {:class "inline-block w-2 h-2 rounded-full"
                  :style {:background-color color}}]
          [:span (kind-name k)]])])))

(defn- hover-node-info
  "Render compact badge + summary for a hovered graph node."
  [node]
  [:div {:class "flex items-center gap-2 truncate"}
   [:span {:class "inline-block w-2.5 h-2.5 rounded-full shrink-0"
           :style {:background-color (:node/color node)}}]
   [:span {:class (str "font-semibold " t/text-primary)} (:node/label node)]
   [:span {:class t/text-muted} (str "(" (kind-name (:node/kind node)) ")")]
   (when-not (str/blank? (:node/sublabel node))
     [:span {:class (str "truncate " t/text-secondary)}
      (str "· " (:node/sublabel node))])])

(defn- hover-tooltip-bar
  "Render MD3 tonal tooltip bar for the hovered graph node."
  [nodes hovered-id]
  (let [node (when hovered-id
               (first (filter #(= hovered-id (:node/id %)) nodes)))]
    [:div {:class (str "mb-2 px-3 py-1.5 rounded-xl border text-xs flex "
                       "items-center justify-between min-h-[30px] "
                       "m3-tonal-surface " t/md-outline-variant)
           :data-testid "graph-hover-tooltip"}
     (if node
       [hover-node-info node]
       [:span {:class t/text-muted}
        "Hover a node for details; click any node or row to inspect."])]))

(defn- graph-edge-el
  "Render one SVG relationship edge line with highlight transition."
  [edge selected-id]
  (let [hl? (:edge/highlighted? edge)
        stroke (if hl?
                 "var(--md-primary, #6366f1)"
                 "var(--md-outline, #94a3b8)")
        tr-css "stroke-opacity 200ms ease, stroke-width 200ms"]
    [:line {:key (:edge/id edge)
            :x1 (:edge/x1 edge) :y1 (:edge/y1 edge)
            :x2 (:edge/x2 edge) :y2 (:edge/y2 edge)
            :stroke stroke
            :stroke-width (if hl? 2.2 1.1)
            :stroke-opacity (if hl? 0.92 (if selected-id 0.2 0.45))
            :style {:transition tr-css}}
     [:title (:edge/label edge)]]))

(defn- node-circles
  "Render highlight ring and main circle for a graph node."
  [node r sel? selected-id]
  [:<>
   (when sel?
     [:circle {:r (+ r 5.5) :fill "none" :stroke "var(--md-primary, #6366f1)"
               :stroke-width 2.5 :stroke-dasharray "4 2"}])
   [:circle {:r r :fill (:node/color node)
             :fill-opacity (if (and selected-id (not sel?)) 0.65 0.95)
             :stroke "#ffffff" :stroke-width 1.8}]])

(def ^:private node-transition-css
  (str "transform 320ms var(--md-ease-standard, cubic-bezier(0.2,0,0,1)), "
       "opacity 200ms ease"))

(defn- graph-node-el
  "Render one clickable, GPU-animated SVG node group."
  [node selected-id on-select on-hover]
  (let [nid (:node/id node)
        r (:node/r node)
        xform (str "translate(" (:node/x node) "," (:node/y node) ")")]
    [:g {:key nid :class "graph-node cursor-pointer"
         :data-testid (str "graph-node-" nid) :transform xform
         :style {:transition node-transition-css}
         :on-click #(when on-select (on-select nid))
         :on-mouse-enter #(when on-hover (on-hover nid))
         :on-mouse-leave #(when on-hover (on-hover nil))}
     [node-circles node r (:node/selected? node) selected-id]
     [:text {:y (+ r 12) :text-anchor "middle" :font-size "9"
             :fill "var(--text-secondary, #64748b)"
             :class "pointer-events-none select-none"}
      (truncate-label (:node/label node) 16)]
     [:title (str (kind-name (:node/kind node)) ": " (:node/label node))]]))

(defn- layout-btn
  "Render a single layout mode pill button."
  [mode active-mode lbl testid on-toggle]
  [common/button {:variant (if (= mode active-mode) :primary :default)
                  :size :xs :data-testid testid
                  :on-click #(when on-toggle (on-toggle mode))} lbl])

(defn- graph-toolbar-actions
  "Render layout toggle and reset selection buttons for graph header."
  [nodes edges mode selected-id on-toggle on-reset]
  [:div {:class "flex flex-wrap items-center gap-1.5"}
   [common/badge {} (str (count nodes) " nodes · " (count edges) " edges")]
   [layout-btn :clustered mode "Clustered" "layout-clustered-btn" on-toggle]
   [layout-btn :radial mode "Radial" "layout-radial-btn" on-toggle]
   [common/button {:variant :tonal :size :xs :disabled (nil? selected-id)
                   :data-testid "reset-graph-selection"
                   :on-click #(when on-reset (on-reset))} "Reset"]])

(defn- graph-svg
  "Render SVG canvas with edges and nodes."
  [graph selected-id on-select on-hover]
  (let [nodes (:graph/nodes graph)
        edges (:graph/edges graph)]
    [:svg {:data-testid "archive-network-graph"
           :viewBox (or (:graph/viewbox graph) ag/graph-viewbox)
           :role "img" :aria-label "Archive Knowledge Network Graph"
           :class (str "w-full h-72 rounded-xl border "
                       t/md-outline-variant " " t/bg-input)}
     [:g {:class "graph-edges"}
      (for [edge edges] (graph-edge-el edge selected-id))]
     [:g {:class "graph-nodes"}
      (for [node nodes] (graph-node-el node selected-id on-select on-hover))]]))

(defn network-graph-panel
  "Render MD3 interactive SVG Network Graph panel with legend and tooltips."
  [{:keys [graph layout-mode selected-id hovered-id
           on-select on-hover on-toggle-layout on-reset]}]
  (let [nodes (:graph/nodes graph)
        edges (:graph/edges graph)
        mode (or layout-mode (:graph/layout graph) :clustered)]
    [common/card {:class "mb-4" :data-testid "archive-network-graph-panel"}
     [common/section-header
      "Knowledge Network Graph"
      [graph-toolbar-actions
       nodes edges mode selected-id on-toggle-layout on-reset]]
     [legend-bar nodes]
     [hover-tooltip-bar nodes hovered-id]
     (if (empty? nodes)
       [:div {:class (str "h-48 flex items-center justify-center rounded-xl "
                          "border text-sm " t/md-outline-variant " "
                          t/text-muted)}
        "Load a tier or add facts to visualize archive relationships."]
       [graph-svg graph selected-id on-select on-hover])]))

(defn- inspector-header-actions
  "Render [EDN | JSON] format toggle and Copy button."
  [fmt entity text copied? on-format-change on-copy]
  [:div {:class "flex items-center gap-1.5"}
   [layout-btn :edn fmt "EDN" "inspector-fmt-edn" on-format-change]
   [layout-btn :json fmt "JSON" "inspector-fmt-json" on-format-change]
   [common/button {:variant :tonal :size :xs :disabled (nil? entity)
                   :data-testid "inspector-copy-btn"
                   :on-click #(when (and entity on-copy) (on-copy text))}
    (if copied? "Copied" "Copy")]])

(defn- inspector-body
  "Render formatted EDN/JSON inspector body or empty selection hint."
  [entity text]
  (if entity
    [:div {:class "space-y-2"}
     [:div {:class "flex flex-wrap items-center gap-2 text-xs"}
      [common/badge {} (kind-name (ag/fact-kind entity))]
      [:span {:class (str "font-semibold truncate " t/text-primary)}
       (ag/fact-label entity)]]
     [common/pre-block {:content text :class "max-h-64 text-xs"}]]
    [:div {:class (str "rounded-xl border p-4 text-xs text-center "
                       "m3-tonal-surface " t/md-outline-variant " "
                       t/text-muted)}
     "Click a table row or network graph node to inspect its EDN/JSON."]))

(defn inspector-card
  "Render MD3 Live Data Structure Inspector card with EDN/JSON toggle."
  [{:keys [entity format copied? on-format-change on-copy]}]
  (let [fmt (or format :edn)
        formatted (when entity (ag/inspect-entity->formats entity))
        text (if formatted (get formatted fmt "") "")]
    [common/card {:data-testid "fact-inspector"}
     [common/section-header
      "Live Inspector"
      [inspector-header-actions
       fmt entity text copied? on-format-change on-copy]]
     [inspector-body entity text]]))

(defn- sort-th
  "Render clickable sortable table header cell with direction indicator."
  [lbl col-k active-k dir on-sort]
  (let [arrow (when (= col-k active-k) (if (= :desc dir) " ↓" " ↑"))]
    [common/th {:class "cursor-pointer select-none hover:opacity-80"
                :data-testid (str "col-sort-" (name col-k))
                :on-click #(when on-sort (on-sort col-k))}
     (str lbl (or arrow ""))]))

(defn- fact-row
  "Render a single clickable fact row inside the archive table."
  [fact eid selected-id on-select]
  (let [sel? (and selected-id (= eid selected-id))
        row-cls (str "cursor-pointer "
                     (when sel? (str t/md-secondary-container " "
                                     t/md-on-secondary-container)))]
    [common/tr {:key eid :class row-cls :data-testid (str "fact-row-" eid)
                :on-click #(when on-select (on-select eid))}
     [common/td {} [common/badge {} (name (ag/fact-kind fact))]]
     [common/td {:class "font-medium"} (ag/fact-label fact)]
     [common/td {:class (str "text-xs font-mono " t/text-muted)}
      (ag/fact-date fact)]
     [common/td {:class (str "text-xs " t/text-muted)} (ag/fact-summary fact)]
     [common/td {}
      [:div {:class "flex flex-wrap gap-1"}
       (for [tag (:item/tags fact)]
         [common/badge {:key (name tag)} (name tag)])]]]))

(defn facts-table
  "Render filtered, sorted, clickable archive facts table."
  [{:keys [facts sort-by sort-dir selected-id id-fn on-sort on-select]}]
  [common/table-container {}
   [common/table {}
    [common/thead {}
     [common/tr {}
      [sort-th "Type" :kind sort-by sort-dir on-sort]
      [sort-th "Title" :label sort-by sort-dir on-sort]
      [sort-th "Date" :date sort-by sort-dir on-sort]
      [common/th {} "Details"]
      [sort-th "Tags" :tags-count sort-by sort-dir on-sort]]]
    [common/tbody {}
     (if (empty? facts)
       [common/tr {}
        [common/td {:col-span 5 :class "text-center py-6"}
         "No matching facts. Load a seeded tier or add a quick fact."]]
       (for [fact facts]
         (fact-row fact (id-fn fact) selected-id on-select)))]]])
