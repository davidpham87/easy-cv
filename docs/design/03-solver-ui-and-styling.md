# 03: Multi-CV Solver, Re-frame UI, and Styling

This document specifies how Easy CV turns one immutable Fact Archive into
multiple targeted CVs without mutating facts, how the Reagent + Re-frame UI
prevents blast-radius coupling, and how Clojure-defined CSS class maps,
Tailwind CSS v4, and polymorphic components power customizable designs.

## 1. Design Principles (Decomplected UI & State)

Following Rich Hickey's data-oriented principles ([clojure.md][clj-md]):

1. **Facts are immutable; CVs are views**: A job-specific CV never edits a
   `:history/*`, `:skill/*`, or `:project/*` entity. A CV is a pure function
   of `(FactArchive, CVConfig) -> CanonicalCV`.
2. **Isolate state by domain slice**: Each Re-frame view owns its own
   namespaced `app-db` key, events, and subscriptions. No view reaches into
   another view's slice or mutates DataScript inside a Reagent render fn.
3. **Separate app chrome from CV styling**: The authoring UI theme (e.g.,
   Nord Dark, Solarized Light) is independent of the exported CV design
   preset (e.g., Executive Serif, Modern Minimal).
4. **Data over code for styles**: Design presets, fonts, layouts, and
   Tailwind class maps are plain EDN maps, inspectable and serializable.

## 2. Multi-CV Configuration Solver

### 2.1 Fact Archive vs. CV Configuration

In [schema.md][schema-md], a `:cv/*` entity references `:cv/person`,
`:cv/history`, `:cv/skills`, `:cv/projects`, `:cv/documents`, and
`:cv/hobbies`. For multi-CV authoring and interactive filtering, Easy CV
extends `:cv/*` (by accretion, never breaking existing keys) into a
declarative **CV Configuration** (`:cv/config`) stored alongside facts:

```clojure
;; Pure EDN representation of a targeted CV configuration
{:item/uuid        #uuid "11111111-1111-1111-1111-111111111111"
 :cv/id            :backend-clojure
 :cv/title         "Backend Clojure Engineer"
 :cv/person        #uuid "00000000-0000-0000-0000-000000000001"
 :cv/include       #{#uuid "aaaa..."} ;; explicit UUID pins
 :cv/exclude       #{#uuid "bbbb..."} ;; explicit exclusions (win)
 :cv/rules         {:tags-any   #{:clojure :distributed :sql}
                    :after-date "2019-01"
                    :max-items  {:work 4 :projects 3 :skills 12}}
 :cv/order         {:work     [#uuid "aaaa..." #uuid "cccc..."]
                    :projects [#uuid "dddd..."]}
 :cv/overrides     {#uuid "aaaa..."
                    {:history/title "Staff Backend Engineer"
                     :history/description "Led Datalog + SQL pipeline."}}
 :cv/style         {:design :modern-minimal
                    :font   :inter-sans
                    :layout :single-column}
 :cv/summary-override "Clojure & SQL systems builder."}
```

### 2.2 Pure Solver Pipeline (`easy-cv.solver.core`)

The solver is a pure `.cljc` namespace with zero DOM, Re-frame, or SQLite
dependencies. Given an immutable DataScript database value `db` (at any
epoch `t`) and a `cv-config`, `solve-cv` produces a Malli-validated
Canonical CV map:

```
  DataScript DB (t)          CV Config (data)
         |                          |
         +------------+-------------+
                      |
                      v
        [1. Candidate Gather]    pull facts by :item/uuid
                      |
                      v
        [2. Rule Match + Pin]    (rules OR include) MINUS exclude
                      |
                      v
        [3. Merge Overrides]     (merge fact (get overrides uuid))
                      |
                      v
        [4. Order & Budget]      explicit :cv/order -> date desc; cap
                      |
                      v
        [5. Malli Validate]      assert :cv/canonical schema
                      |
                      v
          Canonical Resolved CV  (immutable value for renderers)
```

Key invariants of the solver model:

- **Non-mutating overrides**: `overrides` are merged only inside the
  solved Canonical CV value. Editing a bullet for "Startup CV" never
  overwrites the underlying fact in the archive or in "Academic CV".
- **Exclusion supremacy**: `:cv/exclude` is always applied after rule
  matching and explicit `:cv/include`. If an item is excluded, it will
  never leak into the output even if a tag rule matches it.
