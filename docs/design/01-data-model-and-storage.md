# 01: Data Model, Canonical IR, and Storage

Easy CV treats a user's career as an append-oriented **fact archive**
rather than a mutable word-processor document. DataScript serves as the
in-memory relational query and time-travel engine, Malli validates
boundaries and defines the canonical CV intermediate representation (IR),
and worker-hosted SQLite WASM + OPFS/IndexedDB provides durable ACID
storage and SQL-tables JSON export.

## Rich Hickey Data Orientation

Applying the principles in [docs/clojure.md][clojure-doc]:

- **Epochal Time Model**: The DataScript connection `conn` is an
  **identity**; every `@conn` (`d/db`) is an immutable **value** at logic
  time `t`. Queries run against a fixed `db` snapshot, so rendering a CV
  never observes half-edited state.
- **Accretion over Breaking Changes**: Entities use stable `:item/uuid`
  identities and namespaced attributes. New capabilities (such as BibTeX
  metadata or CV layout overrides) arrive as new attributes rather than
  mutating or breaking existing facts.
- **Schema a la Carte**: DataScript stores open EAV facts without forcing
  upfront rigidity, while Malli schemas validate *only* at boundary
  checkpoints (import, persistence, and strict export).

## Complete DataScript Schema

The draft in [wiki/docs/schema.md][schema] omits several `:db/valueType`
and `:db/cardinality` specs and lacks citation, CV styling, and snapshot
attributes. Below is the accreted DataScript schema (preserving every
existing key from `wiki/docs/schema.md`):

```clojure
{;; --- Core Identity & Versioning ---
 :item/uuid        {:db/unique :db.unique/identity}
 :item/tags        {:db/cardinality :db.cardinality/many}
 :item/archived?   {}

 ;; --- Person ---
 :person/email     {:db/unique :db.unique/identity}
 :person/name      {}
 :person/phone     {}
 :person/website   {}
 :person/summary   {}
 :person/location  {}
 :person/links     {:db/cardinality :db.cardinality/many}

 ;; --- Organization ---
 :org/name         {}
 :org/type         {} ;; :company | :school | :lab | :community
 :org/location     {}
 :org/url          {}

 ;; --- History (Work, Education, Volunteer) ---
 :history/organization {:db/valueType :db.type/ref}
 :history/type         {} ;; :work | :education | :volunteer
 :history/title        {}
 :history/subtitle     {}
 :history/start-date   {} ;; ISO "YYYY-MM" or "YYYY-MM-DD"
 :history/end-date     {} ;; nil/absent => "Present"
 :history/description  {}
 :history/highlights   {:db/cardinality :db.cardinality/many}
 :history/location     {}

 ;; --- Skill ---
 :skill/name        {:db/unique :db.unique/identity}
 :skill/category    {}
 :skill/level       {}
 :skill/description {}

 ;; --- Project ---
 :project/name        {}
 :project/description {}
 :project/url         {}
 :project/start-date  {}
 :project/end-date    {}
 :project/skills      {:db/cardinality :db.cardinality/many
                       :db/valueType   :db.type/ref}

 ;; --- Document & Scientific Citations (BibTeX / Zotero CSL-JSON) ---
 :document/name     {}
 :document/url      {}
 :document/type     {} ;; :publication | :reference | :diploma | :talk
 :document/date     {}
 :document/citekey  {:db/unique :db.unique/identity}
 :document/doi      {}
 :document/venue    {}
 :document/authors  {:db/cardinality :db.cardinality/many}
 :document/bibtex   {}
 :document/csl-json {} ;; Raw CSL-JSON map stored as EDN value

 ;; --- Hobby ---
 :hobby/name        {}
 :hobby/description {}

 ;; --- CV Configuration (Multi-CV Solver Targets) ---
 :cv/id             {:db/unique :db.unique/identity}
 :cv/title          {}
 :cv/person         {:db/valueType :db.type/ref}
 :cv/history        {:db/cardinality :db.cardinality/many
                     :db/valueType   :db.type/ref}
 :cv/skills         {:db/cardinality :db.cardinality/many
                     :db/valueType   :db.type/ref}
 :cv/projects       {:db/cardinality :db.cardinality/many
                     :db/valueType   :db.type/ref}
 :cv/documents      {:db/cardinality :db.cardinality/many
                     :db/valueType   :db.type/ref}
 :cv/hobbies        {:db/cardinality :db.cardinality/many
                     :db.valueType   :db.type/ref}
 :cv/theme          {} ;; Keyword preset e.g. :academic | :minimal
 :cv/layout         {} ;; Keyword preset e.g. :single-col | :two-col
 :cv/font           {} ;; Map {:heading "..." :body "..." :mono "..."}
 :cv/section-order  {} ;; Ordered vector of section keywords
 :cv/overrides      {} ;; Map {item-uuid -> {:title ... :order ...}}

 ;; --- Epochal Snapshots ---
 :snapshot/id             {:db/unique :db.unique/identity}
 :snapshot/parent         {}
 :snapshot/label          {}
 :snapshot/created-at     {}
 :snapshot/schema-version {}}
```

