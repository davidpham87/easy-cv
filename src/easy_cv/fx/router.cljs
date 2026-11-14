(ns easy-cv.fx.router
  "Reitit fragment routing and isolated [:router] Re-frame slice."
  (:require [re-frame.core :as rf]
            [reitit.frontend :as rfront]
            [reitit.frontend.easy :as rfe]))

(defn- route-node
  [path route-kw]
  [path (hash-map :name route-kw)])

(def routes
  "SPA route table supporting fragment navigation."
  ["/"
   (route-node "" :home)
   (route-node "archive" :archive)
   (route-node "solver" :solver)
   (route-node "styles" :styles)
   (route-node "preview" :preview)
   (route-node "editor" :editor)
   (route-node "io" :io)])

(def router
  "Compiled Reitit frontend router."
  (rfront/router routes))

(defn match-path
  "Purely matches a URL path string against the SPA router."
  [path]
  (rfront/match-by-path router path))

(defn on-navigate
  "Dispatches ::navigated when browser URL fragment changes."
  [match _]
  (rf/dispatch [::navigated match]))

(defn init-routes!
  "Starts Reitit fragment history listener in browser environments."
  []
  (when (exists? js/window)
    (rfe/start! router on-navigate {:use-fragment true})))

(rf/reg-event-db
 ::navigated
 (fn [db [_ match]]
   (assoc-in db [:router :current-route] match)))

(rf/reg-event-fx
 ::navigate
 (fn [_ [_ route-name params query]]
   {:router/navigate [route-name params query]}))

(rf/reg-fx
 :router/navigate
 (fn [[route-name params query]]
   (when (exists? js/window)
     (rfe/push-state route-name params query))))

(rf/reg-sub
 ::current-route
 (fn [db _]
   (get-in db [:router :current-route])))

(rf/reg-sub
 ::route-name
 :<- [::current-route]
 (fn [match _]
   (let [kw (get-in match [:data :name])]
     (if (or (nil? kw) (= kw :home))
       :archive
       kw))))
