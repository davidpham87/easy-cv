(ns easy-cv.bench
  "Benchmark suite reporting p50/p95/p99 for db, solve-cv, and HoneySQL."
  (:require [easy-cv.core.db :as db]
            [easy-cv.core.generators :as gen]
            [easy-cv.core.honeysql :as hsql]
            [easy-cv.core.solver :as solver]))

(defn- now-ms
  []
  #?(:clj (/ (double (System/nanoTime)) 1e6)
     :cljs (if (and (exists? js/performance) (.-now js/performance))
             (.now js/performance)
             (js/Date.now))))

(defn- percentile
  [sorted-vals p]
  (let [n (count sorted-vals)]
    (if (zero? n)
      0.0
      (nth sorted-vals (min (dec n) (int (* p n)))))))

(defn- summarize-timings
  [label timings]
  (let [ordered (vec (sort timings))]
    {:bench/label label
     :bench/runs (count ordered)
     :bench/p50 (percentile ordered 0.50)
     :bench/p95 (percentile ordered 0.95)
     :bench/p99 (percentile ordered 0.99)}))

(defn- clone-fact-batch
  [base-facts batch-idx]
  (let [non-person (remove #(or (contains? % :person/email)
                                (contains? % :cv/id))
                           base-facts)]
    (map-indexed
     (fn [idx f]
       (cond-> (assoc f :item/uuid (random-uuid))
         (:skill/name f)
         (assoc :skill/name (str (:skill/name f) "-" batch-idx "-" idx))
         (:document/citekey f)
         (assoc :document/citekey
                (str (:document/citekey f) "-" batch-idx "-" idx))))
     non-person)))

(defn build-scaled-archive
  "Builds tx-data with at least target-count facts plus person and CVConfig."
  [target-count]
  (let [base (gen/generate-archive :senior {:seed 101 :size 20})
        person (:archive/person base)
        cfg (first (:archive/configs base))
        seed-tx (:archive/tx-data base)]
    (loop [acc (vec seed-tx)
           batch 0]
      (if (>= (count acc) target-count)
        {:bench/tx-data (vec (take target-count acc))
         :bench/cv-id (:cv/id cfg)
         :bench/person person
         :bench/cfg cfg}
        (recur (into acc (clone-fact-batch seed-tx batch))
               (inc batch))))))

(defn bench-solve-scale
  "Measures db creation and solve-cv p50/p95/p99 for target-count facts."
  ([target-count] (bench-solve-scale target-count 15))
  ([target-count iters]
   (let [{:bench/keys [tx-data cv-id person cfg]}
         (build-scaled-archive target-count)
         full-tx (vec (distinct (concat [person cfg] tx-data)))
         dval (db/create-db full-tx)
         timings (mapv (fn [_]
                         (let [t0 (now-ms)]
                           (solver/solve-cv dval cv-id)
                           (- (now-ms) t0)))
                       (range iters))]
     (assoc (summarize-timings (str "solve-" target-count) timings)
            :bench/fact-count target-count))))

(defn bench-honeysql-batch
  "Measures HoneySQL batch query generation p50/p95/p99 for n facts."
  ([n-facts] (bench-honeysql-batch n-facts 15))
  ([n-facts iters]
   (let [rows (mapv (fn [idx]
                      {:fact/uuid (str "u-" idx)
                       :fact/entity-type :history
                       :fact/attrs-transit "{}"
                       :fact/updated-at idx
                       :fact/archived 0})
                    (range n-facts))
         timings (mapv (fn [_]
                         (let [t0 (now-ms)]
                           (mapv hsql/upsert-fact-query rows)
                           (- (now-ms) t0)))
                       (range iters))]
     (summarize-timings (str "honeysql-" n-facts) timings))))

(defn run-suite
  "Runs the full solver (50/200/500 facts) and HoneySQL benchmark suite."
  ([] (run-suite 15))
  ([iters]
   {:bench/solve [(bench-solve-scale 50 iters)
                  (bench-solve-scale 200 iters)
                  (bench-solve-scale 500 iters)]
    :bench/honeysql (bench-honeysql-batch 500 iters)}))

(defn -main
  "CLI entrypoint printing formatted p50/p95/p99 benchmark results."
  [& _]
  (println (pr-str (run-suite 15))))
