// License: GPL. For details, see LICENSE file.
package org.openhistoricalmap.josm.plugins.timefilter;

import static org.openstreetmap.josm.tools.I18n.tr;

import java.text.NumberFormat;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import javax.swing.JOptionPane;

import org.openhistoricalmap.josm.plugins.timefilter.classify.ClassificationCache;
import org.openhistoricalmap.josm.plugins.timefilter.classify.Classifier;
import org.openhistoricalmap.josm.plugins.timefilter.classify.PrimitiveKey;
import org.openhistoricalmap.josm.plugins.timefilter.classify.RelationPropagator;
import org.openhistoricalmap.josm.plugins.timefilter.classify.WayNodePropagator;
import org.openhistoricalmap.josm.plugins.timefilter.paint.OhmTierStyleSource;
import org.openhistoricalmap.josm.plugins.timefilter.paint.StyleRegistration;
import org.openhistoricalmap.josm.plugins.timefilter.model.DateRange;
import org.openhistoricalmap.josm.plugins.timefilter.model.OhmDate;
import org.openhistoricalmap.josm.plugins.timefilter.model.Tier;
import org.openhistoricalmap.josm.plugins.timefilter.model.TimeWindow;
import org.openhistoricalmap.josm.plugins.timefilter.parse.DateParser;
import org.openhistoricalmap.josm.plugins.timefilter.parse.PrimitiveDateExtractor;
import org.openhistoricalmap.josm.plugins.timefilter.pref.TimeFilterPreferences;
import org.openstreetmap.josm.data.osm.DataSet;
import org.openstreetmap.josm.data.osm.Node;
import org.openstreetmap.josm.data.osm.OsmPrimitive;
import org.openstreetmap.josm.data.osm.Relation;
import org.openstreetmap.josm.data.osm.Way;
import org.openstreetmap.josm.data.osm.event.DataChangedEvent;
import org.openstreetmap.josm.gui.MainApplication;
import org.openstreetmap.josm.gui.MapFrame;
import org.openstreetmap.josm.gui.Notification;
import org.openstreetmap.josm.gui.layer.OsmDataLayer;
import org.openstreetmap.josm.gui.util.GuiHelper;
import org.openstreetmap.josm.tools.Logging;

/**
 * Orchestrates the parse -> classify -> apply-flags -> repaint pipeline.
 *
 * Threading: classification runs on {@link MainApplication#worker} so the
 * EDT stays responsive on large layers. Once classification completes we
 * mutate {@code disabled} / {@code disabledAndHidden} flags on each
 * primitive (under {@code beginUpdate}/{@code endUpdate}), then fire
 * {@code fireFilterChanged} and invalidate the edit layer to trigger a
 * repaint via JOSM's standard render pipeline.
 *
 * Why flags instead of a custom MapPaint style? JOSM's renderer already
 * honors these per-primitive flags: BRIGHT renders normally, NORMAL/FAINT
 * (disabled) renders desaturated, and FAINT (disabledAndHidden) is skipped
 * entirely. Going through the flag API gives correct results without
 * fighting the cascade ordering against the user's base style.
 *
 * Caveat: JOSM's own Filter dialog uses the same flags. Running both at
 * the same time on the same dataset will conflict; this plugin clears
 * its flags on Clear / on plugin shutdown.
 */
public final class TimeFilterController {

    private final AtomicReference<Result> lastResult = new AtomicReference<>(Result.EMPTY);
    private final OhmTierStyleSource styleSource = new OhmTierStyleSource();
    private volatile boolean active;
    private volatile boolean styleAttached;
    /**
     * Selection captured at the moment the filter was first activated.
     * Restored on {@link #clear()} so users don't lose their selection to
     * primitives that became FAINT (and got auto-deselected) during Apply.
     * {@code null} when no filter is active.
     */
    private volatile Set<OsmPrimitive> preFilterSelection;

    public TimeFilterController() {}

    public boolean isActive() { return active; }
    public Result getLastResult() { return lastResult.get(); }

