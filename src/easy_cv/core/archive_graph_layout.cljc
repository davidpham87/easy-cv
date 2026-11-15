(ns easy-cv.core.archive-graph-layout
  "Pure .cljc deterministic coordinate layout for archive network graphs.")

(def cluster-anchors
  "Target [x y] cluster centroids per entity kind in 760x380 viewBox."
  {:person [380.0 190.0]
   :cv/config [135.0 90.0]
   :education [135.0 215.0]
   :volunteer [135.0 315.0]
   :work [255.0 275.0]
   :org [390.0 320.0]
   :hobby [380.0 66.0]
   :project [545.0 105.0]
   :skill [650.0 200.0]
   :document [555.0 295.0]
   :other [655.0 325.0]})

(def radial-rings
  "Concentric kind rings from center outward for :radial layout."
  [#{:person}
   #{:cv/config :org}
   #{:work :education :volunteer :project}
   #{:skill :document :hobby :other}])

(defn node-radius
  "Choose SVG circle radius based on entity kind."
  [kind]
  (case kind
    :person 18.0
    :cv/config 16.0
    (:work :education :volunteer :project :org) 13.0
    11.0))

(defn clamp
  "Clamp numeric x to [lo hi]."
  [x lo hi]
  (double (max lo (min hi x))))

(defn init-cluster-pos
  "Assign deterministic initial (x, y) near the node's kind cluster anchor."
  [idx total [cx cy]]
  (if (<= total 1)
    [(clamp cx 42.0 718.0) (clamp cy 38.0 342.0)]
    (let [ang (* idx 2.399963)
          rad (+ 20.0 (* 13.0 (Math/sqrt idx)))]
      [(clamp (+ cx (* rad (Math/cos ang))) 42.0 718.0)
       (clamp (+ cy (* 0.8 rad (Math/sin ang))) 38.0 342.0)])))

(defn init-clustered-coords
  "Compute initial clustered coordinates keyed by :node/id."
  [nodes]
  (let [grouped (group-by :node/kind nodes)]
    (into {}
          (mapcat (fn [[k grp]]
                    (let [sgrp (sort-by :node/id grp)
                          tot (count sgrp)
                          anchor (get cluster-anchors k [380.0 190.0])]
                      (map-indexed
                       (fn [i n]
                         [(:node/id n) (init-cluster-pos i tot anchor)])
                       sgrp)))
                  grouped))))

(defn repulse-step
  "Compute deterministic pairwise repulsion force on node id at pos."
  [nid [x y] coords]
  (reduce-kv
   (fn [[fx fy] oid [ox oy]]
     (if (= nid oid)
       [fx fy]
       (let [dx (- x ox)
             dy (- y oy)
             d2 (+ (* dx dx) (* dy dy))
             d (Math/sqrt (max d2 1.0))]
         (if (< d 46.0)
           (let [push (/ (* 0.45 (- 46.0 d)) d)]
             [(+ fx (* dx push)) (+ fy (* dy push))])
           [fx fy]))))
   [0.0 0.0]
   coords))

(defn relax-step
  "Apply one repulsion and anchor-pull step over coords map."
  [anchors coords]
  (into {}
        (map (fn [[nid [x y]]]
               (let [[rx ry] (repulse-step nid [x y] coords)
                     [ax ay] (get anchors nid [380.0 190.0])
                     nx (+ x rx (* 0.08 (- ax x)))
                     ny (+ y ry (* 0.08 (- ay y)))]
                 [nid [(clamp nx 42.0 718.0) (clamp ny 38.0 342.0)]]))
             coords)))

(defn relax-coords
  "Run deterministic repulsion + anchor pull iterations over coords map."
  [nodes coords]
  (let [anchors (into {} (map (fn [n]
                                [(:node/id n)
                                 (get cluster-anchors (:node/kind n)
                                      [380.0 190.0])])
                              nodes))]
    (nth (iterate #(relax-step anchors %) coords) 12)))

(defn radial-ring-coords
  "Place one concentric ring of nodes evenly around (380, 190)."
  [ring-idx grp]
  (let [sgrp (sort-by :node/id grp)
        cnt (count sgrp)
        rx (* ring-idx 98.0)
        ry (* ring-idx 48.0)]
    (map-indexed
     (fn [i n]
       (let [ang (if (zero? ring-idx)
                   0.0
                   (+ (* 0.4 ring-idx) (/ (* 2.0 Math/PI i) (max cnt 1))))
             r-fac (if (and (zero? ring-idx) (> cnt 1)) 24.0 1.0)
             x (+ 380.0 (* (if (zero? ring-idx) r-fac rx) (Math/cos ang)))
             y (+ 190.0 (* (if (zero? ring-idx) r-fac ry) (Math/sin ang)))]
         [(:node/id n) [(clamp x 42.0 718.0) (clamp y 38.0 342.0)]]))
     sgrp)))

(defn layout-coords
  "Compute deterministic {node-id [x y]} map for :clustered or :radial."
  [nodes layout]
  (if (= :radial layout)
    (into {}
          (mapcat (fn [[idx kind-set]]
                    (let [grp (filter #(contains? kind-set (:node/kind %))
                                      nodes)]
                      (radial-ring-coords idx grp)))
                  (map-indexed vector radial-rings)))
    (relax-coords nodes (init-clustered-coords nodes))))

(defn apply-node-coords
  "Attach :node/x and :node/y from coords map onto sorted nodes."
  [nodes coords]
  (->> nodes
       (mapv (fn [n]
               (let [[x y] (get coords (:node/id n) [380.0 190.0])]
                 (assoc n :node/x (double x) :node/y (double y)))))
       (sort-by :node/id)
       vec))

(defn attach-edge-coords
  "Attach endpoint coordinates and :edge/highlighted? onto edges."
  [edges node-map sel-str]
  (mapv (fn [e]
          (let [a (get node-map (:edge/from e))
                b (get node-map (:edge/to e))]
            (assoc e
                   :edge/x1 (double (:node/x a))
                   :edge/y1 (double (:node/y a))
                   :edge/x2 (double (:node/x b))
                   :edge/y2 (double (:node/y b))
                   :edge/highlighted?
                   (boolean (and sel-str
                                 (or (= sel-str (:edge/from e))
                                     (= sel-str (:edge/to e))))))))
        edges))

(defn filter-visible-edges
  "Keep only edges whose both endpoints are in vis-nodes."
  [edges vis-nodes]
  (let [vis-ids (set (map :node/id vis-nodes))]
    (filterv #(and (contains? vis-ids (:edge/from %))
                   (contains? vis-ids (:edge/to %)))
             edges)))
