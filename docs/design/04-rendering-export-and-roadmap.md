# 04: Multi-Format Rendering, Testing, and Roadmap

This document defines Easy CV's multi-format rendering pipeline, in-browser
LaTeX/Typst WASM tradeoffs, property-based testing (PBT) and benchmark
suites, and an iterative Phases 0..7 implementation roadmap built for small
commits and minimal blast radius.

## 1. Multi-Format Rendering Pipeline

Every renderer is a pure `.cljc` function `(render* canonical-cv opts)`
taking the Malli-validated Canonical Resolved CV emitted by
`easy-cv.solver.core` (see `03-solver-ui-and-styling.md`). Renderers never
query DataScript or mutate `app-db`, so adding or changing an output target
has zero blast radius on facts or UI state.

```
  Canonical Resolved CV (Malli-validated EDN)
         |
         +---> render.html     --> Single-page HTML + inline CSS + JSON-LD
         +---> render.site     --> Multi-page static site (.zip via worker)
         +---> render.pdf      --> Print CSS (@page) or Worker WASM PDF
         +---> render.latex    --> .tex + .bib (+ optional BusyTeX WASM)
         +---> render.markdown --> GitHub-Flavored Markdown + YAML header
         +---> render.jsonld   --> Schema.org Person JSON-LD graph
         |
  SQLite WASM (Worker + HoneySQL)
         +---> render.sql-json --> Normalized SQL tables JSON dump
```

### 1.1 HTML (Single-Page & Full Static Website Export)

1. **Single-Page HTML (`easy-cv.render.html`)**:
   - Serializes the active Reagent/Hiccup sheet (or pure `.cljc` Hiccup
     template) to standalone HTML5 (`<!doctype html>`).
   - Inlines resolved CSS custom properties from the active preset, a
     scoped Tailwind stylesheet subset, `@media print` rules, and an
     optional `<script type="application/ld+json">` Schema.org block.
   - Zero external network calls or CDN fonts; works offline and preserves
     user privacy ([README.md][readme]).
2. **Full Static Website Export (`easy-cv.render.site`)**:
   - Generates a multi-page static portfolio ready for GitHub Pages or
     Netlify:
     - `index.html`: Primary CV summary, experience, and skills.
     - `projects.html`: Expanded project cards with skill tags & links.
     - `publications.html`: Formatted bibliography from `:document` /
       CSL-JSON entries.
     - `cv.jsonld`: Machine-readable Schema.org `Person` artifact.
     - `styles.css` + `robots.txt` (`noindex` toggle for privacy).
   - Packaged in a Web Worker using a lightweight pure-JS ZIP writer
     (`fflate`) and saved via `utils.io/download-file` (adapted from
     `bb-web-ds-tools.utils.io`).

### 1.2 PDF Generation

PDF output follows a two-tier strategy balancing zero-overhead immediacy
with exact typographic control:

- **Tier 1 — Browser Print CSS (Default, 0 MB WASM)**:
  - Dedicated `@media print` stylesheet and `/print` route hide app chrome
    and isolate `#cv-sheet`.
  - Sets `@page { size: A4; margin: 14mm; }` (or US Letter via preset),
    `break-inside: avoid` on timeline/project items, and triggers
    `js/window.print`.
  - Produces crisp vector text, selectable links, and ATS-friendly PDFs
    with zero download penalty.
- **Tier 2 — Worker WASM & CI Headless PDF**:
  - In-browser LaTeX/Typst WASM compilation (Section 1.3) for users who
    want exact TeX/Typst PDF typesetting offline.
  - Headless Playwright `page.pdf()` via a `bb` task for deterministic CI
    visual regression checks.

### 1.3 LaTeX Export & Online WASM Compilation Tradeoffs