    /**
     * Parse the user inputs and apply. Returns synchronously after
     * scheduling the worker; the actual classification + repaint happens
     * asynchronously and the {@code onComplete} callback fires on the EDT.
     */
    public void apply(String setPointRaw, int offsetDays, Runnable onComplete) {
        OhmDate setPoint = DateParser.parse(setPointRaw);
        if (setPoint == null) {
            lastResult.set(Result.invalid("Could not parse '" + setPointRaw + "' as a date."));
            org.openstreetmap.josm.gui.util.GuiHelper.runInEDT(onComplete);
            return;
        }
        TimeWindow window;
        try {
            window = new TimeWindow(setPoint, offsetDays);
        } catch (IllegalArgumentException e) {
            lastResult.set(Result.invalid(e.getMessage()));
            org.openstreetmap.josm.gui.util.GuiHelper.runInEDT(onComplete);
            return;
        }

        DataSet dataSet = activeDataSet();
        if (dataSet == null) {
            lastResult.set(Result.invalid("No active OSM data layer."));
            org.openstreetmap.josm.gui.util.GuiHelper.runInEDT(onComplete);
            return;
        }

        // First time the filter activates, snapshot the user's current
        // selection so we can restore it on Clear (FAINT primitives get
        // deselected during Apply).
        if (!active) {
            preFilterSelection = new HashSet<>(dataSet.getSelected());
        }

        MainApplication.worker.submit(() -> {
            try {
                // Re-apply must start from a clean baseline, otherwise the
                // monotonic-escalation rule (we only ever hide, never reveal)
                // would let a previous Apply's hides leak into this one.
                // Reset our flag mutations, restore JOSM's own filter state,
                // then escalate based on the new classification.
                resetToBaseline(dataSet);
                Result r = classifyAndApply(dataSet, window);
                lastResult.set(r);
                active = true;
                ensureStyleAttached();
                org.openstreetmap.josm.gui.util.GuiHelper.runInEDT(onComplete);
            } catch (RuntimeException e) {
                Logging.error(e);
                lastResult.set(Result.invalid("Classification failed: " + e.getMessage()));
                org.openstreetmap.josm.gui.util.GuiHelper.runInEDT(onComplete);
            }
        });
    }

    private static void resetToBaseline(DataSet ds) {
        ds.beginUpdate();
        try {
            for (OsmPrimitive p : iterateAll(ds)) {
                p.unsetDisabledState();
            }
        } finally {
            ds.endUpdate();
        }
        triggerJosmFilterRefresh(ds);
    }

    /**
     * Clear all disabled flags and invalidate the layer. Synchronous.
     *
     * If JOSM's own Filter dialog has filters active, it'll re-apply them
     * automatically — we ping its {@code dataChanged} handler so the user
     * doesn't have to click "Update" on the filter panel.
     */
    public void clear() {
        DataSet ds = activeDataSet();
        Set<OsmPrimitive> snapshot = preFilterSelection;
        if (ds != null) {
            ds.beginUpdate();
            try {
                for (OsmPrimitive p : iterateAll(ds)) {
                    p.unsetDisabledState();
                }
                ds.clearMappaintCache();
            } finally {
                ds.endUpdate();
            }
            triggerJosmFilterRefresh(ds);
            // Restore the pre-filter selection. Some of those primitives may
            // have been deleted in the meantime; skip them.
            if (snapshot != null && !snapshot.isEmpty()) {
                List<OsmPrimitive> alive = new ArrayList<>();
                for (OsmPrimitive p : snapshot) {
                    if (!p.isDeleted()) alive.add(p);
                }
                if (!alive.isEmpty()) ds.setSelected(alive);
            }
        }
        if (styleAttached) {
            StyleRegistration.detach(styleSource);
            styleAttached = false;
        }
        invalidateEditLayer();
        active = false;
        preFilterSelection = null;
        ClassificationCache.set(ClassificationCache.EMPTY);
        lastResult.set(Result.EMPTY);
    }

