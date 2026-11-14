(ns easy-cv.workers.compute
  "Compute Web Worker offloading fake archive generation, solving, and bench."
  (:require [easy-cv.core.db :as db]
            [easy-cv.core.generators :as gen]
            [easy-cv.core.solver :as solver]
            [easy-cv.utils.worker :as worker]))

(defn- now-ms
  []
  (if (and (exists? js/performance) (.-now js/performance))
    (.now js/performance)
    (js/Date.now)))

(defn- percentile
  [sorted-vals p]
  (let [n (count sorted-vals)]
    (if (zero? n)
      0.0
      (nth sorted-vals (min (dec n) (int (* p n)))))))

(defn- run-bench-op
  [{:bench/keys [tier fact-iters] :or {tier :junior fact-iters 20}}]
  (let [archive (gen/generate-archive tier {:seed 42 :size 20})
        dval (db/create-db (:archive/tx-data archive))
        cv-id (:cv/id (first (:archive/configs archive)))
        timings (mapv (fn [_]
                        (let [t0 (now-ms)]
                          (solver/solve-cv dval cv-id)
                          (- (now-ms) t0)))
                      (range fact-iters))
        ordered (vec (sort timings))]
    {:bench/iters fact-iters
     :bench/p50 (percentile ordered 0.50)
     :bench/p95 (percentile ordered 0.95)
     :bench/p99 (percentile ordered 0.99)}))

(def ^:private op-table
  {:compute/generate-archive
   (fn [{:compute/keys [tier seed size] :or {tier :student seed 42 size 20}}]
     (gen/generate-archive tier {:seed seed :size size}))
   :compute/solve-all
   (fn [{:compute/keys [facts]}]
     {:compute/cvs (solver/solve-all (db/create-db facts))})
   :compute/run-bench
   (fn [payload]
     (run-bench-op payload))})

(defn- dispatch-op!
  [op payload]
  (if-let [op-fn (get op-table op)]
    (op-fn payload)
    {:compute/error (str "Unknown compute op: " op)}))

(defn- post-reply!
  [msg]
  (.postMessage js/self (worker/transit-encode msg)))

(defn- on-message!
  [e]
  (let [msg (worker/transit-decode (.-data e))
        op (:msg/op msg)
        req-id (:msg/id msg)]
    (when-not (= :worker/cancel op)
      (let [res (try
                  (dispatch-op! op (:msg/payload msg))
                  (catch :default err
                    {:compute/error (.-message err)}))]
        (post-reply! (worker/reply-envelope req-id op res))))))

(defn ^:export init
  "Initializes the compute Web Worker and posts readiness signal."
  []
  (.addEventListener js/self "message" on-message!)
  (post-reply! (worker/make-envelope :compute/ready {:worker/kind :compute})))
