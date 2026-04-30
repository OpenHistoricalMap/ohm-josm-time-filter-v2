# TODO

Open items, roughly in order of value. Sorted into "ought to do soon" and
"someday / nice to have".

## Code improvements (worth doing)

### Shift-button definitions are 10 explicit calls

`TimeFilterDialog.buildShiftButtonRow()` repeats `makeShiftButton(...)`
ten times. A small `record (label, tooltip, ΔY, ΔM, ΔD)` array iterated in
a loop would be tighter and cheaper to extend. Marginal.

### Spinner shortcut-disable trampoline is a hack

The offset spinner's shortcut-safe focus listener (`TimeFilterDialog`,
"shortcutTrampoline") forwards focus events to a hidden
`DisableShortcutsOnFocusGainedTextField`. Works, but a clean version
would subclass `JFormattedTextField` to implement
`DisableShortcutsOnFocusGainedComponent` directly and use it as the
spinner's editor's text field.

### Verify worker-thread flag mutation is concurrency-safe

`TimeFilterController.classifyAndApply` calls `p.setDisabledState(...)`
from `MainApplication.worker` (not the EDT). JOSM's primitives use
internal write locks, so it should be safe, but worth confirming under a
concurrent edit (e.g., user dragging a node while a re-classify runs).

### Tooltip / docs for partial-date set point semantics

`OhmDate.pointEpochDay()` resolves a partial date like `1900` to its
*earliest* day (Jan 1 1900). A user typing `1900` and expecting "any
time in 1900" might be surprised that an object with `start_date=1900-12`
isn't considered "on this day" for set point `1900`. Either widen the
filter date tooltip with an example or document explicitly in the README.

## Future / nice-to-have

### Integration test for the full controller pipeline

The propagators, `Classifier`, `OhmDate`, and `DateParser` all have unit
tests. `TimeFilterController` does not, because it pulls in JOSM's static
init (which the project's tests deliberately avoid). A test that parses a
small `.osm` fixture by hand and runs `classifyAndApply` end-to-end would
catch regressions across the whole pipeline. Could live in a separate
`test/integration/` source set.

### Performance profiling on large layers

Each `Apply` (and each shift-button click) iterates every primitive,
classifies, and sets flags. On the 158 MB `test_chronologies.osm` fixture
this is tens of thousands of primitives per click. Async on the worker so
it doesn't block the EDT, but worth profiling to know the floor and
ceiling of responsiveness.

### Selection restoration on Clear

Primitives the filter hides (FAINT) are deselected during `classifyAndApply`.
After Clear, they're not re-selected. Snapshot the original selection
before Apply and restore on Clear, if it makes sense as a UX.

### Bundled dialog icon

`resources/images/dialogs/timefilter.svg` is a placeholder I made when
the plugin first refused to load without one. The user-provided icons
under `resources/images/ohmtimefilter/` are much nicer. Either reuse one
of those or commission a dedicated dialog icon.

### i18n translation files

All user-facing strings already pass through `tr()` so the code is
translation-ready, but no `.lang` / `.po` toolchain exists. Adding
translations would require setting up the JOSM i18n pipeline.

### README screenshot is hosted off-repo

The README's preview image is a Monosnap-uploaded URL. If the host
disappears the image breaks. Move to a repo-hosted file under `docs/` or
similar.

### Investigate JOSM's mid-session plugin disable behaviour

`TimeFilterPlugin`'s class javadoc explains why we have no `Plugin.destroy`
hook (JOSM doesn't expose one and disable-via-prefs requires a restart).
Worth confirming that's true in current JOSM, and whether any newer
hooks have been added that would let us clean up more proactively.
