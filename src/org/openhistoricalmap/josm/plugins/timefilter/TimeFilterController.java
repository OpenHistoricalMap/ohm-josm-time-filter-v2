// License: GPL. For details, see LICENSE file.
package org.openhistoricalmap.josm.plugins.timefilter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

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
import org.openstreetmap.josm.data.osm.DataSet;
import org.openstreetmap.josm.data.osm.Node;
import org.openstreetmap.josm.data.osm.OsmPrimitive;
import org.openstreetmap.josm.data.osm.Relation;
import org.openstreetmap.josm.data.osm.Way;
import org.openstreetmap.josm.data.osm.event.DataChangedEvent;
import org.openstreetmap.josm.gui.MainApplication;
import org.openstreetmap.josm.gui.MapFrame;
import org.openstreetmap.josm.gui.layer.OsmDataLayer;
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
        }
        if (styleAttached) {
            StyleRegistration.detach(styleSource);
            styleAttached = false;
        }
        invalidateEditLayer();
        active = false;
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
        Map<Long, Tier> byId = new HashMap<>();
        Set<Long> hasOwnDateTags = new HashSet<>();
        int unparseable = 0;

        for (OsmPrimitive p : iterateAll(dataSet)) {
            DateRange range = PrimitiveDateExtractor.extract(p);
            if (range.isUnparseable()) unparseable++;
            Tier t = Classifier.classify(range, window);
            // Tagless primitives (corner nodes of buildings, segments of a
            // multipolygon outer, etc.) only render as part of a parent
            // way/relation. Seed them as FAINT and rely on propagation to
            // lift them — otherwise the no-tags-means-always-present default
            // leaves them visible even when their parent is hidden.
            if (!p.hasKeys() && t == Tier.BRIGHT) {
                t = Tier.FAINT;
            }
            long key = PrimitiveKey.of(p);
            byId.put(key, t);
            // Track primitives that carry their own date tags so
            // propagation doesn't override their classification. Critical
            // for chronology relations: their child boundary relations are
            // independently date-tagged, and the chronology's wide range
            // shouldn't promote them.
            if (p.hasKey("start_date") || p.hasKey("end_date")) {
                hasOwnDateTags.add(key);
            }
        }

        // Order matters: relation->member promotion first (so a way inherits
        // its multipolygon's tier before its own nodes inherit from it), then
        // way->node promotion.
        RelationPropagator.propagate(dataSet.getRelations(), byId, hasOwnDateTags);
        WayNodePropagator.propagate(dataSet.getWays(), byId, hasOwnDateTags);

        // Pre-count and publish the cache before mutating flags / triggering
        // repaint, so OhmTierStyleSource.apply sees the new tier assignments
        // on the very first paint pass that follows.
        int bright = 0, normal = 0, faint = 0;
        for (Tier t : byId.values()) {
            switch (t) {
                case BRIGHT: bright++; break;
                case NORMAL: normal++; break;
                case FAINT: faint++; break;
            }
        }
        ClassificationCache cache = new ClassificationCache(byId, bright, normal, faint, unparseable);
        ClassificationCache.set(cache);

        // Mutate per-primitive flags. Only FAINT gets touched (set
        // disabled+hidden); NORMAL stays selectable and receives its
        // visual fade through the registered MapPaint style.
        List<OsmPrimitive> deselect = new ArrayList<>();
        dataSet.beginUpdate();
        try {
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
