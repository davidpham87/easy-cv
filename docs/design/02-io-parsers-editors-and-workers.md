# 02: I/O Parsers, Schema.org, Generators, Editors, and Workers

This document defines Easy CV's external boundaries: Schema.org JSON-LD
mapping, EDN/YAML/BibTeX/Zotero parsers, seeded fake CV generators, the
code editor abstraction, and the Transit Web Worker bus.

## Schema.org Bidirectional Mapping (JSON-LD)

To make archived facts portable and validator-friendly (including LLM
import prompts described in [the project README][project-readme]), Easy CV
maps its Canonical IR bidirectionally to [Schema.org][schema-org] JSON-LD:

- **Person -> `Person`**: `:person/name` -> `name`, `:person/email` ->
  `email`, `:person/phone` -> `telephone`, `:person/website` -> `url`,
  `:person/summary` -> `description`, `:person/location` ->
  `address` (`PostalAddress`), `:person/links` -> `sameAs`, and
  selected `:skill/name` values -> `knowsAbout`.
- **Work History -> `worksFor` / `OrganizationRole`**: `:history/title`
  -> `roleName`, `:history/start-date` -> `startDate`,
  `:history/end-date` -> `endDate`, `:history/organization` ->
  `worksFor` (`Organization` with `name`, `url`, `location`).
- **Education -> `alumniOf` / `hasCredential`**:
  `EducationalOrganization` (`alumniOf`) paired with
  `EducationalOccupationalCredential` (`credentialCategory` for degree,
  `about` for field of study, `recognizedBy` for institution).
- **Projects -> `workExample` (`CreativeWork`)**: `:project/name` ->
  `name`, `:project/description` -> `description`, `:project/url` ->
  `url`, `:project/skills` -> `keywords`.
- **Publications -> `ScholarlyArticle`**: `:document/name` ->
  `headline`, `:document/authors` -> `author`, `:document/date` ->
  `datePublished`, `:document/doi` -> `identifier`, `:document/venue` ->
  `isPartOf`.

A pure `.cljc` module (`easy-cv.core.schema-org`) implements
`ir->json-ld` and `json-ld->tx-data`. Unknown JSON-LD fields are kept via
open-map accretion rather than silently dropped.

## Documented I/O Parsers and Validators

```text
Raw Text (EDN / YAML / JSON-LD / BibTeX / CSL-JSON)
   |
   v  [Format-Specific Pure Parser or Worker JS Adapter]
Plain Clojure Data (untrusted)
   |
   v  [easy-cv.core.io.validate : Malli Coercer + Schema.org Check]
   +--- Invalid ---> Structured Error List [{:path [...] :msg "..."}]
   |
   v  Valid
Canonical Facts / DataScript Tx Data -> Preview Diff -> d/transact!
```

### Supported Input Formats

- **EDN**: Parsed via `clojure.edn/read-string` (pure `.cljc`, never
  `eval`). Native format for power users and snapshot backups.
- **YAML**: Parsed via `js-yaml` in CLJS (following
  `bb-web-ds-tools.utils.dataset-processing`) and `clj-yaml` in JVM/bb
  tests; keys normalized via `camel-snake-kebab.core/->kebab-case`.
- **JSON & Schema.org JSON-LD**: Parsed via `js/JSON.parse` (CLJS) or
  `org.babashka/json` (CLJ), then validated against `@context`, `@type`,
  and Malli IR schemas.
- **BibTeX & Zotero CSL-JSON**:
  - Use `@citation-js/core` (~34-35 KB min+gz) +
    `@citation-js/plugin-bibtex` inside `:compute-worker` to parse
    `.bib` files into normalized CSL-JSON, plus `@citation-js/plugin-csl`
    when formatted bibliography strings are needed.
  - When strict Better BibTeX brace/LaTeX-math fidelity is required,
    `@retorquere/bibtex-parser` is a drop-in parser alternative; for
    lightweight parsing without CSL formatting, `bibtex-parse-js`
    (<10 KB min+gz) suffices.
  - **Zotero Integration**: Fetch public or user-supplied Zotero Web API
    endpoints (`/items?format=csljson`) directly as native CSL-JSON
    (zero BibTeX parsing overhead) and map items to `:document/*`
    entities (`:document/citekey`, `:document/doi`, `:document/csl-json`).

All parsers emit **structured path errors** (`{:path :line :message}`)
so both the UI import modal and the code editor gutter can highlight
mistakes immediately.

## Fake CV Generators (Testing, Demos, and Benchmarks)

Built on `malli.generator` and `clojure.test.check.generators` (reusing
`bb-web-ds-tools.components.malli/generate-data`), `easy-cv.core.generators`
produces deterministic, seeded fact archives and CV configs.

### Career Stage Profiles

- **`:student` (Primary Focus)**: 0-1 yr industry experience, active
  degree (`:history/end-date` nil or future), 2-3 coursework/capstone
  projects, hackathon/club roles, broad foundational skills, and
  optional first-author workshop paper.
- **`:junior` (Primary Focus)**: 1-3 yrs experience, 1-2 full-time or
  internship roles, 3-5 shipped projects with skill refs, concise
  summary, and targeted skills matrix.
