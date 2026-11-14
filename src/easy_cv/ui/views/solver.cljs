(ns easy-cv.ui.views.solver
  "Multi-CV solver configuration view: targets, filters, overrides, order."
  (:require [clojure.string :as str]
            [easy-cv.core.db :as db]
            [easy-cv.core.solver :as solver]
            [easy-cv.fx.datascript :as fx-ds]
            [easy-cv.ui.components.common :as common]
            [easy-cv.ui.components.cv-blocks :as cv-blocks]
            [easy-cv.ui.components.layout :as layout]
            [easy-cv.ui.theme :as t]
            [re-frame.core :as rf]))

(def override-field-options
  "Fact attributes editable via per-CV overrides."
  [:history/title :history/subtitle :history/description
   :project/name :project/description :skill/name :document/name])

(defn- slug->cv-id
  "Convert a human CV title string into a keyword :cv/id."
  [title]
  (let [cleaned (-> (str/lower-case (str title))
                    (str/replace #"[^a-z0-9]+" "-")
                    (str/replace #"^-|-$" ""))]
    (keyword (if (str/blank? cleaned) "custom-cv" cleaned))))

(defn- item-label
  "Return human-readable label for any archive fact."
  [item]
  (or (:history/title item) (:project/name item) (:skill/name item)
      (:document/name item) (:hobby/name item) (str (:item/uuid item))))

(defn- fact-membership
  "Return :excluded, :included, or :auto for item-uuid under cfg."
  [cfg item-uuid]
  (cond
    (contains? (set (:cv/exclude cfg)) item-uuid) :excluded
    (contains? (set (:cv/include cfg)) item-uuid) :included
    :else :auto))

(defn- swap-section-order
  "Move sec-id one slot :up or :down inside order-vec."
  [order-vec sec-id dir]
  (let [v (vec (or (seq order-vec) solver/default-section-order))
        idx (first (keep-indexed #(when (= %2 sec-id) %1) v))
        target (when idx (if (= dir :up) (dec idx) (inc idx)))]
    (if (and idx target (<= 0 target) (< target (count v)))
      (assoc v idx (nth v target) target sec-id)
      v)))

(defn- candidate-item?
  "True for career fact entities selectable by the solver."
  [fact]
  (boolean (or (:history/title fact) (:project/name fact) (:skill/name fact)
               (:document/name fact) (:hobby/name fact))))

(defn- first-person-uuid
  "Find first :person/email entity UUID in ds-db if present."
  [ds-db]
  (some :item/uuid (db/facts-by-type ds-db :person)))

(rf/reg-cofx
 ::ds-db
 (fn [cofx _]
   (assoc cofx :ds-db @fx-ds/conn)))

(rf/reg-event-db
 ::init
 (fn [app-db _]
   (update app-db :solver
           #(merge {:active-cv-id nil
                    :new-cv-title ""
                    :show-preview? false
                    :override-draft {:override/uuid nil
                                     :override/field :history/title
                                     :override/text ""}}
                   %))))

(rf/reg-event-db
 ::select-cv
 (fn [app-db [_ cv-id]]
   (assoc-in app-db [:solver :active-cv-id] cv-id)))

(rf/reg-event-db
 ::set-new-cv-title
 (fn [app-db [_ title]]
   (assoc-in app-db [:solver :new-cv-title] title)))

(rf/reg-event-db
 ::toggle-preview
 (fn [app-db _]
   (update-in app-db [:solver :show-preview?] not)))

(rf/reg-event-db
 ::set-override-draft
 (fn [app-db [_ field val]]
   (assoc-in app-db [:solver :override-draft field] val)))

(rf/reg-event-db
 ::load-override-draft
 (fn [app-db [_ item-uuid field text]]
   (assoc-in app-db [:solver :override-draft]
             {:override/uuid item-uuid
              :override/field (or field :history/title)
              :override/text (or text "")})))

(rf/reg-event-fx
 ::create-cv-target
 [(rf/inject-cofx ::ds-db)]
 (fn [{:keys [db ds-db]} _]
   (let [title (str/trim (get-in db [:solver :new-cv-title] ""))]
     (if (str/blank? title)
       {}
       (let [cv-id (slug->cv-id title)
             person-id (first-person-uuid ds-db)
             cfg (cond-> {:item/uuid (random-uuid)
                          :cv/id cv-id
                          :cv/title title
                          :cv/include #{}
                          :cv/exclude #{}
                          :cv/overrides {}
                          :cv/section-order solver/default-section-order
                          :cv/style solver/default-style}
                   person-id (assoc :cv/person person-id))]
         {:db (-> db
                  (assoc-in [:solver :active-cv-id] cv-id)
                  (assoc-in [:solver :new-cv-title] ""))
          :ds/transact [cfg]})))))

(defn- update-config-tx
  "Build :ds/transact effect updating active CVConfig via f."
  [ds-db cv-id f]
  (if-let [cfg (db/find-cv-config ds-db cv-id)]
    {:ds/transact [(merge {:db/id [:cv/id cv-id]} (f cfg))]}
    {}))

(rf/reg-event-fx
 ::include-fact
 [(rf/inject-cofx ::ds-db)]
 (fn [{:keys [ds-db]} [_ cv-id item-uuid]]
   (update-config-tx
    ds-db cv-id
    (fn [cfg]
      {:cv/include (conj (set (:cv/include cfg)) item-uuid)
       :cv/exclude (disj (set (:cv/exclude cfg)) item-uuid)}))))

(rf/reg-event-fx
 ::exclude-fact
 [(rf/inject-cofx ::ds-db)]
 (fn [{:keys [ds-db]} [_ cv-id item-uuid]]
   (update-config-tx
    ds-db cv-id
    (fn [cfg]
      {:cv/exclude (conj (set (:cv/exclude cfg)) item-uuid)}))))

(rf/reg-event-fx
 ::reset-fact
 [(rf/inject-cofx ::ds-db)]
 (fn [{:keys [ds-db]} [_ cv-id item-uuid]]
   (update-config-tx
    ds-db cv-id
    (fn [cfg]
      {:cv/include (disj (set (:cv/include cfg)) item-uuid)
       :cv/exclude (disj (set (:cv/exclude cfg)) item-uuid)}))))

(rf/reg-event-fx
 ::set-override
 [(rf/inject-cofx ::ds-db)]
 (fn [{:keys [ds-db]} [_ cv-id item-uuid field text]]
   (update-config-tx
    ds-db cv-id
    (fn [cfg]
      (let [ov (or (:cv/overrides cfg) {})
            cur (get ov item-uuid {})
            nxt (if (str/blank? text)
                  (dissoc cur field)
                  (assoc cur field text))]
        {:cv/overrides (if (empty? nxt)
                         (dissoc ov item-uuid)
                         (assoc ov item-uuid nxt))})))))

(rf/reg-event-fx
 ::clear-override
 [(rf/inject-cofx ::ds-db)]
 (fn [{:keys [ds-db]} [_ cv-id item-uuid]]
   (update-config-tx
    ds-db cv-id
    (fn [cfg]
      {:cv/overrides (dissoc (or (:cv/overrides cfg) {}) item-uuid)}))))

(rf/reg-event-fx
 ::move-section
 [(rf/inject-cofx ::ds-db)]
 (fn [{:keys [ds-db]} [_ cv-id sec-id dir]]
   (update-config-tx
    ds-db cv-id
    (fn [cfg]
      {:cv/section-order (swap-section-order (:cv/section-order cfg)
                                             sec-id dir)}))))

(rf/reg-sub
 ::slice
 (fn [app-db _] (:solver app-db)))

(rf/reg-sub
 ::new-cv-title
 :<- [::slice]
 (fn [s _] (or (:new-cv-title s) "")))

(rf/reg-sub
 ::show-preview?
 :<- [::slice]
 (fn [s _] (boolean (:show-preview? s))))

(rf/reg-sub
 ::override-draft
 :<- [::slice]
 (fn [s _]
   (or (:override-draft s)
       {:override/uuid nil :override/field :history/title :override/text ""})))

(rf/reg-sub
 ::configs
 :<- [:easy-cv.fx.datascript/db]
 (fn [ds-db _] (db/list-cv-configs ds-db)))

(rf/reg-sub
 ::active-cv-id
 :<- [::slice]
 :<- [::configs]
 (fn [[s cfgs] _]
   (let [chosen (:active-cv-id s)]
     (if (some #(= chosen (:cv/id %)) cfgs)
       chosen
       (:cv/id (first cfgs))))))

(rf/reg-sub
 ::active-config
 :<- [::configs]
 :<- [::active-cv-id]
 (fn [[cfgs cv-id] _]
   (first (filter #(= cv-id (:cv/id %)) cfgs))))

(rf/reg-sub
 ::candidate-items
 :<- [:easy-cv.fx.datascript/db]
 (fn [ds-db _]
   (filterv candidate-item? (db/all-facts ds-db))))

(rf/reg-sub
 ::solved-cv
 :<- [:easy-cv.fx.datascript/db]
 :<- [::active-cv-id]
 (fn [[ds-db cv-id] _]
   (when (and ds-db cv-id (db/find-cv-config ds-db cv-id))
     (solver/solve-cv ds-db cv-id))))

(rf/reg-sub
 ::section-counts
 :<- [::solved-cv]
 (fn [solved _]
   (mapv (fn [{:section/keys [id items]}]
           {:section/id id :section/count (count items)})
         (:cv/sections solved))))

(defn- with-val
  "Attach HTML :value prop without an unqualified-key map literal."
  [props v]
  (assoc props (keyword "value") v))

(defn- select-opt
  "Render an HTML <option> element for select controls."
  [v lbl]
  [:option (with-val {:key v} v) lbl])

(defn- find-cv-id
  "Match stringified option value back to its :cv/id in configs."
  [configs s]
  (some #(when (= s (str (:cv/id %))) (:cv/id %)) configs))

(defn- new-target-form
  "Render input and button to create a new CV target."
  [new-title]
  [:div {:class "flex gap-2 items-end"}
   [:div {:class "flex-grow"}
    [common/label {} "New CV Target"]
    [common/input (with-val {:placeholder "Staff Backend CV"
                             :on-change-event [::set-new-cv-title]}
                    new-title)]]
   [common/button {:variant :primary :size :sm
                   :on-click #(rf/dispatch [::create-cv-target])}
    "Create"]])

(defn- target-selector-card
  "Render CV target switcher and new target creation input."
  [configs active-id]
  (let [new-title @(rf/subscribe [::new-cv-title])]
    [common/card {:class "mb-4"}
     [:div {:class "grid grid-cols-1 md:grid-cols-2 gap-3 items-end"}
      [:div
       [common/label {} "Active CV Target"]
       [common/select
        (with-val {:class "w-full text-sm"
                   :on-change #(when-let [cv-id (find-cv-id configs %)]
                                 (rf/dispatch [::select-cv cv-id]))}
          (if active-id (str active-id) ""))
        (for [cfg configs]
          (select-opt (str (:cv/id cfg))
                      (str (or (:cv/title cfg) (:cv/id cfg)))))]]
      [new-target-form new-title]]]))

(defn- action-btn
  "Render an extra-small button dispatching a Re-frame event."
  [lbl variant evt]
  [common/button {:size :xs :variant variant :on-click #(rf/dispatch evt)} lbl])

(defn- fact-toggle-row
  "Render a single candidate fact with include/exclude/override buttons."
  [cfg item]
  (let [cv-id (:cv/id cfg)
        uid (:item/uuid item)
        state (fact-membership cfg uid)]
    [common/tr {:key (str uid)}
     [common/td {:class "font-medium"} (item-label item)]
     [common/td {} [common/badge {} (name state)]]
     [common/td {}
      [:div {:class "flex flex-wrap gap-1"}
       [action-btn "Include" :primary [::include-fact cv-id uid]]
       [action-btn "Exclude" :danger [::exclude-fact cv-id uid]]
       [action-btn "Reset" :default [::reset-fact cv-id uid]]
       [action-btn "Edit" :default [::load-override-draft uid :history/title
                                    (item-label item)]]]]]))

(defn- fact-inclusion-card
  "Render candidate facts table for toggling include/exclude rules."
  [cfg items]
  [common/card {:class "mb-4"}
   [common/section-header {:title "Fact Include / Exclude"}]
   [common/table-container {}
    [common/table {}
     [common/thead {}
      [common/tr {}
       [common/th {} "Fact"] [common/th {} "State"] [common/th {} "Actions"]]]
     [common/tbody {}
      (if (empty? items)
        [common/tr {} [common/td {:col-span 3} "No candidate facts in store."]]
        (for [item items] (fact-toggle-row cfg item)))]]]])

(defn- override-actions
  "Render Apply Override and Clear action buttons."
  [cv-id uid fld txt]
  [:div {:class "flex gap-2"}
   [common/button {:variant :primary :size :sm :disabled (nil? uid)
                   :on-click #(rf/dispatch [::set-override cv-id uid fld txt])}
    "Apply Override"]
   [common/button {:variant :danger :size :sm :disabled (nil? uid)
                   :on-click #(rf/dispatch [::clear-override cv-id uid])}
    "Clear"]])

(defn- override-field-select
  "Render attribute dropdown for per-CV override editing."
  [fld]
  [common/select
   (with-val {:class "w-full text-sm"
              :on-change #(rf/dispatch [::set-override-draft
                                        :override/field (keyword %)])}
     (subs (str fld) 1))
   (for [f override-field-options]
     (let [s (subs (str f) 1)] (select-opt s s)))])

(defn- override-editor-card
  "Render per-CV field override form and active overrides list."
  [cfg]
  (let [draft @(rf/subscribe [::override-draft])
        uid (:override/uuid draft)
        fld (or (:override/field draft) :history/title)
        txt (or (:override/text draft) "")]
    [common/card {}
     [common/section-header {:title "Per-CV Overrides"}]
     [:div {:class "space-y-2"}
      [:div {:class (str "text-xs " t/text-muted)}
       (if uid (str "Selected UUID: " uid) "Pick a fact row -> Edit")]
      [override-field-select fld]
      [common/input (with-val {:placeholder "Override value..."
                               :on-change-event [::set-override-draft
                                                 :override/text]} txt)]
      [override-actions (:cv/id cfg) uid fld txt]]]))

(defn- section-order-row
  "Render a single section row with count and Up/Down reorder buttons."
  [cv-id sec-id cnt]
  [:div {:key (name sec-id)
         :class "flex items-center justify-between text-sm"}
   [:span (str (name sec-id) " (" cnt ")")]
   [:div {:class "flex gap-1"}
    [action-btn "Up" :default [::move-section cv-id sec-id :up]]
    [action-btn "Down" :default [::move-section cv-id sec-id :down]]]])

(defn- section-order-card
  "Render section order controls and solved item counts per section."
  [cfg counts]
  (let [cv-id (:cv/id cfg)
        count-map (into {} (map (juxt :section/id :section/count) counts))
        order (or (seq (:cv/section-order cfg)) solver/default-section-order)]
    [common/card {:class "mb-4"}
     [common/section-header {:title "Section Order & Solved Counts"}]
     [:div {:class "space-y-2"}
      (for [sec-id order]
        (section-order-row cv-id sec-id (get count-map sec-id 0)))]]))

(defn- solved-inspector-card
  "Render solved CanonicalCV summary counts and optional live preview."
  [solved]
  (let [show? @(rf/subscribe [::show-preview?])
        total (reduce + 0 (map (comp count :section/items)
                               (:cv/sections solved)))]
    [common/card {}
     [:div {:class "flex items-center justify-between mb-2"}
      [:h3 {:class "font-semibold"}
       (str "Solved: " (or (:cv/title solved) "None") " (" total " items)")]
      [common/button {:size :xs :on-click #(rf/dispatch [::toggle-preview])}
       (if show? "Hide Preview" "Show Preview")]]
     (when (and show? solved)
       [:div {:class "mt-3 overflow-auto max-h-96 border rounded p-2"}
        [cv-blocks/cv-preview solved]])]))

(defn view
  "Main Multi-CV Solver Reagent view component."
  []
  (let [configs @(rf/subscribe [::configs])
        cfg @(rf/subscribe [::active-config])
        items @(rf/subscribe [::candidate-items])
        solved @(rf/subscribe [::solved-cv])
        counts @(rf/subscribe [::section-counts])]
    [layout/split-view {:ratio :1-1}
     [:div {:class "p-4"}
      [common/page-header {:title "Multi-CV Solver"
                           :description "Configure targets, rules & overrides"}]
      [target-selector-card configs (:cv/id cfg)]
      (when cfg [fact-inclusion-card cfg items])
      (when cfg [override-editor-card cfg])]
     [:div {:class "p-4"}
      (when cfg [section-order-card cfg counts])
      [solved-inspector-card solved]]]))
