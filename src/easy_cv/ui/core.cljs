(ns easy-cv.ui.core
  "Root browser entrypoint for Easy CV."
  (:require [reagent.dom.client :as rdomc]))

(defonce ^:private root-atom (atom nil))

(defn root-view
  "Minimal placeholder root component for Phase 0."
  []
  [:main {:class "min-h-screen bg-slate-50 text-slate-900 p-8"}
   [:h1 {:class "text-2xl font-bold tracking-tight"} "Easy CV"]
   [:p {:class "mt-2 text-sm text-slate-600"}
    "Local-first CV fact archive and renderer."]])

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
  (mount-root))