1. **Pure `.tex` + `.bib` Export (`easy-cv.render.latex`, Always On)**:
   - Emits clean, human-editable LaTeX (`moderncv`, `article`, or preset
     macro template) plus a matching `.bib` file from `:document` and
     Zotero CSL-JSON references.
   - Uses a strict escaper for reserved TeX characters (`\`, `%`, `&`,
     `$`, `#`, `_`, `{`, `}`, `~`, `^`) verified by PBT.
   - Costs 0 MB bundle size and lets users compile locally or in Overleaf.
2. **In-Browser LaTeX WASM vs. Typst WASM Tradeoffs**:
   - **`TeXlyre/texlyre-busytex`** (Primary LaTeX WASM choice):
     - Payload: `busytex.wasm` (~30-32 MB) + TeX Live data (~90-400+ MB,
       preloaded or lazy).
     - Runtime: `BusyTexRunner` in a Web Worker supporting pdfLaTeX,
       XeLaTeX, LuaLaTeX, `bibtex8`, and `makeindex`.
   - **`typeward/texlive-wasm`** (Watchlist):
     - Payload: Modular per-engine WASM (`pdflatex.wasm` ~1.3 MB,
       `xelatex.wasm` ~2.8 MB, `lualatex.wasm` ~4.8 MB, `biber`/`bibtexu`
       ~877 KB-9.1 MB) + external TeX Live tarballs.
     - Status: Pre-alpha maturity; revisit as bundle sizes mature.
   - **`SwiftLaTeX`** (Backup):
     - Runtime: `PdfTeXEngine`/`XeTeXEngine` in Worker + on-demand TeX
       Live fetching (`SwiftLaTeX/Texlive-Ondemand`).
     - Risk: AGPL-3.0, low update frequency, and public server outages;
       requires self-hosting the `setTexliveEndpoint` asset server.
   - **`manuels/texlive.js`** (Reject):
     - Legacy Emscripten `pdftex` (TeX Live 2016); outdated.
   - **Typst WASM (`Myriad-Dreamin/typst.ts`)** (Fast Preview Alternative):
     - Payload: `@myriaddreamin/typst-ts-web-compiler` (~7.62 MB gz) +
       renderer (~351 KB gz); fast incremental PDF/SVG/HTML compilation.
     - Tradeoff: No native `.tex` export; converting Typst to LaTeX needs
       Pandoc (`--from typst --to latex`) or experimental Tylax/TypeTeX
       via `mitex`, which loses complex custom CV templates.

**Architectural Decision**: Keep `.tex`/`.bib` pure-CLJS generation as the
mandatory core. Load WASM compilation lazily in a dedicated Web Worker
(`workers.typeset`) only when the user clicks "Compile PDF in Browser":
default to `texlyre-busytex` for true LaTeX fidelity, and optionally expose
`typst.ts` (`@myriaddreamin/typst-ts-web-compiler`) for native Typst
presets where instant incremental preview matters more than `.tex` parity.

### 1.4 Markdown, Schema.org JSON-LD, and SQL JSON Dump

- **Markdown (`easy-cv.render.markdown`)**: Emits GFM / Pandoc-compatible
  Markdown with YAML frontmatter (`title`, `author`, `date`, `style`) and
  reference-style links for publications and projects.
- **Schema.org JSON-LD (`easy-cv.render.jsonld`)**: Emits a canonical
  [Schema.org][schema-org] `Person` document:
  - Root `Person` (`name`, `jobTitle`, `email`, `address` `PostalAddress`,
    `sameAs`, `knowsAbout` from `:skill/*`).
  - `:work` -> `worksFor` (`Organization` + `OrganizationRole` with
    `startDate`/`endDate`).
  - `:education` -> `alumniOf` (`EducationalOrganization`) and
    `hasCredential` (`EducationalOccupationalCredential` with
    `credentialCategory` and `recognizedBy`).
  - `:project` / `:document` -> `workExample` (`CreativeWork`).
- **SQL Tables JSON Dump (`easy-cv.render.sql-export`)**:
  - Dispatches a `:worker/post` request to the SQLite WASM worker
    (`@sqlite.org/sqlite-wasm`).
  - Runs parameterized HoneySQL `SELECT` queries (never raw string
    concatenation) across normalized tables: `persons`, `orgs`,
    `histories`, `skills`, `projects`, `documents`, `hobbies`,
    `cv_configs`, `cv_facts`, and `snapshots`.
  - Wraps rows with `{:schema-version 1 :exported-at <iso> :tables {...}}`
    and triggers `download-file` as `easy-cv-sql-dump.json` for reuse in
    DuckDB, Datasette, Python, or re-import.

## 2. Property-Based Testing & Benchmark Suites

Following [clojure.md][clj-md] ("Data is essential, Code is accidental";
prefer generative testing over brittle example-only tests) and reusing
patterns from `bb-web-ds-tools`:

### 2.1 Fake CV Generators (`easy-cv.gen.cv`)

Built with `malli.generator` and `clojure.test.check.generators`, seeded
for reproducible fixtures and focused on Easy CV's primary audience:

- **Primary Focus (`:student`, `:junior`)**: Coursework, academic
  projects, hackathons, internships, TA roles, 0-2 years work history,
  and grouped foundational skills.
- **Career Progression (`:mid`, `:senior`, `:vp`, `:exec`)**: Larger
  histories, cross-org leadership, patents/publications (with BibTeX /
  CSL-JSON entries), board/advisory roles, and multi-CV target configs.

### 2.2 PBT Invariants (`test/easy_cv/...`)

Adapts `bb-web-ds-tools.components.malli-gen-test` (`tc/quick-check`,
`prop/for-all`, `mg/generator`) and `bb-web-ds-tools.workspaces-test`
(`day8.re-frame.test/run-test-sync` + `setup/suppress-re-frame-warnings`):

1. **Fact-Archive Non-Mutation**: Solving `N` random CV configs against a
   DataScript DB leaves `(d/datoms @conn :eavt)` count and entity hashes
   identical before and after solving.
2. **Solver Determinism & Malli Validity**: `(solve-cv db cfg)` returns
   equal output across repeated calls and passes `(m/validate CanonicalCV)`.
3. **Exclusion Supremacy**: For any `uuid` in `:cv/exclude`, `uuid` never
   appears in any section of `(solve-cv db cfg)` regardless of `:cv/rules`
   or `:cv/include`.
4. **Override Locality**: An attribute in `:cv/overrides` changes only the
   target CV's solved output and never leaks into sibling CV configs.
5. **Round-Trip & Adversarial Escaping**:
   - `EDN -> parse -> CanonicalCV` and `YAML -> parse -> CanonicalCV`
     and `JSON-LD -> import -> CanonicalCV` preserve normalized equality.
   - `render.latex`, `render.html`, and `render.markdown` never throw on
     generated Unicode or adversarial punctuation (`%&$_{}~^\<>"`) and
     produce balanced braces/tags.
6. **SQL <-> DataScript Parity**: Exporting SQL tables JSON via HoneySQL
   and re-hydrating produces equivalent entity sets.
7. **Re-frame Slice Isolation**: Under `rf-test/run-test-sync`, events in
   `views.solver` never mutate `::archive`, `::styles`, or `::editor` keys
   in `app-db`.

### 2.3 Benchmark Suites (`bench/easy_cv/bench.cljs`)

Executed via Babashka / `shadow-cljs` `:node-script` and browser Karma /
Playwright harnesses, recording `p50`, `p95`, and `p99` timings:

- **Suite A — Query & Solver**: DataScript pull + `solve-cv` across 50,
  500, and 2,000 facts x 10 CV configs (`< 5 ms` p95 for 500 facts).
- **Suite B — Renderers**: `render.html`, `render.markdown`,
  `render.latex`, `render.jsonld` (`< 10 ms` p95 per format for 60fps
  live preview).
- **Suite C — Worker & SQL I/O**: Transit round-trip + HoneySQL batch
  insert + SQL JSON dump on 1,000 rows (`< 50 ms` p95 on OPFS/RAM).

## 3. Iterative Implementation Roadmap (Phases 0..7)

Designed for small, atomic git commits, test-first development (write the
Malli schema + `test.check` property before the module), and strict
blast-radius containment:

```
  P0 Hygiene --> P1 Malli + Gen --> P2 DataScript + Solver
                                              |
       +--------------------------------------+
       v                                      v
  P3 Worker + SQLite WASM             P4 I/O Parsers (EDN/YAML/JSON-LD/BibTeX)
       |                                      |
       +------------------+-------------------+
                          v
                 P5 Re-frame Shell, CSS Maps & Solver UI
                          |
                          v
                 P6 Renderers, CodeMirror 6 & WASM PDF
                          |
                          v
                 P7 Benchmarks, Playwright E2E & Static Deploy
```

### Phase 0: Repo Hygiene, Toolchain & Test Harness
- Align `deps.edn` and `package.json` (`shadow-cljs` version parity;
  verify `reagent 2.0.1` + `react 18.2.0` vs `19.2.0` compatibility).
- Add missing deps (`datascript`, `transit-cljs`, `reitit`, `test.check`,
  `day8.re-frame/test`, `core.async`, `clj-yaml`, `binaryage/devtools`,
  `@sqlite.org/sqlite-wasm`, Tailwind CSS v4 `@tailwindcss/cli`).
- Move `public/js/index.html` to `public/index.html`; configure
  `shadow-cljs.edn` `:init-fn`, `:devtools`, `:dev-http` with COOP/COEP
  headers (`same-origin` / `require-corp`) for OPFS, `:web-worker`
  module stub, and `:karma` / `:node-test` builds; add `bb.edn` tasks.
- **Done Gate**: Hot reload works, Tailwind v4 compiles, empty test runs.

### Phase 1: Malli Schemas, Canonical CV & Fake Generators
- Define `.cljc` Malli schemas extending [schema.md][schema-md] (facts,
  `:cv/config`, `:cv/canonical`, `:cv/style`, CSL-JSON citations).
- Implement `easy-cv.gen.cv` (`:student`, `:junior`, `:mid`, `:senior`,
  `:vp`, `:exec`).
- **Done Gate**: `malli.generator` generates 100 valid CVs per tier via
  `tc/quick-check` with zero schema failures.

### Phase 2: DataScript Fact Store, Versioning & Pure Solver
- Implement `easy-cv.db` (DataScript schema, `d/listen!` version atom,
  epochal snapshot capture/restore) and `easy-cv.solver.core`.
- **Done Gate**: PBT proves non-mutation, exclusion supremacy, override
  locality, and deterministic ordering; Query/Solver benchmark `< 5 ms`.

### Phase 3: Web Worker Bridge, SQLite WASM & SQL JSON Export
- Implement `easy-cv.utils.worker` (Transit + `core.async`, adapted from
  `bb-web-ds-tools.utils.worker`) and `easy-cv.workers.persistence`
  (`@sqlite.org/sqlite-wasm` choosing `OpfsDb` else `DB` + HoneySQL).
- Implement `easy-cv.render.sql-export` for full SQL tables JSON dump.
- **Done Gate**: Worker unit tests (mock `js/Worker`), async SQLite
  persistence test, and SQL<->DataScript round-trip PBT pass.

### Phase 4: I/O Parsers & Validators
- Implement EDN, YAML (`clj-yaml` / `js-yaml`), Schema.org JSON-LD
  mapper, and BibTeX/Zotero CSL-JSON importer (`@citation-js/core` +
  `plugin-bibtex` or Zotero native CSL-JSON).
- **Done Gate**: Round-trip PBT passes; invalid inputs return structured
  Malli human-readable errors without throwing.

### Phase 5: Re-frame Shell, Clojure CSS Maps & Core Views
- Port `theme`, `utils.themes`, `events.theme`, `components.common`,
  `components.layout`, and `navigation` from `bb-web-ds-tools`.
- Build isolated slices: `views.archive`, `views.solver`, `views.styles`
  with static Tailwind v4 class maps and design/font/layout presets.
- **Done Gate**: `day8.re-frame/test` confirms zero cross-slice `app-db`
  leakage; UI renders all 4 wireframe views cleanly.

### Phase 6: Multi-Format Renderers, CodeMirror 6 & WASM PDF
- Implement pure `.cljc` renderers (`html`, `site` zip, `print` CSS,
  `latex` `.tex`/`.bib`, `markdown`, `jsonld`).
- Integrate CodeMirror 6 (`nextjournal/clojure-mode`, YAML, and
  `codemirror-lang-latex`) with live Malli diagnostics in `views.editor`.
- Wire optional lazy `workers.typeset` (`texlyre-busytex` / `typst.ts`).
- **Done Gate**: Escaping/rendering PBT passes; renderer benchmark `< 10ms`.

### Phase 7: Benchmarks, E2E Print/Export & Static Release
- Add Playwright E2E suite testing offline persistence, multi-CV switch,
  HTML/ZIP/JSON-LD/SQL-JSON downloads, and PDF print output.
- Publish benchmark baseline report in CI and static production bundle.
- **Done Gate**: All unit, PBT, Re-frame, E2E, and benchmark SLAs green.

[readme]: ../../README.md
[clj-md]: ../clojure.md
[schema-md]: ../../wiki/docs/schema.md
[schema-org]: https://schema.org