    private void ensureStyleAttached() {
        if (!styleAttached) {
            StyleRegistration.attach(styleSource);
            styleAttached = true;
        } else {
            StyleRegistration.clearLayerStyleCaches();
        }
    }

    private static void triggerJosmFilterRefresh(DataSet ds) {
        MapFrame map = MainApplication.getMap();
        if (map == null || map.filterDialog == null) return;
        try {
            map.filterDialog.dataChanged(new DataChangedEvent(ds));
        } catch (RuntimeException e) {
            Logging.warn(e);
        }
    }

    /**
     * Filter the active layer using the current JOSM selection's
     * {@code start_date} / {@code end_date} tags as the focus point.
     * Convenience overload that uses the offset persisted in
     * {@link TimeFilterPreferences} (defaulting to 0). Equivalent to
     * what the dialog's "Filter to Selection" button does.
     *
     * <p>This is part of the plugin's stable public API — see
     * {@link TimeFilterPlugin#filterToSelection()}.</p>
     *
     * @param onComplete optional callback fired on the EDT after the
     *                   filter has applied (or after an early-failure
     *                   notification has been shown). May be {@code null}.
     */
    public void filterToSelection(Runnable onComplete) {
        filterToSelection(TimeFilterPreferences.loadOffsetDays(0), onComplete);
    }

    /**
     * Filter the active layer using the current JOSM selection's
     * {@code start_date} / {@code end_date} tags as the focus point,
     * with an explicit ± window offset.
     *
     * <p>Failures (no active layer, no selection, no parseable dates)
     * are surfaced via JOSM Notifications. After a successful apply,
     * a follow-up Notification fires if any of the originally-selected
     * primitives ended up FAINT (hidden from view).</p>
     *
     * @param offsetDays the ± window offset in days; persisted to
     *                   preferences as a side effect of the apply.
     * @param onComplete optional EDT-bound callback fired after the
     *                   filter has applied (or after the early-failure
     *                   notification, if there was one).
     */
    public void filterToSelection(int offsetDays, Runnable onComplete) {
        DataSet ds = activeDataSet();
        if (ds == null) {
            notifyError(tr("No active OSM data layer."));
            runOnEdt(onComplete);
            return;
        }
        Collection<OsmPrimitive> selected = ds.getSelected();
        if (selected.isEmpty()) {
            notifyError(tr("Nothing selected."));
            runOnEdt(onComplete);
            return;
        }
        Long focusEpoch = computeFocusEpoch(selected);
        if (focusEpoch == null) {
            notifyError(tr("Selection has no defined dates."));
            runOnEdt(onComplete);
            return;
        }

        LocalDate focus = LocalDate.ofEpochDay(focusEpoch);
        String formatted = formatLocalDate(focus);
        Set<OsmPrimitive> snapshot = new HashSet<>(selected);
        TimeFilterPreferences.save(formatted, offsetDays);
        apply(formatted, offsetDays, () -> {
            int hidden = 0;
            for (OsmPrimitive p : snapshot) {
                if (p.isDisabledAndHidden()) hidden++;
            }
            if (hidden > 0) {
                NumberFormat nf = NumberFormat.getIntegerInstance();
                notifyError(tr("Filter date hides {0} of {1} selected items.",
                        nf.format(hidden), nf.format(snapshot.size())));
            }
            if (onComplete != null) onComplete.run();
        });
    }

