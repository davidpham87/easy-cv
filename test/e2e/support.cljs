(ns e2e.support
  "Self-contained Node static server and Playwright browser runner for E2E."
  (:require ["fs" :as fs]
            ["http" :as http]
            ["os" :as os]
            ["path" :as path]
            ["playwright" :refer [chromium]]
            [clojure.string :as str]
            [malli.core :as m]))

(def ^:private mime-types
  {".html" "text/html; charset=utf-8"
   ".js" "application/javascript; charset=utf-8"
   ".css" "text/css; charset=utf-8"
   ".json" "application/json; charset=utf-8"
   ".wasm" "application/wasm"
   ".svg" "image/svg+xml"})

(def E2EPageResult
  "Malli schema for structured E2E page state returned by tests."
  [:map
   [:before :string]
   [:after :string]])

(defn mime-for
  "Returns HTTP Content-Type for a file extension."
  [file-path]
  (get mime-types (path/extname file-path) "application/octet-stream"))

(defn safe-resolve
  "Maps a URL path under root without permitting directory traversal."
  [root raw-url]
  (let [clean (-> (or raw-url "/") (str/split #"\?") first)
        rel (if (= "/" clean) "index.html" (str/replace clean #"^/" ""))
        base (path/resolve root)
        full (path/resolve base rel)]
    (when (or (= full base) (str/starts-with? full (str base path/sep)))
      full)))

(defn- send-response!
  "Writes an HTTP response with COOP/COEP headers for WASM/workers."
  [^js res status ctype body]
  (.writeHead res status
              #js {"Content-Type" ctype
                   "Cross-Origin-Opener-Policy" "same-origin"
                   "Cross-Origin-Embedder-Policy" "require-corp"})
  (.end res body))

(defn- serve-request!
  "Handles one static HTTP request under root."
  [root ^js req ^js res]
  (if-let [full (safe-resolve root (.-url req))]
    (fs/readFile full
                 (fn [err data]
                   (if err
                     (send-response! res 404 "text/plain" "Not found")
                     (send-response! res 200 (mime-for full) data))))
    (send-response! res 403 "text/plain" "Forbidden")))

(defn start-server!
  "Starts an ephemeral HTTP server serving public/ and resolves {:server :url}."
  ([] (start-server! "public"))
  ([root]
   (js/Promise.
    (fn [resolve reject]
      (let [^js srv (http/createServer #(serve-request! root %1 %2))]
        (.on srv "error" reject)
        (.listen srv 0 "127.0.0.1"
                 (fn []
                   (let [port (.-port (.address srv))]
                     (resolve {:server srv
                               :url (str "http://127.0.0.1:" port)})))))))))

(defn stop-server!
  "Closes the ephemeral HTTP server."
  [^js srv]
  (js/Promise.
   (fn [resolve _]
     (if srv (.close srv #(resolve nil)) (resolve nil)))))

(defn- cached-chromium-candidates
  "Lists candidate cached Playwright Chromium executables on macOS/Linux."
  []
  (let [caches [(path/join (os/homedir) "Library/Caches/ms-playwright")
                (path/join (os/homedir) ".cache/ms-playwright")]
        mac-sub "chrome-mac-arm64/Google Chrome for Testing.app/Contents/MacOS"
        mac-bin "Google Chrome for Testing"]
    (for [dir caches
          :when (fs/existsSync dir)
          entry (sort (js->clj (fs/readdirSync dir)))
          :when (str/starts-with? entry "chromium-")
          :let [p1 (path/join dir entry mac-sub mac-bin)
                p2 (path/join dir entry "chrome-linux/chrome")]]
      (if (fs/existsSync p1) p1 p2))))

(defn resolve-chromium-exe
  "Returns Playwright's executablePath if present, else a cached binary."
  []
  (let [^js browser-type chromium
        default-exe (.executablePath browser-type)]
    (if (and default-exe (fs/existsSync default-exe))
      default-exe
      (first (filter fs/existsSync (cached-chromium-candidates))))))

(defn launch-browser!
  "Launches headless Chromium via Playwright."
  []
  (let [^js browser-type chromium
        exe (resolve-chromium-exe)
        opts (cond-> {:headless true} exe (assoc :executablePath exe))]
    (.launch browser-type (clj->js opts))))

(defn- cleanup!
  "Closes Playwright browser and HTTP server."
  [^js browser ^js srv]
  (.then (if browser (.close browser) (js/Promise.resolve nil))
         #(stop-server! srv)))

(defn valid-page-result?
  "Validates E2E result map against E2EPageResult Malli schema."
  [x]
  (m/validate E2EPageResult x))

(defn with-app-page
  "Runs async test-fn [page url errors] against live Easy CV and cleans up."
  [test-fn]
  (let [state (atom {})
        errors (atom [])]
    (-> (start-server!)
        (.then (fn [{:keys [server url]}]
                 (swap! state assoc :server server :url url)
                 (launch-browser!)))
        (.then (fn [^js browser]
                 (swap! state assoc :browser browser)
                 (.newPage browser)))
        (.then (fn [^js page]
                 (.on page "pageerror" #(swap! errors conj %))
                 (test-fn page (:url @state) errors)))
        (.finally #(cleanup! (:browser @state) (:server @state))))))
