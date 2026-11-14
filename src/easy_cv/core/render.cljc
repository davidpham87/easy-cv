(ns easy-cv.core.render
  "Open polymorphic multi-format CV renderer dispatching on target format."
  (:require [easy-cv.core.io :as io]
            [easy-cv.core.render.html :as html]
            [easy-cv.core.render.latex :as latex]
            [easy-cv.core.render.markdown :as markdown]
            [easy-cv.core.render.site :as site]))

(defmulti render-cv
  "Render a CanonicalCV to fmt (:html, :site, :latex, :markdown, :json-ld)."
  (fn [fmt _canonical _opts] fmt))

(defmethod render-cv :html
  [_ canonical opts]
  (html/render-html canonical opts))

(defmethod render-cv :site
  [_ canonical opts]
  (site/render-site canonical opts))

(defmethod render-cv :latex
  [_ canonical opts]
  (latex/render-latex canonical opts))

(defmethod render-cv :markdown
  [_ canonical opts]
  (markdown/render-markdown canonical opts))

(defmethod render-cv :json-ld
  [_ canonical opts]
  (io/export-output :json-ld canonical opts))

(defmethod render-cv :default
  [fmt _ _]
  (throw (ex-info (str "Unsupported render format: " fmt)
                  {:render/format fmt})))

(defn render
  "Convenience wrapper calling render-cv with default empty opts map."
  ([fmt canonical] (render-cv fmt canonical {}))
  ([fmt canonical opts] (render-cv fmt canonical opts)))
