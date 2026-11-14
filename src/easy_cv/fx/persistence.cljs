(ns easy-cv.fx.persistence
  "Re-frame effect handlers for worker RPC, persistence, and downloads."
  (:require [clojure.core.async :as a]
            [easy-cv.utils.worker :as worker]
            [re-frame.core :as rf]))

(defonce ^:private bus-atom (atom nil))

(def ^:private default-worker-url "/js/persistence-worker.js")

(defn download-file!
  "Triggers a browser file download from string or byte-array content."
  [{:io/keys [content filename mime-type]
    :or {mime-type "application/json"}}]
  (let [blob (js/Blob. #js [content] #js {"type" mime-type})
        url (js/URL.createObjectURL blob)
        anchor (js/document.createElement "a")]
    (js/Object.assign anchor #js {"href" url "download" filename})
    (js/document.body.appendChild anchor)
    (.click anchor)
    (js/document.body.removeChild anchor)
    (js/URL.revokeObjectURL url)))

(defn init-persistence!
  "Starts or restarts the persistence Web Worker RPC bus."
  [{:persistence/keys [worker-url factory on-ready on-event]
    :or {worker-url default-worker-url}}]
  (when-let [prev @bus-atom]
    (worker/terminate! prev))
  (let [dispatch-ev! (fn [msg]
                       (let [op (:msg/op msg)]
                         (cond
                           (and (= :persistence/ready op) on-ready)
                           (rf/dispatch (conj on-ready (:msg/payload msg)))
                           on-event
                           (rf/dispatch (conj on-event msg)))))
        bus (worker/create-rpc-bus!
             worker-url
             (cond-> {:worker/on-event dispatch-ev!}
               factory (assoc :worker/factory factory)))]
    (reset! bus-atom bus)
    bus))

(defn send-worker-fx!
  "Dispatches a correlated RPC request to the active persistence worker."
  [{:worker/keys [op payload on-success on-error timeout-ms]
    :or {timeout-ms 10000}}]
  (if-let [bus @bus-atom]
    (let [reply-ch (worker/request! bus op payload timeout-ms)]
      (a/go
        (let [reply (a/<! reply-ch)
              res-payload (:msg/payload reply)
              err? (or (contains? #{:worker/timeout :worker/error
                                    :worker/cancelled :worker/terminated}
                                  (:msg/op reply))
                       (contains? res-payload :persistence/error))]
          (if err?
            (when on-error (rf/dispatch (conj on-error reply)))
            (when on-success (rf/dispatch (conj on-success res-payload)))))))
    (when on-error
      (rf/dispatch
       (conj on-error {:msg/op :worker/not-initialized})))))

(rf/reg-fx :persistence/init init-persistence!)
(rf/reg-fx :worker/send send-worker-fx!)
(rf/reg-fx :io/download-file download-file!)
