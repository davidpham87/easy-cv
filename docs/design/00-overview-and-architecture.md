# 00: System Overview and Architecture

Easy CV is a zero-backend, privacy-preserving ClojureScript (CLJS) SPA. All
fact authoring, DataScript querying, SQLite persistence, and document
rendering happen inside the user's browser.

## High-Level System Architecture

The system enforces a strict **pure core / imperative shell** boundary.
Pure `.cljc` namespaces handle parsing, validation, solving, HoneySQL AST
construction, and rendering so they run identically in JVM Clojure tests,
Babashka tasks, and browser/worker CLJS.

```text
+-------------------------------------------------------------------------+
|                        BROWSER MAIN THREAD (SPA)                        |
|  +------------------+   +--------------------+   +-------------------+  |
|  | Reagent UI Views |<--| Re-frame Subs DAG  |   | Reitit Router     |  |
|  | (Archive/Solver/ |   | (Layer 2 & 3)      |   | (Fragment hash)   |  |
|  |  Editor/Preview) |   +--------------------+   +-------------------+  |
|  +--------+---------+             ^                       |             |
|           | dispatch              | reactive              v             |
|  +--------v-----------------------+-----------------------+----------+  |
|  |           Re-frame Event Handlers + Partitioned :app-db           |  |
|  |  [:router :archive :solver :editor :render :ui :persistence]      |  |
|  +--------+------------------------------+---------------------------+  |
|           | pure return {:fx [...]}      | d/listen! -> ds-version      |
|  +--------v---------+           +--------v---------------------------+  |
|  | Re-frame Effect  |           | DataScript Conn (In-Memory EAV DB) |  |
|  | Handlers (fx/*)  |           | Immutable @conn snapshots over time|  |
|  +--------+---------+           +------------------------------------+  |
+-----------|-------------------------------------------------------------+
            | Transit JSON over postMessage (core.async bus)
     +------+-------------------------+--------------------------+
     v                                v                          v
+--------------------------+ +--------------------+ +---------------------+
| :persistence-worker      | | :compute-worker    | | :latex-worker       |
| @sqlite.org/sqlite-wasm  | | Fake CV generators | | BusyTeX/SwiftLaTeX  |
| + HoneySQL v2 + OPFS/IDB | | BibTeX/CSL parse   | | or Typst WASM       |
+--------------------------+ +--------------------+ +---------------------+
```

## Namespace and Directory Topology

Separating pure `.cljc` logic from browser-only `.cljs` code keeps the
test feedback loop sub-second on the JVM while enabling CLJS hot reload.

```text
easy-cv/
  public/
    index.html              # Moved from public/js/index.html; CSP + mount
    css/app.css             # Tailwind v4 @import + @source "../../src"
  src/easy_cv/
    core/                   # PURE .cljc (JVM + Babashka + CLJS + Worker)
      schema.cljc           # DataScript schema + Malli canonical IR
      schema_org.cljc       # Bidirectional Schema.org JSON-LD mapper
      honeysql.cljc         # Parameterized HoneySQL v2 DDL & queries
      io/                   # EDN, YAML, JSON-LD, BibTeX/CSL normalizers
      solver.cljc           # Deterministic CV selection & override solver
      generators.cljc       # Malli + test.check fake CV generators
      render/               # Pure IR -> HTML, LaTeX, Markdown, JSON
    fx/                     # IMPERATIVE SHELL (.cljs reg-fx handlers)
      worker_bus.cljs       # Transit + core.async worker request/reply
      persistence.cljs      # Snapshot sync, OPFS/IDB, SQL JSON download
      datascript.cljs       # conn atom, d/transact!, d/listen! bridge
      router.cljs           # Reitit push_state / fragment routing
    ui/                     # REAGENT + CO-LOCATED RE-FRAME (.cljs)
      core.cljs             # init, ^:dev/after-load reload!, root mount
      theme.cljs            # Semantic CSS var tokens + Tailwind maps
      components/           # Polymorphic CV blocks, common UI, editor
      views/                # archive, solver, editor, preview, settings
    workers/                # SHADOW-CLJS :web-worker MODULES (.cljs)
      persistence.cljs      # SQLite WASM + OPFS/opfs-sahpool + HoneySQL
      compute.cljs          # Generators, heavy parsing, benchmarks
      latex.cljs            # Optional LaTeX / Typst WASM compiler
  test/easy_cv/
    core/                   # JVM + CLJS property-based tests (test.check)
    ui/                     # day8.re-frame.test/run-test-sync specs
    workers/                # Mocked js/Worker + async SQLite WASM tests
```