    /**
     * Compute a focus-day epoch (in {@link LocalDate#ofEpochDay} terms)
     * from a JOSM selection. Returns {@code null} if no member of the
     * selection has a defined {@code start_date} or {@code end_date}.
     *
     * <p>Single-primitive special case: if exactly one primitive is
     * selected and it has only one of (start, end) defined, that
     * endpoint is returned directly. Otherwise the focus is the
     * arithmetic mean of every defined endpoint across the selection
     * (open ends are skipped).</p>
     *
     * <p>Public helper exposed for cross-plugin use; the dialog button
     * and {@link TimeFilterPlugin#filterToSelection()} both go through
     * the {@link #filterToSelection(Runnable)} entry points which call
     * this internally.</p>
     */
    public static Long computeFocusEpoch(Collection<? extends OsmPrimitive> selected) {
        if (selected.size() == 1) {
            OsmPrimitive only = selected.iterator().next();
            DateRange r = PrimitiveDateExtractor.extract(only);
            if (r.isUnparseable()) return null;
            boolean startDef = !r.getStart().isInfinity();
            boolean endDef = !r.getEnd().isInfinity();
            if (startDef && !endDef) return r.getStart().earliestEpochDay();
            if (!startDef && endDef) return r.getEnd().latestEpochDay();
            if (startDef && endDef) {
                return (r.getStart().earliestEpochDay() + r.getEnd().latestEpochDay()) / 2;
            }
            return null;
        }
        long sum = 0;
        int count = 0;
        for (OsmPrimitive p : selected) {
            DateRange r = PrimitiveDateExtractor.extract(p);
            if (r.isUnparseable()) continue;
            if (!r.getStart().isInfinity()) {
                sum += r.getStart().earliestEpochDay();
                count++;
            }
            if (!r.getEnd().isInfinity()) {
                sum += r.getEnd().latestEpochDay();
                count++;
            }
        }
        if (count == 0) return null;
        return sum / count;
    }

    /**
     * Format a {@link LocalDate} in the {@code YYYY-MM-DD} (or
     * {@code -YYYY-MM-DD} for BCE) form that the dialog's filter-date
     * field accepts.
     */
    public static String formatLocalDate(LocalDate ld) {
        int y = ld.getYear();
        if (y < 0) {
            return String.format("-%04d-%02d-%02d", -y, ld.getMonthValue(), ld.getDayOfMonth());
        }
        return String.format("%04d-%02d-%02d", y, ld.getMonthValue(), ld.getDayOfMonth());
    }

    private static void notifyError(String msg) {
        new Notification(msg).setIcon(JOptionPane.ERROR_MESSAGE).show();
    }

    private static void runOnEdt(Runnable r) {
        if (r != null) GuiHelper.runInEDT(r);
    }

    /**
     * Re-run classification with the current window. No-op if not active.
     * Used by the data-change listener.
     */
    public void refreshIfActive(TimeWindow window) {
        if (!active) return;
        DataSet dataSet = activeDataSet();
        if (dataSet == null) return;
        MainApplication.worker.submit(() -> {
            try {
                resetToBaseline(dataSet);
                Result r = classifyAndApply(dataSet, window);
                lastResult.set(r);
                ensureStyleAttached();
            } catch (RuntimeException e) {
                Logging.error(e);
            }
        });
    }