- **Deterministic ordering**: Items listed in `:cv/order` appear first in
  user-specified order; remaining matched items sort deterministically by
  `:history/start-date` descending (missing `:end-date` first), then
  `:item/uuid` lexicographically.
- **Zero-cost multi-CV solving & diffing**: Because `solve-cv` is pure,
  `solve-all` maps `solve-cv` over all active `:cv/config` records in
  milliseconds. `diff-cvs` compares two solved CVs by `:item/uuid` and
  reports `{:only-a #{...} :only-b #{...} :overridden #{...}}`.
- **Budget heuristics (non-LLM)**: Computes estimated line/page counts
  and keyword overlap against pasted job-description tokens (pure set
  intersection, no LLM required) so students/juniors see fit at a glance.

### 2.3 Interactive Toggle Semantics

User actions in the Solver UI translate to small, reversible transactions
on the `:cv/config` entity only:

| Action | Event Dispatched | Config Transaction |
| :--- | :--- | :--- |
| Pin ON | `::solver/include-fact` | Add to `:cv/include`, drop `:cv/exclude` |
| Pin OFF | `::solver/exclude-fact` | Add to `:cv/exclude`, drop `:cv/include` |
| Reset | `::solver/reset-fact` | Drop from both sets (rules decide) |
| Edit | `::solver/set-override` | `assoc-in [:cv/overrides id k] v` |
| Reorder | `::solver/move-item` | Update section vec in `:cv/order` |

Because every change is a DataScript transaction on `:cv/config`, undo/redo
and historical snapshots (`db-at-t`) work identically for CV configs and
facts.

## 3. Reagent + Re-frame UI Architecture

### 3.1 App Shell & Routing (Adapted from `bb-web-ds-tools`)

Easy CV adapts the proven SPA shell from `bb-web-ds-tools.core`:

- **Mounting & Hot Reload**: `reagent.dom.client/create-root` with `init`
  and `^:dev/after-load reload!` (plus `malli.registry/set-default-registry!`
  and `malli.experimental.time/schemas` initialized once at boot).
- **Routing**: `reitit.frontend` + `reitit.frontend.easy` (`router`,
  `init-routes!`, `on-navigate`, `::current-route`, `::navigate`,
  `::navigated`, and `:navigate` fx) with fragment routing:
  - `/` (`:archive`): Fact Archive & Quick Entry
  - `/solver` (`:solver`): Multi-CV Configuration Matrix
  - `/styles` (`:styles`): Design, Font, Layout & CSS Class Customizer
  - `/preview` (`:preview`): Live Preview + Multi-Format Export
  - `/editor` (`:editor`): Split CodeMirror 6 (EDN/YAML/LaTeX) + Preview
  - `/io` (`:io`): Import/Export, BibTeX/Zotero, SQL Dump, Fake CV Gen

### 3.2 Strict Blast-Radius Isolation

To honor the Re-frame philosophy and prevent big-blast-radius edits:

```
+-------------------------------------------------------------------+
|  Views (pure Hiccup fns; subscribe & dispatch ONLY; NO db reads)  |
|  views.archive | views.solver | views.styles | views.preview      |
+--------+---------------+--------------+--------------+------------+
         | sub / disp    | sub / disp   | sub / disp   | sub / disp
         v               v              v              v
+-------------------------------------------------------------------+
|  Isolated Re-frame Slices (co-located events + subs per view)     |
|  db key: ::archive  ::solver       ::styles       ::editor        |
|  rule: slice X NEVER writes to slice Y; transient :user-input     |
|        stays separated from committed facts                       |
+--------+------------------------------+---------------------------+
         | :ds/transact fx              | :worker/post fx
         v                              v
+-------------------------+    +------------------------------------+
| DataScript Conn + Atom  |    | Web Worker Pool (Transit/async)    |
| ::ds-version (d/listen!)|    | SQLite WASM + OPFS | LaTeX/Typst   |
+-------------------------+    +------------------------------------+
```

Concrete isolation rules enforced across the codebase:

1. **Co-located View Slices**: Following `bb-web-ds-tools` (`views/malli`,
   `views/honeysql`, `views/datasets`), each view owns its events and subs
   under its own namespace keyword (`::archive`, `::solver`, `::styles`,
   `::editor`, `::io`). Changing a form field in `views.archive` cannot
   break `views.solver`.