### Reactive DataScript Bridge

Reusing the pattern from `bb-web-ds-tools.workspaces.core`:

- A single `defonce conn (d/create-conn ds-schema)` holds the live fact
  store.
- `d/listen!` increments a Reagent atom `ds-version`.
- A `reg-sub-raw ::ds-version` exposes `(r/reaction @ds-version)` as a
  Layer 2 signal.
- Derived Layer 3 subscriptions take `:<- [::ds-version]` and run pure
  Datalog queries against `@conn` (never mutating inside queries).

## Malli Schemas and Canonical CV IR

DataScript manages normalized graph relations; **Malli** defines the
denormalized **Canonical CV IR** tree consumed by all renderers and
validated on import/export.

Following `bb-web-ds-tools.core` and `bb-web-ds-tools.components.malli`:

- Initialize `mr/set-default-registry!` with `(m/default-schemas)` plus
  `(met/schemas)` (`malli.experimental.time/schemas`).
- Reuse `validate-data`, `generate-data`, `infer-schema`, and
  `transform-to-json-schema` from `components/malli.cljc`.
- Keep core IR maps **open** (`{:closed false}`) so user-added custom keys
  survive round-trips; apply `malli.util/closed-schema` only when users
  request strict Schema.org / lint validation.

```clojure
(def DateISO
  [:re #"^\d{4}(-\d{2}(-\d{2})?)?$"])

(def CanonicalCV
  [:map
   [:cv/id :uuid]
   [:cv/title :string]
   [:cv/schema-version pos-int?]
   [:cv/style [:map
               [:theme :keyword]
               [:layout :keyword]
               [:font [:map-of :keyword :string]]]]
   [:cv/person [:map
                [:person/name :string]
                [:person/email :string]
                [:person/location {:optional true} :string]
                [:person/summary {:optional true} :string]]]
   [:cv/sections
    [:vector
     [:map
      [:section/id :keyword]
      [:section/items [:vector :map]]]]]])
```

The solver (detailed in [03-solver-ui][doc-03]) is a pure function:
`(solve-cv db cv-id) -> CanonicalCV`, applying `:cv/section-order` and
`:cv/overrides` without mutating any underlying DataScript facts.

## Dual-Engine Persistence and Storage

Why use **both** DataScript and SQLite WASM?

- **DataScript (RAM)**: Sub-millisecond graph pulls, Datalog joins, and
  instant `d/db` snapshot diffs for interactive CV editing.
- **SQLite WASM (Worker + OPFS/IDB)**: Durable ACID storage, large-archive
  SQL filtering/aggregation, and direct SQL-tables JSON export.

### Storage Tier Selection

Inside `:persistence-worker` (`@sqlite.org/sqlite-wasm` via
`sqlite3InitModule`), the runtime picks the strongest available backend:

1. **Primary (`oo1.OpfsDb`)**: Used when `Cross-Origin-Opener-Policy` and
   `Cross-Origin-Embedder-Policy` headers enable OPFS sync access handles.
   Provides near-native disk I/O and ACID transactions.
2. **Header-less OPFS (`opfs-sahpool` VFS)**: Used on static hosts without
   COOP/COEP headers; supports persistent OPFS in a single active tab.
3. **IndexedDB Fallback**: If OPFS is unavailable (e.g., private browsing),
   use `oo1.DB` in-memory SQLite paired with debounced `datascript-transit`
   snapshots in IndexedDB (or `wa-sqlite` `IDBBatchAtomicVFS`).

### Storage and Snapshot Sync Pipeline

```text
Main Thread (DataScript)              :persistence-worker (SQLite WASM)
------------------------              ---------------------------------
d/transact! -> d/listen!
      |
      v (debounced 300ms)
fx/persistence:
  1. Transit-encode tx/snapshot ----> 2. Begin SQLite TX
  2. HoneySQL AST or payload   ----> 3. HoneySQL -> ? parameterized SQL
                                      4. Exec on OpfsDb / sahpool / IDB
                                      5. Write snapshot row (versioned)
                                          |
Main UI <- :persistence/saved <-----------+
```

### Normalized SQLite DDL and HoneySQL v2

Unlike `bb-web-ds-tools.workspaces.persistence-fx`, which builds SQL via
string concatenation in `persist-all!`, Easy CV constructs **all** DDL,
writes, and queries in pure `easy-cv.core.honeysql` (`.cljc`) via
`honey.sql/format` with positional `?` parameters:

```sql
CREATE TABLE IF NOT EXISTS facts (
  uuid TEXT PRIMARY KEY,
  entity_type TEXT NOT NULL,
  attrs_transit TEXT NOT NULL,
  updated_at INTEGER NOT NULL,
  archived INTEGER DEFAULT 0
);
CREATE TABLE IF NOT EXISTS cv_configs (
  uuid TEXT PRIMARY KEY,
  title TEXT NOT NULL,
  config_transit TEXT NOT NULL,
  updated_at INTEGER NOT NULL
);
CREATE TABLE IF NOT EXISTS snapshots (
  id TEXT PRIMARY KEY,
  parent_id TEXT,
  label TEXT,
  schema_version INTEGER NOT NULL,
  db_transit TEXT NOT NULL,
  created_at INTEGER NOT NULL
);
```

Normalized projection tables (`persons`, `organizations`, `history`,
`skills`, `projects`, `documents`, `hobbies`) are maintained alongside
`facts` in the same SQLite transaction so users can query them with SQL
and export clean relational JSON.

### Multi-Version Snapshots and Migration

1. **In-session undo/redo**: Maintain a bounded ring vector of immutable
   `@conn` `d/db` values in `:persistence` (zero serialization cost).
2. **Durable checkpoints**: Encode `(d/serializable @conn)` with
   `cognitect.transit` (`datascript-transit` handlers) and insert into
   `snapshots` with `:snapshot/schema-version`.
3. **Migration on boot**: When loading a snapshot with `v < current-v`,
   a pure chain `(migrate-snapshot snap v current-v)` applies additive
   transforms before calling `d/from-serializable`.

### SQL-Tables JSON Export and Backup

Users retain full ownership of their data with two download actions built
on `bb-web-ds-tools.utils.io/download-file`:

- **SQL-Tables JSON Export**: Worker runs HoneySQL `SELECT *` across
  projection tables (`persons`, `organizations`, `history`, `skills`,
  `projects`, `documents`, `hobbies`, `cv_configs`) and emits a single
  `easy-cv-tables.json` (`{:schema-version N :tables {...}}`).
- **Full SQLite Binary Backup**: Calls `sqlite3_js_db_export` (same
  mechanism as `bb-web-ds-tools.workspaces.persistence-fx/export-db`) to
  download a portable `.sqlite3` file.

[clojure-doc]: ../clojure.md
[schema]: ../../wiki/docs/schema.md
[doc-03]: ./03-solver-ui-and-styling.md
