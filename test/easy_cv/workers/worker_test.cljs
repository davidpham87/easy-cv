(ns easy-cv.workers.worker-test
  (:require [clojure.core.async :as a]
            [clojure.test :refer [async deftest is testing]]
            [clojure.test.check.clojure-test :refer [defspec]]
            [clojure.test.check.generators :as gen]
            [clojure.test.check.properties :as prop]
            [easy-cv.fx.persistence :as fx-p]
            [easy-cv.utils.worker :as worker]
            [re-frame.core :as rf]))

(defn- make-mock-worker!
  "Creates an in-memory mock js/Worker supporting addEventListener."
  [on-post]
  (let [listeners (atom {"message" [] "error" []})
        terminated? (atom false)
        emit! (fn [ev-type payload]
                (doseq [cb (get @listeners ev-type)]
                  (cb payload)))]
    {:mock/emit! emit!
     :mock/terminated? terminated?
     :mock/worker
     #js {"addEventListener"
          (fn [ev-type cb]
            (swap! listeners update ev-type (fnil conj []) cb))
          "postMessage"
          (fn [raw]
            (on-post (worker/transit-decode raw)
                     (fn [resp]
                       (emit! "message"
                              #js {"data" (worker/transit-encode resp)}))))
          "terminate"
          (fn [] (reset! terminated? true))}}))

(defspec transit-envelope-round-trip-test 40
  (testing "Transit JSON round-trips RPC envelopes losslessly"
    (prop/for-all [op (gen/elements [:persistence/upsert-fact
                                     :persistence/load-all
                                     :persistence/export-sql-json])
                   uuid gen/uuid
                   title gen/string-ascii]
                  (let [env (worker/make-envelope
                             op {:fact/uuid uuid :cv/title title})
                        decoded (-> env
                                    worker/transit-encode
                                    worker/transit-decode)]
                    (= env decoded)))))

(deftest rpc-bus-concurrent-correlation-test
  (testing "RPC bus correlates out-of-order replies and handles events"
    (async done
           (let [events (atom [])
                 inbox (atom [])
                 mock (make-mock-worker!
                       (fn [msg send!]
                         (swap! inbox conj [msg send!])))
                 bus (worker/create-rpc-bus!
                      "/mock.js"
                      {:worker/factory (fn [_] (:mock/worker mock))
                       :worker/on-event #(swap! events conj %)})
                 ch1 (worker/request! bus :op/first {:seq/n 1} 2000)
                 ch2 (worker/request! bus :op/second {:seq/n 2} 2000)]
             (a/go
               (let [[m1 s1] (first @inbox)
                     [m2 s2] (second @inbox)]
                 ((:mock/emit! mock)
                  "message"
                  #js {"data" (worker/transit-encode
                               (worker/make-envelope :persistence/ready {}))})
                 (s2 (worker/reply-envelope (:msg/id m2) :op/second {:ok 2}))
                 (s1 (worker/reply-envelope (:msg/id m1) :op/first {:ok 1}))
                 (let [r1 (a/<! ch1)
                       r2 (a/<! ch2)]
                   (is (= {:ok 1} (:msg/payload r1)))
                   (is (= {:ok 2} (:msg/payload r2)))
                   (is (= [:persistence/ready] (map :msg/op @events)))
                   (worker/terminate! bus)
                   (is (true? @(:mock/terminated? mock)))
                   (done))))))))

(deftest rpc-bus-timeout-and-cancel-test
  (testing "RPC bus times out unresponsive requests and supports cancel!"
    (async done
           (let [sent-ids (atom [])
                 cancelled (atom [])
                 mock (make-mock-worker!
                       (fn [msg _]
                         (if (= :worker/cancel (:msg/op msg))
                           (swap! cancelled conj msg)
                           (swap! sent-ids conj (:msg/id msg)))))
                 bus (worker/create-rpc-bus!
                      "/mock.js"
                      {:worker/factory (fn [_] (:mock/worker mock))})
                 ch-timeout (worker/request! bus :op/slow {} 30)
                 ch-cancel (worker/request! bus :op/cancel-me {} 2000)]
             (worker/cancel! bus (second @sent-ids))
             (a/go
               (let [r-cancel (a/<! ch-cancel)
                     r-timeout (a/<! ch-timeout)]
                 (is (= :worker/cancelled (:msg/op r-cancel)))
                 (is (= :worker/timeout (:msg/op r-timeout)))
                 (is (= 1 (count @cancelled)))
                 (worker/terminate! bus)
                 (done)))))))

(deftest fx-persistence-dispatch-test
  (testing "fx.persistence initializes bus and routes RPC replies"
    (async done
           (let [got (atom nil)
                 mock (make-mock-worker!
                       (fn [msg send!]
                         (send! (worker/reply-envelope
                                 (:msg/id msg)
                                 (:msg/op msg)
                                 {:persistence/ok true}))))]
             (rf/reg-event-db
              ::fx-ok
              (fn [db [_ res]]
                (reset! got res)
                (is (= {:persistence/ok true} @got))
                (done)
                db))
             (fx-p/init-persistence!
              {:persistence/factory (fn [_] (:mock/worker mock))})
             (fx-p/send-worker-fx!
              {:worker/op :persistence/upsert-fact
               :worker/payload {:fact/uuid "u1"}
               :worker/on-success [::fx-ok]})))))
