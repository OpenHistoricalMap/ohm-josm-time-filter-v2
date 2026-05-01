# TODO

Open items, with the bigger pass of follow-up work already done. The
items below are either deferred (waiting on user or external action) or
research-grade (worth doing, low immediate ROI).

## Deferred — needs user / host action

### i18n translation files

All user-facing strings already pass through `tr(...)` so the code is
translation-ready. There's no `.lang` / `.po` toolchain yet, and setting
one up preemptively (without a translator interested) is sunk cost.

When a translator turns up:

1. Generate a POT template by extracting strings from source. JOSM core's
   `i18n.xml` Ant target does this for core; we'd want a parallel target
   in our `build.xml` calling `xgettext` over `src/`.
2. Translators contribute `<lang>.po` files under `data/` or `i18n/`.
3. Compile to `.mo` and bundle in the jar at `data/<lang>.mo`.
4. JOSM's runtime auto-picks the right `.mo` based on the user's locale.

Start with a single language (e.g. `de.po`) before generalising the
build pipeline.

## Research / observe

### Profile on real OHM data

Synthetic micro-bench (`PipelinePerformanceTest`) puts 100k primitives +
1k relations + 30k ways at ~300 ms total wall-time on a developer
laptop, well under the felt-instant threshold. The real test is JOSM
+ `test_chronologies.osm` (158 MB, ~1.5M primitives), which hits the
JOSM-types adapter overhead the micro-bench doesn't measure. Worth
running once and recording the number; if it's >1 second per Apply, the
shift-button-spam UX would feel laggy and we'd want to look at:

- Caching `start_date`/`end_date` parses per-tag-string (lots of repeats
  in chronology data — every member of a chronology often shares the
  same dates with its siblings).
- Skipping primitives that haven't changed since the last classify.
- Splitting classification across multiple worker threads.

### Confirm worker-thread iteration is genuinely safe under concurrent edit

The classify-then-mutate loop is now wrapped in
`dataSet.beginUpdate()` / `endUpdate()`, matching JOSM's own
`FilterModel.executeFilters` pattern. That should be enough — JOSM's
own filter dialog has used this for years without reported races — but
worth a manual stress test (drag a node continuously while Apply
fires) before claiming this is bulletproof.

## Done in this pass

- ~~Shift-button definitions consolidated into a static `Shift[]` array,
  iterated in `buildShiftButtonRow`~~
- ~~Spinner shortcut-disable trampoline extracted into
  `ShortcutSafeFocus.installOn(JTextField)`~~
- ~~`classifyAndApply` wrapped in `beginUpdate()`/`endUpdate()` for
  thread-safe iteration~~
- ~~Date-field tooltip explains partial-date resolution~~
- ~~Selection restoration on Clear (`preFilterSelection` snapshot
  taken on first Apply, restored on Clear, dead primitives skipped)~~
- ~~Bundled dialog icon redrawn as a clock-in-funnel matching the
  user-provided icon palette~~
- ~~End-to-end integration test (`IntegrationPipelineTest`) covers
  multipolygon-outer lift, chronology non-lift, paint-shop FAINT
  cascade, boundary inclusivity, tagged-node-with-own-dates
  authority, and `PrimitiveKey` collision~~
- ~~Synthetic perf benchmark (`PipelinePerformanceTest`) — 100k prims +
  1k rels + 30k ways in ~300 ms~~
- ~~Mid-session disable lifecycle confirmed (`Plugin.java` has no
  `destroy()` hook); rationale documented in `TimeFilterPlugin`
  class javadoc~~
- ~~README screenshot moved in-repo (`docs/screenshot.png`); README's
  `<img src>` now points at the relative path~~
