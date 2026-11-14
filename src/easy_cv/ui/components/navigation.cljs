(ns easy-cv.ui.components.navigation
  "Top navigation bar, route menu, and top-bar portal."
  (:require ["react-dom" :as rdom]
            [clojure.string :as str]
            [easy-cv.ui.components.common :as common]
            [easy-cv.ui.theme :as t]
            [re-frame.core :as rf]
            [reagent.core :as r]
            [reitit.frontend.easy :as rfe]))

(defn- cls
  [& parts]
  (str/join " " (remove str/blank? parts)))

(def nav-items
  "Primary SPA navigation routes for Easy CV."
  [{:nav/route :archive :nav/label "Fact Archive"}
   {:nav/route :solver :nav/label "CV Solver"}
   {:nav/route :styles :nav/label "Styles"}
   {:nav/route :preview :nav/label "Preview & Export"}
   {:nav/route :editor :nav/label "Code Editor"}
   {:nav/route :io :nav/label "Import / Export"}])

(defn- safe-href
  [route]
  (try
    (or (rfe/href route) (str "#/" (name route)))
    (catch :default _
      (str "#/" (name route)))))

(rf/reg-event-db
 ::set-top-bar-ref
 (fn [db [_ el]]
   (assoc-in db [:ui :navigation :top-bar-ref] el)))

(rf/reg-sub
 ::top-bar-ref
 (fn [db _]
   (get-in db [:ui :navigation :top-bar-ref])))

(defn- nav-dropdown
  [close-fn]
  [:div {:class (cls "absolute left-0 top-full mt-1 w-56 rounded-md"
                     "shadow-lg border z-50 py-1"
                     t/bg-card t/border-subtle)
         :on-mouse-leave close-fn}
   (for [{:nav/keys [route label]} nav-items]
     ^{:key (name route)}
     [:a {:href (safe-href route)
          :class (cls "block px-4 py-2 text-sm hover:bg-white/5"
                      t/text-primary)
          :on-click close-fn}
      label])])

(defn menu-button
  "Renders a toggleable route navigation dropdown button."
  []
  (let [open? (r/atom false)
        close-fn #(reset! open? false)]
    (fn []
      [:div {:class "relative"}
       [:button {:class (cls "p-2 rounded hover:bg-white/10 transition-colors"
                             t/text-primary)
                 :on-click #(swap! open? not)
                 :title "Navigation"}
        [:span {:class "font-mono text-base font-bold"} "="]]
       (when @open?
         [nav-dropdown close-fn])])))

(defn portal-to-top-bar
  "Renders children into the top-bar portal mount node."
  [& children]
  (when-let [el @(rf/subscribe [::top-bar-ref])]
    (rdom/createPortal
     (r/as-element (into [:div {:class "h-full flex items-center"}] children))
     el)))

(defn top-bar
  "Renders the top application bar with navigation and help."
  [{:keys [active-label help-href]}]
  [:div {:class (cls "flex items-center w-full h-12 px-4 border-b"
                     t/border-main t/bg-toolbar)}
   [menu-button]
   [:div {:class (cls "ml-4 font-bold text-lg" t/text-accent)}
    (or active-label "Easy CV")]
   [:div {:class "flex-grow h-full flex items-center justify-end"
          :ref #(rf/dispatch [::set-top-bar-ref %])}
    (when help-href
      [common/help-button
       {:href help-href
        :title "Documentation"
        :class "ml-2 px-2 !py-1"}])]])