## Re-frame Blast-Radius Isolation

To prevent a change in one feature from breaking unrelated views ("big
blast radius"), Easy CV enforces five Re-frame architectural rules:

1. **Partitioned `:app-db` Root**: Top-level keys are strictly owned by
   domain: `:router`, `:archive`, `:solver`, `:editor`, `:render`, `:ui`,
   and `:persistence`. A view may only write to its own domain key.
2. **Co-located Events and Subscriptions**: Following `bb-web-ds-tools`
   (`views/malli.cljs`, `views/honeysql.cljs`), each view namespace defines
   its own keyword-namespaced events (`::select-fact`) and subs.
3. **Layered Subscription DAG**:
   - **Layer 2** extractors pull a single domain slice (`:archive` or
     `::ds-version`) — never subscribe directly to the root `db`.
   - **Layer 3** derived subs combine Layer 2 signals via `:<-` inputs and
     delegate heavy computation to `easy_cv.core.*` pure functions.
4. **Pure Event Handlers, Explicit Effects**: Event handlers use
   `reg-event-fx` (or `reg-event-db`) and remain pure functions of
   `(cofx, event)`. Side effects (`d/transact!`, worker `postMessage`,
   `download-file`, routing) live exclusively in `easy_cv.fx.*` via
   `rf/reg-fx` and `rf/reg-cofx`.
5. **Cross-Domain Communication via Events Only**: Views never read another
   view's internal UI state directly; transitions happen via dispatched
   domain events or shared DataScript facts.

```text
User Click -> (rf/dispatch [::solver/toggle-fact uuid])
                   |
                   v  [Pure reg-event-fx]
          {:db (update-in db [:solver ...] ...)
           :fx [[:ds/transact tx-data]
                [:worker/send [:persistence {:op :save-snapshot}]]]}
                   |
                   +---> fx/datascript -> d/transact! -> d/listen!
                   |                          |
                   |                          v
                   |                  swap! ds-version inc
                   |                          |
                   |                          v
                   |            Layer 2/3 Subs -> Reagent re-render
                   |
                   +---> fx/worker_bus -> Transit postMessage -> Worker
                                                                    |
                   Callback Event <- rf/dispatch [:persistence/ok] <+
```

## Concrete Reuse from `../bb-web-ds-tools`

Easy CV adopts proven modules from `../bb-web-ds-tools` and fixes known
weak spots identified in the baseline audit:

- **`core.cljs`**: Reuse `init`, `reload!`, `routes`, `router`,
  `init-routes!`, `view` multimethod, `rdomc/create-root`, and
  `malli.registry/set-default-registry!` with
  `malli.experimental.time/schemas`.
- **`workspaces/core.cljs`**: Adopt `conn`, `ds-version` atom,
  `d/listen!`, and `reg-sub-raw ::ds-version` reactive bridge; keep
  `d/transact!` isolated inside `reg-fx`.
- **`workspaces/persistence_fx.cljs` & `workers/persistence.cljs`**:
  Reuse `transit-encode`, `transit-decode`, `sqlite3InitModule`, and
  `oo1.OpfsDb` / `oo1.DB` fallback. **Adapt**: replace raw SQL string
  concatenation in `persist-all!` with parameterized HoneySQL v2 inside
  the persistence worker.
- **`utils/worker.cljs`**: Reuse `create-worker`, `post-message`,
  `set-handler`, and `terminate` (Transit JSON + `core.async`), adding
  correlation IDs for request/reply RPC.
- **`components/malli.cljc`**: Reuse `detect-and-parse`, `generate-data`,
  `infer-schema`, `validate-data`, and `transform-to-json-schema`.
- **`components/honeysql.cljc`**: Reuse `honey.sql/format` for SQLite
  DDL, parameterized CRUD queries, and SQL-tables JSON export.
- **`utils/dataset_processing.cljs`**: Reuse `js-yaml` parsing and
  `camel-snake-kebab.core` (`->snake_case`, `->kebab-case`) helpers.
- **`utils/io.cljs`**: Reuse `download-file` (`Blob` +
  `createObjectURL`) for `.tex`, `.html`, `.json`, `.edn`, `.yaml`, and
  `.bib` downloads.
- **`theme.cljs`, `utils/themes.cljs`, `events/theme.cljs`**: Adapt
  `colors` CSS var map, `themes`, `get-theme`, `::set-theme`, and
  `:theme/apply` for both app chrome and CV layout/theme presets.
- **`components/common.cljs`, `layout.cljs`, `navigation.cljs`**: Reuse
  `button`, `card`, `tabs`, `split-view`, `page-container`, and
  `top-bar`; extend with polymorphic CV block renderers.
- **`components/editor.cljs`**: Reuse `monaco-editor` and
  `normalize-value` as the Monaco backend behind a polymorphic editor
  adapter (see [02-io-editors][doc-02]).

## Build, Hot Reload, and Browser Headers

### Resolving Existing Repo Mismatches

Before Phase 1 implementation, reconcile three baseline discrepancies:

1. **React version alignment**: Current `package.json` declares `react`
   `^19.2.0` while `reagent` `2.0.1` in `bb-web-ds-tools` pairs with
   `react` `^18.2.0` and `react-dom` `^18.2.0`. Pin `^18.2.0` unless
   React 19 compatibility is explicitly verified.
2. **shadow-cljs parity**: Align `deps.edn` (`thheller/shadow-cljs`
   `3.2.1`) and `package.json` (`^3.3.2`) to a single matching version.
3. **HTML entry location**: Move `public/js/index.html` to
   `public/index.html` so `:dev-http` serves `/` cleanly while compiled
   JS lives under `public/js/`.

### `deps.edn` and `package.json` Additions

Add missing CLJS/test libraries matching `bb-web-ds-tools`:
`datascript/datascript` (`1.5.4`), `com.cognitect/transit-cljs` (`0.8.280`),
`metosin/reitit` (`0.7.2`), `org.clojure/core.async` (`1.8.741`),
`org.clojure/test.check` (`1.1.1`), `day8.re-frame/test` (`0.1.7`),
`clj-commons/clj-yaml` (`1.0.29`), and `binaryage/devtools` (`1.0.7`).
Add npm deps: `@sqlite.org/sqlite-wasm`, `js-yaml`, `@citation-js/core`,
`@citation-js/plugin-bibtex`, `tailwindcss` (`@tailwindcss/cli`), plus
editor packages (CodeMirror 6 or `monaco-editor` + `webpack`).

### `shadow-cljs.edn` Multi-Module and Worker Config

```clojure
{:deps {:aliases [:cljs]}
 :dev-http {8080 {:root "public"
                  :headers {"Cross-Origin-Opener-Policy" "same-origin"
                            "Cross-Origin-Embedder-Policy" "require-corp"}}}
 :builds
 {:app
  {:target :browser
   :output-dir "public/js"
   :asset-path "/js"
   :js-options {:js-provider :external
                :external-index "target/index.js"
                :keep-as-import #{"@sqlite.org/sqlite-wasm"}}
   :devtools {:autobuild true
              :after-load easy-cv.ui.core/reload!
              :preload [devtools.preload]}
   :modules {:shared             {:entries []}
             :main               {:init-fn easy-cv.ui.core/init
                                  :depends-on #{:shared}}
             :persistence-worker {:init-fn easy-cv.workers.persistence/init
                                  :depends-on #{:shared}
                                  :web-worker true}
             :compute-worker     {:init-fn easy-cv.workers.compute/init
                                  :depends-on #{:shared}
                                  :web-worker true}
             :latex-worker       {:init-fn easy-cv.workers.latex/init
                                  :depends-on #{:shared}
                                  :web-worker true}}}}}
```

COOP (`same-origin`) and COEP (`require-corp`) headers enable
`SharedArrayBuffer` and SQLite's `opfs` VFS in dev and static hosting;
when deployed on static hosts without custom headers (such as GitHub
Pages), the worker automatically selects `opfs-sahpool` or IndexedDB
fallback (detailed in [01-data-model][doc-01]).

[doc-01]: ./01-data-model-and-storage.md
[doc-02]: ./02-io-parsers-editors-and-workers.md
