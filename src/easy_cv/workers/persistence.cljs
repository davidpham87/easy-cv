(ns easy-cv.workers.persistence
  "SQLite WASM persistence worker using OpfsDb with transient DB fallback."
  (:require ["@sqlite.org/sqlite-wasm" :default sqlite3InitModule]
            [easy-cv.core.honeysql :as hsql]
            [easy-cv.utils.worker :as worker]))

(defonce ^:private db-state (atom nil))

(defn- log!
  [& args]
  (apply js/console.log "[persistence-worker]" args))

(defn- log-error!
  [& args]
  (apply js/console.error "[persistence-worker]" args))

(defn- exec-sql!
  [^js db [sql-str & params]]
  (let [opts (cond-> {"sql" sql-str}
               (seq params) (assoc "bind" (vec params)))]
    (.exec db (clj->js opts))))

(defn- query-rows!
  [^js db [sql-str & params]]
  (let [opts (cond-> {"sql" sql-str
                      "rowMode" "object"
                      "returnValue" "resultRows"}
               (seq params) (assoc "bind" (vec params)))
        rows (.exec db (clj->js opts))]
    (js->clj rows :keywordize-keys true)))

(defn- ensure-tables!
  [db]
  (doseq [stmt (hsql/ddl-statements)]
    (exec-sql! db stmt)))

(defn- export-sqlite-bytes!
  [{:sqlite/keys [lib db]}]
  (let [capi (.-capi ^js lib)
        p-db (.-pointer ^js db)]
    (.sqlite3_js_db_export capi p-db)))

(defn- export-sql-json!
  [db exported-at]
  (hsql/db->sql-export #(query-rows! db %) exported-at))

(defn- load-all!
  [db opts]
  {:facts (query-rows! db (hsql/select-facts-query (or opts {})))
   :cv-configs (query-rows! db (hsql/select-cv-configs-query))
   :snapshots (query-rows! db (hsql/select-snapshots-query))})

(defn- exec-ok!
  [db query]
  (exec-sql! db query)
  {:persistence/ok true})

(def ^:private op-table
  {:persistence/upsert-fact
   (fn [{:sqlite/keys [db]} p] (exec-ok! db (hsql/upsert-fact-query p)))
   :persistence/delete-fact
   (fn [{:sqlite/keys [db]} p]
     (exec-ok! db (hsql/delete-fact-query (:fact/uuid p))))
   :persistence/upsert-cv-config
   (fn [{:sqlite/keys [db]} p] (exec-ok! db (hsql/upsert-cv-config-query p)))
   :persistence/save-snapshot
   (fn [{:sqlite/keys [db]} p] (exec-ok! db (hsql/insert-snapshot-query p)))
   :persistence/load-all
   (fn [{:sqlite/keys [db]} p] (load-all! db p))
   :persistence/export-sql-json
   (fn [{:sqlite/keys [db]} p] (export-sql-json! db (:exported-at p)))
   :persistence/export-sqlite
   (fn [state _] {:sqlite/bytes (vec (export-sqlite-bytes! state))})})

(defn- dispatch-op!
  [state op payload]
  (if-let [op-fn (get op-table op)]
    (op-fn state payload)
    {:persistence/error (str "Unknown persistence op: " op)}))

(defn- post-reply!
  [msg]
  (.postMessage js/self (worker/transit-encode msg)))

(defn- on-message!
  [e]
  (let [msg (worker/transit-decode (.-data e))
        op (:msg/op msg)
        req-id (:msg/id msg)]
    (when-not (= :worker/cancel op)
      (if-let [state @db-state]
        (let [res (try
                    (dispatch-op! state op (:msg/payload msg))
                    (catch :default err
                      {:persistence/error (.-message err)}))]
          (post-reply! (worker/reply-envelope req-id op res)))
        (post-reply!
         (worker/reply-envelope
          req-id op {:persistence/error "SQLite DB not initialized"}))))))

(defn- start-db!
  [^js sqlite3]
  (let [oo1 (.-oo1 sqlite3)
        opfs? (exists? (.-opfs oo1))
        db (if opfs?
             (new (.-OpfsDb oo1) "/easy-cv.sqlite3")
             (new (.-DB oo1) "/easy-cv.sqlite3" "ct"))
        tier (if opfs? :opfs :memory)]
    (ensure-tables! db)
    (reset! db-state {:sqlite/lib sqlite3
                      :sqlite/db db
                      :storage/tier tier})
    (log! "SQLite ready, tier:" (name tier)
          "version:" (.. sqlite3 -version -libVersion))
    (post-reply!
     (worker/make-envelope :persistence/ready {:storage/tier tier}))))

(defn ^:export init
  "Initializes SQLite WASM and registers the worker message listener."
  []
  (.addEventListener js/self "message" on-message!)
  (-> (sqlite3InitModule #js {"print" log! "printErr" log-error!})
      (.then start-db!)
      (.catch (fn [err]
                (log-error! "Init error:" (.-message err))
                (post-reply!
                 (worker/make-envelope
                  :persistence/init-error
                  {:error/message (.-message err)}))))))