2. **Layered Subscriptions (Signal Graph)**:
   - *Layer 2 (DataScript Bridge)*: Adapted from `bb-web-ds-tools`
     `workspaces.core`, a `d/listen!` callback increments a Reagent atom
     `ds-version`. `rf/reg-sub-raw ::ds-version` exposes reactive DB
     snapshots without copying the whole DataScript store into `app-db`.
   - *Layer 3 (Derived Solver Subs)*: `:<- [::ds-version]` and
     `:<- [::solver/active-config-id]` feed pure `solve-cv` calls.
     `views.preview` and `views.export` subscribe to `::solver/resolved-cv`
     and are completely agnostic to how facts were edited or filtered.
3. **Pure Event Handlers + Effect Boundary**:
   - Events return data maps `{:db ... :ds/transact ... :worker/post ...}`.
   - All side effects (`d/transact!`, `Worker.postMessage`, `:theme/apply`,
     `download-file`) live in `rf/reg-fx` handlers in `easy-cv.fx.*`.
4. **Shared Component Reuse from `bb-web-ds-tools`**:
   - `easy-cv.components.common` <- `bb-web-ds-tools.components.common`:
     `button`, `button-info`, `input`, `select`, `textarea`, `pre-block`,
     `card`, `table-container`/`table`/`thead`/`tbody`/`tr`/`th`/`td`,
     `page-header`, `section-header`, `label`, `tabs`, `nav-tabs`,
     `help-button`.
   - `easy-cv.components.layout` <- `bb-web-ds-tools.components.layout` &
     `layout/tool_view`: `page-container`, `container`, `section`, `grid`,
     `flex-row`, `flex-col`, `split-view`, `tool-view`.
   - `easy-cv.components.navigation` <- `components.navigation`:
     `top-bar`, `menu-button`, `portal-to-top-bar`.

## 4. Styling: Clojure CSS Maps, Tailwind v4 & Polymorphism

### 4.1 Tailwind CSS v4 + Clojure-Defined Static Class Maps

Tailwind CSS v4 replaces `tailwind.config.js` with CSS-first `@theme` and
`@source` directives in `resources/css/app.css`:

```css
@import "tailwindcss";
@source "../../src";
@source "../../public/index.html";
```

Because Tailwind v4's Oxide Rust scanner reads `.cljs` and `.cljc` files
as plain text, **dynamic string construction like `(str "bg-" color)`
is invisible to the CSS compiler and breaks in production**. Easy CV
enforces static Clojure lookup maps:

```clojure
(ns easy-cv.styles.classes)

;; Static strings ensure Tailwind v4 Oxide extracts every utility class.
(def density-classes
  {:compact  {:section "mb-2" :item "py-1 text-xs leading-snug"}
   :balanced {:section "mb-4" :item "py-1.5 text-sm leading-normal"}
   :airy     {:section "mb-6" :item "py-2 text-base leading-relaxed"}})

(def accent-classes
  {:slate   {:heading "text-slate-800 border-slate-300" :badge "bg-slate-100"}
   :indigo  {:heading "text-indigo-900 border-indigo-300" :badge "bg-indigo-50"}
   :emerald {:heading "text-emerald-900 border-emerald-300"
             :badge   "bg-emerald-50"}
   :mono    {:heading "text-black border-black" :badge "bg-neutral-100"}})
```

For the **App Chrome Theme**, Easy CV directly adapts
`bb-web-ds-tools.theme`, `bb-web-ds-tools.utils.themes`, and
`bb-web-ds-tools.events.theme`:

- `easy-cv.theme` defines `colors` (`:bg-page "var(--bg-page)"`,
  `:bg-card`, `:text-primary`, `:border-default`, etc.), `mapping` to
  `:portal.colors/*`, `resolve-theme-colors`, and static arbitrary-value
  vars (`bg-[var(--bg-page)]`, `text-[var(--text-primary)]`).
- `easy-cv.events.theme` dispatches `::set-theme` and `:theme/apply` fx to
  update `:root` CSS variables and sync the CodeMirror 6 editor theme
  (replacing Monaco calls from `bb-web-ds-tools`).

### 4.2 Predefined Design, Font, and Layout Presets

CV styling uses three orthogonal EDN preset registries in
`easy-cv.styles.presets` so users can mix and match or pick a bundle:

