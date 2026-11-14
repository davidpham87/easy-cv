# Easy CV Design Documentation

This directory holds the modular architecture and implementation plan for
**Easy CV**, a browser-local, data-oriented ClojureScript single-page app
(SPA) for authoring CVs as a collection of verifiable facts.

## Core Problem and Separation of Concerns

As defined in [the project README][project-readme], CV authoring normally
entangles three distinct activities, creating high cognitive load and error
risk on every edit:

1. **Fact Archiving**: Collecting durable facts about work, education,
   skills, projects, publications, and hobbies.
2. **Job-Specific Selection**: Choosing and ordering the subset of facts
   relevant to a target role (LLM matching is explicitly out of scope,
   though structured I/O supports LLM-assisted workflows).
3. **Multi-Format Styling and Rendering**: Producing spotless LaTeX, PDF,
   HTML (single-page or static site), Markdown, and structured data.

Easy CV decomplects these three responsibilities behind immutable data
boundaries, local browser persistence (zero telemetry, zero server), and
Re-frame isolation.

## Document Index

| Doc | Scope | Key Concerns |
| --- | --- | --- |
| [00-overview][doc-00] | Architecture | Re-frame isolation, build, reuse |
| [01-data-model][doc-01] | Data & Storage | DataScript, Malli, SQLite, OPFS |
| [02-io-editors][doc-02] | I/O & Workers | Schema.org, BibTeX, Gen, Editor |
| [03-solver-ui][doc-03] | Solver & UI | Multi-CV solver, Tailwind, CLJ CSS |
| [04-rendering][doc-04] | Render & Plan | LaTeX/Typst WASM, PBT, Phases 0-7 |

## Cross-Document Dataflow

```text
+-------------------+     +--------------------+     +-----------------------+
| 02: I/O & Editors | --> | 01: Fact Store     | --> | 03: Multi-CV Solver   |
| EDN/YAML/JSON-LD  |     | DataScript (RAM)   |     | Selection + Overrides |
| BibTeX / CSL-JSON |     | + SQLite WASM      |     | + Layout/Theme Config |
+-------------------+     +--------------------+     +-----------------------+
                                 ^    |                          |
                                 |    v                          v
                          +--------------------+     +-----------------------+
                          | 01: Snapshots/JSON |     | 04: Pure Renderers    |
                          | Transit + OPFS/IDB |     | HTML/LaTeX/PDF/MD/LD  |
                          +--------------------+     +-----------------------+
```

## Architectural Tenets

Following [docs/clojure.md][clojure-doc] (Rich Hickey design principles) and
Re-frame discipline, every module obeys six rules:

1. **Decomplect Logic, State, and I/O**: Pure `.cljc` cores transform plain
   data; Re-frame effect handlers (`reg-fx`) and Web Workers perform all I/O.
2. **Epochal Time Model**: Identity (`conn`) points to a succession of
   immutable DataScript database values (`d/db`). Versioning and undo are
   snapshot values, never in-place mutation.
3. **Open Maps and Accretion**: Grow schemas by adding namespaced keys
   (accretion) rather than breaking existing facts. Reject unknown keys only
   at strict external export boundaries.
4. **Canonical Data over Code**: Plain maps, vectors, sets, and keywords
   form the single intermediate representation (IR) shared by all renderers.
5. **Small Re-frame Blast Radius**: Partition `:app-db` by domain, co-locate
   view subs/events, layer subscriptions, and forbid cross-view coupling.
6. **Test-First with Generative PBT**: Validate invariants via Malli +
   `test.check` and `day8.re-frame/test` in small, frequent git commits.

## Baseline Audit and Sibling Reuse

Inspection of the current repository and `../bb-web-ds-tools` revealed:

- **Existing stubs**: `src/example/core.cljc`, `test/example/core_test.clj`,
  `public/js/index.html`, and draft schema in [wiki/docs/schema.md][schema].
- **Build gaps to close**: `deps.edn` pins `shadow-cljs` `3.2.1` while
  `package.json` pins `^3.3.2` and `react` `^19.2.0` (whereas `reagent`
  `2.0.1` in `bb-web-ds-tools` runs cleanly on `react` `^18.2.0`). Also
  `public/js/index.html` must move to `public/index.html`.
- **Direct reuse from `../bb-web-ds-tools`**: App shell, Reitit routing,
  Tailwind theme tokens, common UI components, Monaco wrapper, Malli &
  HoneySQL `.cljc` helpers, DataScript `ds-version` reactive bridge,
  Transit worker bus, and `@sqlite.org/sqlite-wasm` OPFS worker.

[project-readme]: ../../README.md
[clojure-doc]: ../clojure.md
[schema]: ../../wiki/docs/schema.md
[doc-00]: ./00-overview-and-architecture.md
[doc-01]: ./01-data-model-and-storage.md
[doc-02]: ./02-io-parsers-editors-and-workers.md
[doc-03]: ./03-solver-ui-and-styling.md
[doc-04]: ./04-rendering-export-and-roadmap.md
