# Easy CV Hammock Design Brief

## Problem

Authoring CVs entangles three unrelated concerns: archiving durable career
facts, selecting job-relevant facts per target CV, and styling/rendering into
PDF, LaTeX, HTML, and structured data. Mixing them creates high cognitive load
and fragility across CV edits.

## Facts

- Existing `README.md` mandates pure browser-local execution, zero telemetry,
  and strict separation of Facts, Selection, and Styling.
- Existing `wiki/docs/schema.md` defines draft DataScript keys (`:item/uuid`,
  `:person/*`, `:org/*`, `:history/*`, `:skill/*`, `:project/*`, `:document/*`,
  `:hobby/*`, `:cv/*`).
- Sibling repo `../bb-web-ds-tools` already implements DataScript `ds-version`,
  Re-frame/Reitit shell, Tailwind CSS var themes, Malli helpers, HoneySQL,
  Transit worker bus, and `@sqlite.org/sqlite-wasm` OPFS/IDB workers.
- Baseline `package.json` (`react ^19.2.0`) and `deps.edn` (`shadow-cljs`
  `3.2.1`) have version mismatches and `public/js/index.html` is misplaced.

## Alternatives

1. **Monolithic Re-frame App DB Only**: Store all facts, configs, and derived
   CVs in one flat Re-frame atom. Rejected: loses relational Datalog queries,
   snapshot time-travel, and causes large blast radius across views.
2. **SQLite WASM Only Without DataScript**: Run HoneySQL queries over worker
   RPC for every UI interaction. Rejected: async worker round-trips add latency
   to reactive Reagent subscriptions and interactive multi-CV solving.
3. **Hybrid Epochal DataScript + Worker SQLite WASM + Pure `.cljc` Core**:
   Chosen. DataScript handles synchronous reactive RAM queries and snapshots;
   pure `.cljc` core handles Malli schemas, solving, and rendering; workers
   handle SQLite WASM OPFS/IDB persistence, heavy parsing, and WASM typeset.

## Decomplect

- Partition `src/easy_cv/core/` (pure `.cljc`), `src/easy_cv/fx/` (imperative
  `reg-fx` shell), `src/easy_cv/ui/` (isolated Reagent/Re-frame slices), and
  `src/easy_cv/workers/` (`shadow-cljs` `:web-worker` modules).
- Keep `:app-db` top-level keys disjoint per domain (`:router`, `:archive`,
  `:solver`, `:editor`, `:render`, `:ui`, `:persistence`).
- Communicate across slices exclusively via Re-frame events and immutable
  DataScript `@conn` values.