- **Design** (`:modern-minimal`, `:academic-classic`, `:swiss-grid`,
  `:compact-ats`, `:executive-serif`): controls palette, divider lines,
  bullet glyphs, badge style, and LaTeX documentclass mapping.
- **Font** (`:inter-sans`, `:merriweather-serif`, `:ibm-plex-mono`,
  `:computer-modern-web`, `:source-sans`): maps to self-hosted
  `@fontsource` CSS stacks and LaTeX font packages (`lmodern`, `helvet`).
- **Layout** (`:single-column`, `:two-column-sidebar`, `:timeline-left`,
  `:dense-two-page`): controls grid columns, section order, header
  alignment, and `@media print` page-break rules.

Curated one-click bundles (`predefined-bundles`) pair sensible defaults:
e.g. `:student-tech` (`:modern-minimal` + `:inter-sans` +
`:single-column`), `:academic-cv` (`:academic-classic` +
`:computer-modern-web` + `:single-column`), `:exec-brief`
(`:executive-serif` + `:merriweather-serif` + `:two-column-sidebar`).

### 4.3 Polymorphic Reagent Components & Custom CSS Overrides

CV preview rendering uses Clojure multimethods (`easy-cv.render.components`)
dispatching on `[layout-id section-type]` so any layout can override a
single section (e.g., `:two-column-sidebar` rendering `:skills` as compact
tag pills, while `:single-column` renders grouped category rows):

```clojure
(defmulti render-section
  (fn [ctx section-type _items]
    [(get-in ctx [:style :layout]) section-type]))

(defmethod render-section :default
  [ctx section-type items]
  [default-section-view ctx section-type items])

(defmethod render-section [:two-column-sidebar :skills]
  [ctx _ items]
  [sidebar-skill-pills ctx items])
```

Power users can supply `:cv/class-overrides` (e.g.,
`{:heading "tracking-widest uppercase"}`) or raw scoped CSS in
`:cv/custom-css` (sanitized and scoped to `#cv-sheet`) without touching
Clojure code.

## 5. UI Wireframes (<= 80 Chars Wide)

### 5.1 Fact Archive (`/`)

```
+----------------------------------------------------------------------------+
| Easy CV | [Archive] [Solver] [Styles] [Preview] [Editor] [I/O]  [Theme: v] |
+----------------------------------------------------------------------------+
| Fact Archive (142 facts @ tx:48)           [+ New Fact] [Import] [Gen Fake]|
| Search: [clojure___________]  Type: [All v]  Tag: [#backend v]  [Undo tx]  |
+------------------------------+---------------------------------------------+
| Facts (filtered: 18)         | Selected Fact Editor (UUID: a1b2-...)       |
|------------------------------|---------------------------------------------|
| [Work] Senior Eng @ Acme     | Type:   [Work v]   Org: [Acme Corp v]       |
|   2022-03 .. Present         | Title:  [Senior Engineer__________________] |
| [Work] Intern @ BetaLabs     | Dates:  [2022-03___] to [Present____]       |
|   2020-06 .. 2020-09         | Tags:   [#clojure] [#sql] [#wasm] [+tag]    |
| [Edu ] B.Sc. CS @ State Univ | Desc (Markdown/LaTeX bullets):              |
|   2017-09 .. 2021-05         | +-----------------------------------------+ |
| [Proj] easy-cv (CLJS)        | | - Built local-first Datalog engine      | |
| [Skil] Clojure (Expert)      | | - Cut query p95 latency by 60%          | |
| [Doc ] WASM Paper (2024)     | +-----------------------------------------+ |
| [Hob ] Classical Guitar      | Used in CVs: [Backend] [Full-Stack]         |
|                              | [Save Fact (New Tx)]          [Delete Fact] |
+------------------------------+---------------------------------------------+
```

### 5.2 Multi-CV Solver (`/solver`)

