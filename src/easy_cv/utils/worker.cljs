(ns easy-cv.utils.worker
  "Transit JSON and core.async RPC worker bus with correlation IDs."
  (:require [clojure.core.async :as a]
            [cognitect.transit :as t]))

(def ^:private default-timeout-ms 10000)

(defn transit-encode
  "Encodes Clojure data into a Transit JSON string."
  [x]
  (t/write (t/writer :json) x))

(defn transit-decode
  "Decodes a Transit JSON string into Clojure data."
  [s]
  (t/read (t/reader :json) s))

(defn make-envelope
  "Creates a correlated RPC request envelope."
  ([op payload]
   (make-envelope (str (random-uuid)) op payload))
  ([msg-id op payload]
   {:msg/id msg-id
    :msg/op op
    :msg/payload payload
    :msg/reply-to nil}))

(defn reply-envelope
  "Creates a correlated RPC response envelope."
  [reply-to op payload]
  {:msg/id (str (random-uuid))
   :msg/op op
   :msg/payload payload
   :msg/reply-to reply-to})

(defn- decode-event
  [e]
  (transit-decode (.-data e)))

(defn- pop-pending!
  [pending-atom msg-id]
  (let [[prev _] (swap-vals! pending-atom dissoc msg-id)]
    (get prev msg-id)))

(defn- deliver-reply!
  [pending-atom on-event msg]
  (if-let [reply-id (:msg/reply-to msg)]
    (when-let [ch (pop-pending! pending-atom reply-id)]
      (a/put! ch msg)
      (a/close! ch))
    (when on-event
      (on-event msg))))

(defn- fail-all-pending!
  [pending-atom err-msg]
  (let [[prev _] (swap-vals! pending-atom (constantly {}))]
    (doseq [[_ ch] prev]
      (a/put! ch err-msg)
      (a/close! ch))))

(defn- attach-message-listener!
  [w on-message out-chan]
  (.addEventListener
   w "message"
   (fn [e]
     (let [msg (decode-event e)]
       (if on-message (on-message msg) (a/put! out-chan msg))))))

(defn- attach-error-listener!
  [w on-error out-chan]
  (.addEventListener
   w "error"
   (fn [e]
     (let [err {:msg/op :worker/error
                :msg/payload {:error/message (.-message e)}}]
       (if on-error
         (on-error err)
         (when out-chan (a/put! out-chan err)))))))

(defn create-worker!
  "Spawns a Web Worker from url (or custom :worker/factory) with Transit."
  ([url]
   (create-worker! url {}))
  ([url {:worker/keys [factory on-message on-error]
         :or {factory #(js/Worker. %)}}]
   (let [w (factory url)
         out-chan (when-not on-message (a/chan 16))]
     (attach-message-listener! w on-message out-chan)
     (attach-error-listener! w on-error out-chan)
     (cond-> {:worker/instance w}
       out-chan (assoc :worker/out-chan out-chan)))))

(defn post-message!
  "Posts a Transit-encoded message to the worker."
  [{:worker/keys [instance]} message]
  (.postMessage instance (transit-encode message)))

(defn create-rpc-bus!
  "Creates a correlated RPC bus wrapping a Web Worker."
  [url {:worker/keys [factory on-event]}]
  (let [pending-atom (atom {})
        w-rec (create-worker!
               url
               {:worker/factory (or factory #(js/Worker. %))
                :worker/on-message #(deliver-reply! pending-atom on-event %)
                :worker/on-error #(fail-all-pending! pending-atom %)})]
    (assoc w-rec :worker/pending pending-atom)))

(defn request!
  "Sends an RPC request and returns a channel resolving to the reply."
  ([bus op payload]
   (request! bus op payload default-timeout-ms))
  ([{:worker/keys [pending] :as bus} op payload timeout-ms]
   (let [env (make-envelope op payload)
         msg-id (:msg/id env)
         reply-ch (a/chan 1)
         out-ch (a/chan 1)
         t-ch (a/timeout timeout-ms)]
     (swap! pending assoc msg-id reply-ch)
     (post-message! bus env)
     (a/go
       (let [[val port] (a/alts! [reply-ch t-ch])]
         (if (= port t-ch)
           (do (pop-pending! pending msg-id)
               (a/>! out-ch {:msg/op :worker/timeout :msg/reply-to msg-id}))
           (when val (a/>! out-ch val)))
         (a/close! out-ch)))
     out-ch)))

(defn cancel!
  "Cancels an in-flight RPC request and notifies the worker."
  [{:worker/keys [pending] :as bus} msg-id]
  (when-let [ch (pop-pending! pending msg-id)]
    (a/put! ch {:msg/op :worker/cancelled :msg/reply-to msg-id})
    (a/close! ch))
  (post-message! bus (make-envelope :worker/cancel {:msg/id msg-id})))

(defn terminate!
  "Terminates the worker and closes all pending RPC channels."
  [{:worker/keys [instance out-chan pending]}]
  (when pending
    (fail-all-pending! pending {:msg/op :worker/terminated}))
  (when out-chan
    (a/close! out-chan))
  (when instance
    (.terminate instance)))