- **`:mid` & `:senior`**: 3-12 yrs, multi-org progression, quantified
  impact bullets (`:history/highlights`), mentoring, and architecture
  projects.
- **`:vp` & `:exec`**: 12+ yrs, board/advisory roles, condensed early
  history (testing solver exclusion/truncation), and strategic summary.
- **`:academic`**: Heavy `:document/*` set (10-40 BibTeX/CSL-JSON
  publications, talks, grants) to test citation rendering and filtering.

### Adversarial & Benchmark Fixtures

Dedicated test generators synthesize:
1. Missing optional fields (`nil` end dates, remote locations, no phone).
2. LaTeX-hostile characters (`&`, `%`, `$`, `#`, `_`, `{`, `}`, Unicode).
3. Large stress archives (1,000+ facts, 10+ CV configs) for worker I/O,
   Datalog query, and render benchmark suites.

## Code Editor Architecture: Monaco vs CodeMirror 6

Easy CV lets users edit YAML, EDN, JSON-LD, BibTeX, and generated LaTeX
directly. Because `../bb-web-ds-tools` already wraps Monaco while
CodeMirror 6 excels for lightweight EDN/LaTeX editing, we isolate the
editor behind a **polymorphic component** (`easy-cv.ui.components.editor`):

| Criterion | Monaco (`bb-web-ds-tools`) | CodeMirror 6 |
| --- | --- | --- |
| Repo reuse | Ready (`components/editor.cljs`) | New Reagent wrapper |
| Bundle size | Heavy (~2-4 MB + workers) | Modular (~150-350 KB) |
| EDN edit | Basic token highlight | `nextjournal/clojure-mode` |
| LaTeX mode | Custom Monarch tokenizer | `codemirror-lang-latex` / `stex` |
| YAML / JSON | Built-in JSON + YAML | `lang-yaml` & `lang-json` |

**Recommended Strategy**:
- **Phase 1**: Reuse `bb-web-ds-tools.components.editor/monaco-editor`
  (cursor-preserving edits, `theme` sync via `events/theme`) when fast
  bootstrapping is needed, or mount CodeMirror 6 directly via an
  `editor-view` multimethod dispatched on `:editor/engine`.
- **Target Default**: Prefer **CodeMirror 6** (`nextjournal/clojure-mode`
  for EDN structural editing + `codemirror-lang-latex` or
  `@codemirror/legacy-modes/mode/stex` via `StreamLanguage.define`) to
  keep initial bundle size small for student users on slow networks.

```text
+-----------------------------------------------------------------------+
| Editor Toolbar: [Format: EDN | YAML | JSON-LD | LaTeX]  [Validate]   |
+-----------------------------------+-----------------------------------+
| CodeMirror 6 / Monaco Adapter     | Malli / Schema.org Diagnostics    |
| 1 | :person/name "Ada Lovelace"   | [OK] 14 facts parsed              |
| 2 | :history/start-date "2024-05" | [WARN] line 9: missing :item/uuid |
| 3 | ...                           | [Apply Diff -> DataScript]        |
+-----------------------------------+-----------------------------------+
```

## Web Workers and Transit Message Bus

Heavy parsing (BibTeX, large YAML/JSON), fake archive generation,
SQLite WASM OPFS/IDB I/O, and LaTeX/Typst WASM compilation run off the
main thread so UI typing and preview remain 60 fps.

### Worker Modules (`shadow-cljs`)

1. `:persistence-worker` (`easy_cv.workers.persistence`): Owns SQLite
   WASM handle (`OpfsDb` / `opfs-sahpool` / fallback), executes HoneySQL
   queries, manages snapshots, and builds SQL-tables JSON exports.
2. `:compute-worker` (`easy_cv.workers.compute`): Runs `@citation-js/*`
   BibTeX parsing, large Malli/test.check archive generation, and
   in-browser benchmark runners.
3. `:latex-worker` (`easy_cv.workers.latex`): Hosts BusyTeX / SwiftLaTeX
   or Typst WASM compilation (see [04-rendering][doc-04]).

### Transit Envelope and `core.async` Correlation

Adapting `bb-web-ds-tools.utils.worker` (`create-worker`, `post-message`,
`set-handler`, `terminate`):

- All messages use **Transit JSON** (`cognitect.transit`) so Clojure
  keywords, UUIDs, dates, and sets cross worker boundaries losslessly.
- Every request carries a correlation envelope validated by Malli:

```clojure
{:msg/id       #uuid "..."
 :msg/op       :persistence/save-snapshot ;; or :io/parse-bibtex, etc.
 :msg/payload  {...}
 :msg/reply-to nil}                       ;; set on worker responses
```

- `easy_cv.fx.worker-bus` maintains a pending map `{msg-id -> chan}`
  with explicit timeouts and cancellation (`:msg/op :worker/cancel`) so
  rapid typing in the solver or editor cancels stale worker jobs.
- **Hot Reload**: During `shadow-cljs watch`, worker modules receive code
  reloads separately; `ui.core/reload!` only re-renders the React root
  and never tears down active `persistence-worker` DB handles.

[project-readme]: ../../README.md
[schema-org]: https://schema.org
[doc-04]: ./04-rendering-export-and-roadmap.md
