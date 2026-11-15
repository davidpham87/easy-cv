(ns easy-cv.ui.core
  "Root browser entrypoint, boot effects, and Re-frame MD3 shell."
  (:require [easy-cv.fx.datascript :as fx-ds]
            [easy-cv.fx.persistence]
            [easy-cv.fx.router :as fx-router]
            [easy-cv.ui.components.common :as common]
            [easy-cv.ui.components.layout :as layout]
            [easy-cv.ui.components.navigation :as nav]
            [easy-cv.ui.theme :as theme]
            [easy-cv.ui.views.archive :as archive]
            [easy-cv.ui.views.editor :as editor]
            [easy-cv.ui.views.preview :as preview]
            [easy-cv.ui.views.solver :as solver]
            [malli.core :as m]
            [malli.registry :as mr]
            [malli.util :as mu]
            [re-frame.core :as rf]
            [reagent.dom.client :as rdomc]))

(defonce ^:private root-atom (atom nil))

(def theme-options
  "All 12 available app chrome themes rendered in the top bar."
  theme/theme-catalog)

(def quick-theme-options
  "Quick-access chrome themes rendered as pill buttons."
  (filterv :theme/quick? theme-options))

(defn init-malli-registry!
  "Registers default and util Malli schemas once at boot."
  []
  (mr/set-default-registry!
   (mr/composite-registry (m/default-schemas) (mu/schemas))))

(rf/reg-event-fx
 ::persistence-ready
 (fn [{:keys [db]} [_ payload]]
   {:db (assoc-in db [:ui :persistence] (or payload {:persistence/ok true}))}))

(rf/reg-event-fx
 ::boot
 (fn [{:keys [db]} _]
   (let [theme-kw (get-in db [:ui :theme] :light)
         palette (theme/theme-by-id theme-kw)
         css-vars (theme/resolve-theme-colors palette)]
     (cond-> {:db (assoc-in db [:ui :booted?] true)
              :theme/apply {:theme/id theme-kw
                            :theme/palette palette
                            :theme/css-vars css-vars}}
       (exists? js/Worker)
       (assoc :persistence/init
              {:persistence/on-ready [::persistence-ready]})))))

(defmulti view
  "Route-dispatched primary workspace panel."
  identity)

(defmethod view :archive [_] [archive/view])
(defmethod view :solver [_] [solver/view])
(defmethod view :editor [_] [editor/view])
(defmethod view :io [_] [editor/view])
(defmethod view :preview [_] [preview/view])
(defmethod view :styles [_] [preview/view])
(defmethod view :default [_] [archive/view])

(defn- theme-select
  [current]
  [common/select
   (assoc {:class "text-xs !py-1 !px-2.5 !rounded-full font-medium"
           :aria-label "All Themes"
           :data-testid "theme-select"
           :on-change #(rf/dispatch
                        [::theme/set-theme (keyword (.. % -target -value))])}
          (keyword "value") (name (or current :light)))
   (for [{:theme/keys [key label]} theme-options]
     ^{:key (name key)}
     [:option (assoc {} (keyword "value") (name key)) label])])

(defn theme-toggle
  "Renders MD3 quick theme pills and a 12-theme select in the top bar."
  []
  (let [current @(rf/subscribe [::theme/current-theme])]
    [:div {:class (str "flex items-center gap-1.5 mr-2 px-2 py-1 "
                       "rounded-full border m3-tonal-surface print:hidden "
                       theme/md-outline-variant)}
     (for [{:theme/keys [key label]} quick-theme-options]
       ^{:key (name key)}
       [common/button
        {:size :xs
         :variant (when (= current key) :primary)
         :on-click #(rf/dispatch [::theme/set-theme key])}
        label])
     [theme-select current]]))

(defn root-view
  "Top-level Reagent MD3 application shell."
  []
  (let [route-kw @(rf/subscribe [::fx-router/route-name])]
    [layout/page-container {}
     [layout/flex-col {:class "w-full h-full overflow-hidden"}
      [:div {:class "print:hidden"}
       [nav/top-bar {:active-label "Easy CV"}]]
      [nav/portal-to-top-bar [theme-toggle]]
      [layout/main {}
       [view route-kw]]]]))

(defn mount-root
  "Mounts or updates the Reagent root inside #app."
  []
  (when-let [el (js/document.querySelector "#app")]
    (let [root (or @root-atom (rdomc/create-root el))]
      (reset! root-atom root)
      (rdomc/render root [root-view]))))

(defn ^:dev/after-load reload!
  "Hot-reload hook invoked by shadow-cljs."
  []
  (mount-root))

(defn ^:export init
  "Application entrypoint invoked on page load."
  []
  (init-malli-registry!)
  (fx-ds/seed-demo-archive!)
  (fx-router/init-routes!)
  (rf/dispatch-sync [::boot])
  (mount-root))