    private static Result classifyAndApply(DataSet dataSet, TimeWindow window) {
        // Wrap the entire read-then-write pass in beginUpdate/endUpdate. The
        // worker thread iterating ds.getNodes() / getWays() / getRelations()
        // would otherwise race against EDT edits — JOSM's filter dialog uses
        // the same pattern (FilterModel.executeFilters). beginUpdate is
        // reentrant, so the inner mutations don't conflict.
        Map<Long, Tier> byId = new HashMap<>();
        Set<Long> hasOwnDateTags = new HashSet<>();
        int unparseable = 0;
        List<OsmPrimitive> deselect = new ArrayList<>();
        ClassificationCache cache;

        dataSet.beginUpdate();
        try {
            for (OsmPrimitive p : iterateAll(dataSet)) {
                DateRange range = PrimitiveDateExtractor.extract(p);
                if (range.isUnparseable()) unparseable++;
                Tier t = Classifier.classify(range, window);
                // Tagless primitives (corner nodes of buildings, segments of
                // a multipolygon outer, etc.) only render as part of a parent
                // way/relation. Seed them as FAINT and rely on propagation to
                // lift them — otherwise the no-tags-means-always-present
                // default leaves them visible even when their parent is
                // hidden.
                if (!p.hasKeys() && t == Tier.BRIGHT) {
                    t = Tier.FAINT;
                }
                long key = PrimitiveKey.of(p);
                byId.put(key, t);
                // Track primitives that carry their own date tags so
                // propagation doesn't override their classification.
                // Critical for chronology relations: their child boundary
                // relations are independently date-tagged, and the
                // chronology's wide range shouldn't promote them.
                if (p.hasKey("start_date") || p.hasKey("end_date")) {
                    hasOwnDateTags.add(key);
                }
            }

            // Order matters: relation->member promotion first (so a way
            // inherits its multipolygon's tier before its own nodes inherit
            // from it), then way->node promotion.
            RelationPropagator.propagate(dataSet.getRelations(), byId, hasOwnDateTags);
            WayNodePropagator.propagate(dataSet.getWays(), byId, hasOwnDateTags);

            // Pre-count and publish the cache before mutating flags so
            // OhmTierStyleSource.apply sees the new tier assignments on the
            // very first paint pass that follows.
            int bright = 0, normal = 0, faint = 0;
            for (Tier t : byId.values()) {
                switch (t) {
                    case BRIGHT: bright++; break;
                    case NORMAL: normal++; break;
                    case FAINT:  faint++;  break;
                }
            }
            cache = new ClassificationCache(byId, bright, normal, faint, unparseable);
            ClassificationCache.set(cache);

            // Mutate per-primitive flags. Only FAINT gets touched (set
            // disabled+hidden); NORMAL stays selectable and receives its
            // visual fade through the registered MapPaint style.
            for (OsmPrimitive p : iterateAll(dataSet)) {
                Tier t = byId.getOrDefault(PrimitiveKey.of(p), Tier.FAINT);
                if (t == Tier.FAINT && !p.isDisabledAndHidden()) {
                    p.setDisabledState(true);
                    if (p.isSelected()) deselect.add(p);
                }
            }
            dataSet.clearMappaintCache();
        } finally {
            dataSet.endUpdate();
        }
        if (!deselect.isEmpty()) {
            dataSet.clearSelection(deselect);
        }
        invalidateEditLayer();

        return new Result(true, null, cache, window);
    }

    private static void invalidateEditLayer() {
        if (MainApplication.getLayerManager() == null) return;
        MainApplication.getLayerManager().invalidateEditLayer();
    }

    private static Iterable<OsmPrimitive> iterateAll(DataSet ds) {
        return () -> {
            java.util.Iterator<Node> nIt = ds.getNodes().iterator();
            java.util.Iterator<Way> wIt = ds.getWays().iterator();
            java.util.Iterator<Relation> rIt = ds.getRelations().iterator();
            return new java.util.Iterator<OsmPrimitive>() {
                @Override public boolean hasNext() {
                    return nIt.hasNext() || wIt.hasNext() || rIt.hasNext();
                }
                @Override public OsmPrimitive next() {
                    if (nIt.hasNext()) return nIt.next();
                    if (wIt.hasNext()) return wIt.next();
                    return rIt.next();
                }
            };
        };
    }

    private static DataSet activeDataSet() {
        if (MainApplication.getLayerManager() == null) return null;
        List<OsmDataLayer> layers = MainApplication.getLayerManager().getLayersOfType(OsmDataLayer.class);
        if (layers.isEmpty()) return null;
        OsmDataLayer active = MainApplication.getLayerManager().getEditLayer();
        if (active != null) return active.data;
        return layers.get(0).data;
    }

    /** Result of an Apply call, exposed to the UI for status display. */
    public static final class Result {
        public static final Result EMPTY = new Result(true, null, ClassificationCache.EMPTY, null);

        public final boolean ok;
        public final String error;
        public final ClassificationCache cache;
        public final TimeWindow window;

        public Result(boolean ok, String error, ClassificationCache cache, TimeWindow window) {
            this.ok = ok;
            this.error = error;
            this.cache = cache;
            this.window = window;
        }

        static Result invalid(String error) {
            return new Result(false, error, ClassificationCache.EMPTY, null);
        }
    }
}