```
+----------------------------------------------------------------------------+
| Multi-CV Solver | Active: [Backend Clojure v] [+ New CV] [Clone] [Diff v]  |
+--------------------+---------------------------------+---------------------+
| Rules & Overrides  | Fact Inclusion Matrix           | Fit & Budget Check  |
|--------------------|---------------------------------|---------------------|
| Tags Any:          | Fact          |Back|Full|Acad | Est. Pages: 1.4/2.0 |
|  [x] #clojure      |---------------+----+----+-----|---------------------|
|  [x] #sql          | Work: Acme    | [x]| [x]| [ ] | Sections:           |
|  [ ] #frontend     | Work: Intern  | [-]| [x]| [x] |  Work:     3/4 max  |
| After: [2019-01]   | Edu:  B.Sc CS | [x]| [x]| [x] |  Projects: 2/3 max  |
| Caps:              | Proj: easy-cv | [x]| [x]| [ ] |  Skills:   9/12 max |
|  Work: [4] Proj:[3]| Proj: Compiler| [x]| [ ]| [x] |---------------------|
|--------------------| Doc:  Paper   | [ ]| [ ]| [x] | Job Keyword Match:  |
| Selected Override: | Skil: Clojure | [x]| [x]| [x] |  Paste JD -> [7/9]  |
|  Work: Acme        | Skil: React   | [ ]| [x]| [ ] |  Missing: k8s, gRPC |
|  Title: [Staff...] | Legend: [x] pin [-] rule [ ] off|---------------------|
|  [Reset Override]  | (Click row to edit CV override) | [Open in Preview ->]|
+--------------------+---------------------------------+---------------------+
```

### 5.3 Theme & Layout Customizer (`/styles`)

```
+----------------------------------------------------------------------------+
| Style Customizer | CV: [Backend Clojure v]  Bundle: [Student Tech v]       |
+----------------------------------+-----------------------------------------+
| Presets & Class Maps             | Live Mini-Sheet Preview (A4 Scale 65%)  |
|----------------------------------|-----------------------------------------|
| Design Preset:                   | +-------------------------------------+ |
|  (*) Modern Minimal              | | ALEX RIVERA                         | |
|  ( ) Academic Classic            | | Backend Clojure Engineer | Berlin   | |
|  ( ) Swiss Grid                  | |-------------------------------------| |
|  ( ) Compact ATS                 | | EXPERIENCE                          | |
|  ( ) Executive Serif             | | Staff Backend Eng, Acme  2022-Pres  | |
| Font Pair: [Inter Sans v]        | |  * Built local-first Datalog...     | |
| Layout:    [Two-Col Sidebar v]   | |                                     | |
| Density:   [Balanced v]          | | PROJECTS           | SKILLS         | |
| Accent:    [Indigo v]            | | easy-cv (CLJS)     | [Clojure][SQL] | |
| Section Order: [Edu,Work,Proj..] | |                    | [DataScript]   | |
| Class Overrides (EDN):           | | EDUCATION          |                | |
|  {:heading "uppercase text-sm"}  | | B.Sc. CS, State U. | 2017 - 2021    | |
| [Apply to CV] [Save as Preset]   | +-------------------------------------+ |
+----------------------------------+-----------------------------------------+
```

### 5.4 Live Preview + Code Editor (`/editor` & `/preview`)

```
+----------------------------------------------------------------------------+
| Live Editor & Export | Mode: [EDN] [YAML] [LaTeX] | Format: [HTML] [PDF].. |
+------------------------------------+---------------------------------------+
| CodeMirror 6 (Parinfer / LaTeX)    | Rendered Preview (HTML / Print Sheet) |
|------------------------------------|---------------------------------------|
| 1 {:cv/id :backend-clojure         | +-----------------------------------+ |
| 2  :cv/title "Backend Engineer"    | | ALEX RIVERA                       | |
| 3  :cv/include #{#uuid "a1b2..."}  | | alex@example.dev | github/alex    | |
| 4  :cv/style {:design :modern      | |-----------------------------------| |
| 5             :font :inter-sans}   | | Experience                        | |
| 6  :cv/overrides                   | |  Senior Eng, Acme (2022-Present)  | |
| 7   {#uuid "a1b2..."               | |  - Datalog + SQLite WASM store    | |
| 8    {:history/title "Staff Eng"}}}| |                                   | |
|------------------------------------| | Skills: Clojure, SQL, DataScript  | |
| Malli Diagnostics:                 | +-----------------------------------+ |
| [OK] Valid :cv/config (0.4 ms)     | [Print PDF] [Build LaTeX WASM] [.zip] |
+------------------------------------+---------------------------------------+
```

[clj-md]: ../clojure.md
[schema-md]: ../../wiki/docs/schema.md
