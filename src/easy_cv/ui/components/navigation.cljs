(ns easy-cv.ui.components.navigation
  "MD3 top navigation bar, animated route menu, and top-bar portal."
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
  [:div {:class (cls "absolute left-0 top-full mt-2 w-60 rounded-2xl"
                     "border z-50 p-1.5 m3-menu-surface m3-menu-enter"
                     t/md-outline-variant)
         :on-mouse-leave close-fn}
   (for [{:nav/keys [route label]} nav-items]
     ^{:key (name route)}
     [:a {:href (safe-href route)
          :class (cls "flex items-center px-3.5 py-2.5 rounded-xl text-sm"
                      "font-medium transition-colors m3-interactive"
                      "hover:bg-[var(--md-state-hover)]" t/text-primary)
          :on-click close-fn}
      label])])

(defn- menu-icon
  []
  [:span {:class "flex flex-col gap-1 w-4" :aria-hidden "true"}
   [:span {:class "block h-0.5 w-4 rounded-full bg-current"}]
   [:span {:class "block h-0.5 w-4 rounded-full bg-current"}]
   [:span {:class "block h-0.5 w-3 rounded-full bg-current"}]])

(defn menu-button
  "Renders an animated MD3 toggleable route navigation dropdown button."
  []
  (let [open? (r/atom false)
        close-fn #(reset! open? false)]
    (fn []
      [:div {:class "relative"}
       [:button {:class (cls "w-9 h-9 rounded-full flex items-center"
                             "justify-center m3-interactive"
                             "hover:bg-[var(--md-state-hover)]"
                             t/text-primary)
                 :on-click #(swap! open? not)
                 :aria-expanded @open?
                 :aria-label "Navigation"
                 :title "Navigation"}
        [menu-icon]]
       (when @open?
         [nav-dropdown close-fn])])))

(defn portal-to-top-bar
  "Renders children into the top-bar portal mount node."
  [& children]
  (when-let [el @(rf/subscribe [::top-bar-ref])]
    (rdom/createPortal
     (r/as-element
      (into [:div {:class "h-full flex items-center"}] children))
     el)))

(defn top-bar
  "Renders the MD3 top application bar with navigation and help."
  [{:keys [active-label help-href]}]
  [:div {:class (cls "flex items-center w-full h-14 px-4 border-b"
                     "m3-tonal-surface shrink-0"
                     t/md-outline-variant t/bg-toolbar)}
   [menu-button]
   [:div {:class (cls "ml-3 flex items-center gap-2 font-bold text-lg"
                      "tracking-tight" t/text-accent)}
    [:span {:class "inline-block w-2.5 h-2.5 rounded-full bg-current"}]
    (or active-label "Easy CV")]
   [:div {:class "flex-grow h-full flex items-center justify-end gap-2"
          :ref #(rf/dispatch [::set-top-bar-ref %])}
    (when help-href
      [common/help-button
       {:href help-href
        :title "Documentation"
        :class "ml-2"}])]])
