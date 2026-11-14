(ns easy-cv.fx.datascript
  "Reactive DataScript connection, subscriptions, and Re-frame effect handlers."
  (:require [datascript.core :as d]
            [easy-cv.core.db :as db]
            [easy-cv.core.generators :as gen]
            [easy-cv.core.schema :as schema]
            [re-frame.core :as rf]
            [reagent.core :as r]))

(defonce conn
  (d/create-conn schema/ds-schema))

(defonce ds-version
  (r/atom 0))

(defonce version-listener
  (d/listen! conn ::ds-version (fn [_] (swap! ds-version inc))))

(def ^:private ref-attrs
  #{:history/organization :project/skills :cv/person :cv/history
    :cv/skills :cv/projects :cv/documents :cv/hobbies})

(defn- uuid->ref
  [v]
  (cond
    (uuid? v) [:item/uuid v]
    (sequential? v) (mapv #(if (uuid? %) [:item/uuid %] %) v)
    :else v))

(defn- normalize-tx-item
  [item]
  (if (map? item)
    (reduce-kv (fn [acc k v]
                 (assoc acc k (if (contains? ref-attrs k)
                                (uuid->ref v)
                                v)))
               {}
               item)
    item))

(defn transact-fx!
  "Applies normalized transaction data to conn."
  [tx-data]
  (when (seq tx-data)
    (d/transact! conn (mapv normalize-tx-item tx-data))))

(defn restore-snapshot-fx!
  "Restores conn to an epochal snapshot value."
  [snapshot]
  (when snapshot
    (d/reset-conn! conn (db/restore-snapshot snapshot))))

(defn seed-demo-archive!
  "Seeds conn with a deterministic :student demo archive when empty."
  ([] (seed-demo-archive! {}))
  ([{:keys [tier seed size]
     :or {tier :student seed 42 size 20}}]
   (when (empty? (db/all-facts @conn))
     (let [archive (gen/generate-archive tier {:seed seed :size size})]
       (transact-fx! (:archive/tx-data archive))
       archive))))

(rf/reg-sub-raw
 ::ds-version
 (fn [_ _]
   (r/reaction @ds-version)))

(rf/reg-sub
 ::db
 :<- [::ds-version]
 (fn [_ _]
   @conn))

(rf/reg-fx :ds/transact transact-fx!)
(rf/reg-fx :ds/restore-snapshot restore-snapshot-fx!)
