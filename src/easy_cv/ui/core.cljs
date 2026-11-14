(ns easy-cv.ui.core
  "Root browser entrypoint, boot effects, and Re-frame shell for Easy CV."
  (:require [easy-cv.fx.datascript :as fx-ds]
            [easy-cv.fx.persistence]
            [easy-cv.fx.router :as fx-router]
            [easy-cv.ui.components.common :as common]
            [easy-cv.ui.components.layout :as layout]
            [easy-cv.ui.components.navigation :as nav]
            [easy-cv.ui.theme :as theme]
            [easy-cv.ui.views.editor :as editor]
            [easy-cv.ui.views.preview :as preview]
            [malli.core :as m]
            [malli.registry :as mr]
            [malli.util :as mu]
            [re-frame.core :as rf]
            [reagent.dom.client :as rdomc]))

(defonce ^:private root-atom (atom nil))

(def theme-options
  "Available app chrome themes rendered in the top bar."
  [{:theme/key :light :theme/label "Light"}
   {:theme/key :dark :theme/label "Dark"}
   {:theme/key :paper :theme/label "Paper"}])

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

(defmethod view :editor
  [_]
  [editor/view])

(defmethod view :io
  [_]
  [editor/view])

(defmethod view :preview
  [_]
  [preview/view])

(defmethod view :styles
  [_]
  [preview/view])

(defmethod view :default
  [route-kw]
  [layout/container {:class "py-8"}
   [common/card {}
    [:h2 {:class "text-lg font-bold"}
     (str "Workspace: " (name (or route-kw :archive)))]
    [:p {:class "mt-2 text-sm"}
     "Use the navigation menu for Editor or Preview & Export."]]])

(defn theme-toggle
  "Renders theme selector buttons inside the top bar portal."
  []
  (let [current @(rf/subscribe [::theme/current-theme])]
    [:div {:class "flex items-center gap-1 mr-2 print:hidden"}
     (for [{:theme/keys [key label]} theme-options]
       ^{:key key}
       [common/button
        {:size :xs
         :variant (when (= current key) :primary)
         :on-click #(rf/dispatch [::theme/set-theme key])}
        label])]))

(defn root-view
  "Top-level Reagent application shell."
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
