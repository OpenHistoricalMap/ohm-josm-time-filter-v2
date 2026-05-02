// License: GPL. For details, see LICENSE file.
package org.openhistoricalmap.josm.plugins.timefilter;

import org.openhistoricalmap.josm.plugins.timefilter.listener.DataLayerListener;
import org.openhistoricalmap.josm.plugins.timefilter.paint.StyleRegistration;
import org.openhistoricalmap.josm.plugins.timefilter.ui.TimeFilterDialog;
import org.openstreetmap.josm.gui.MainApplication;
import org.openstreetmap.josm.gui.MapFrame;
import org.openstreetmap.josm.plugins.Plugin;
import org.openstreetmap.josm.plugins.PluginInformation;

/**
 * Plugin entry. Constructed once when JOSM loads the plugin jar.
 *
 * Tier visualisation is split between two mechanisms:
 *   - FAINT primitives use JOSM's per-primitive {@code disabled+hidden}
 *     flag (renderer skips them entirely; see {@link TimeFilterController}).
 *   - NORMAL primitives keep their flags clean (so they remain
 *     selectable) and are visually faded by an in-memory MapPaint style
 *     (see {@code OhmTierStyleSource}).
 *
 * Lifecycle:
 *   - Construction: {@link StyleRegistration#cleanupStalePrefs()} runs
 *     to strip any phantom row in {@code mappaint.style.entries} from a
 *     previous session that didn't get a chance to detach.
 *   - The {@link TimeFilterDialog} is rebuilt on every map-frame
 *     transition into a non-null frame. JOSM destroys a {@code
 *     ToggleDialog}'s internal title-bar when the owning MapFrame is
 *     closed, so the same dialog instance can't simply be re-docked
 *     in a freshly-created MapFrame: {@code ToggleDialog.dock()} would
 *     trip an NPE on {@code titleBar.setVisible(...)}. The controller
 *     (and its prefs / classification cache) persists across these
 *     transitions; only the UI shell needs to be reconstructed.
 *   - Shutdown: JOSM has no {@code Plugin.destroy()} hook. We use the
 *     final map-frame transition ({@code mapFrameInitialized(_, null)})
 *     as our shutdown signal — it fires when the last layer is closed
 *     during normal JOSM exit. For abnormal exits (crashes, force
 *     quit), anything we leak (primitive flags, the in-memory style
 *     instance in {@code MapPaintStyles}, the marker row in prefs) is
 *     recovered on the next startup: a fresh JOSM ignores the dataset's
 *     old flags, and {@code cleanupStalePrefs()} strips the marker row
 *     before we reattach. The architecture is "leak-safe by reset",
 *     not "leak-free at shutdown".
 *   - Mid-session disable via the Plugin Preferences pane requires a
 *     JOSM restart (JOSM's standard behaviour for plugin changes), so
 *     it reduces to the shutdown + startup path above.
 */
public class TimeFilterPlugin extends Plugin {

    /**
     * Singleton handle for the cross-plugin reflection entry point
     * {@link #filterToSelection()}. Set at the end of the constructor
     * so callers never observe a partially-built instance. {@code
     * volatile} so non-EDT callers see the most-recently-constructed
     * value.
     */
    private static volatile TimeFilterPlugin INSTANCE;

    private final TimeFilterController controller;
    private final DataLayerListener layerListener;
    /** Rebuilt on every transition into a fresh MapFrame; {@code null} between. */
    private TimeFilterDialog dialog;

    public TimeFilterPlugin(PluginInformation info) {
        super(info);

        StyleRegistration.cleanupStalePrefs();

        this.controller = new TimeFilterController();
        this.layerListener = new DataLayerListener(
                ds -> {/* on active layer change: nothing extra to do */},
                this::forwardDataChanged);

        // Mid-session install: JOSM does not call mapFrameInitialized
        // retroactively for an already-open MapFrame, so wire up here
        // if one exists.
        MapFrame existing = MainApplication.getMap();
        if (existing != null) {
            attachFreshDialog(existing);
            layerListener.install();
        }

        INSTANCE = this;
    }

    /**
     * Public API. Filter the active layer using the current JOSM
     * selection's {@code start_date} / {@code end_date} tags as the
     * focus point — equivalent to clicking the dialog's "Filter to
     * Selection" button.
     *
     * <p>Intended for invocation from other plugins via reflection,
     * with no compile-time dependency on this plugin:</p>
     *
     * <pre>{@code
     * try {
     *     Class<?> tfp = Class.forName(
     *         "org.openhistoricalmap.josm.plugins.timefilter.TimeFilterPlugin");
     *     tfp.getMethod("filterToSelection").invoke(null);
     * } catch (ReflectiveOperationException ignored) {
     *     // OHM_Time_Filter not loaded — silent no-op.
     * }
     * }</pre>
     *
     * <p>Behavior: derives a focus epoch from the selection (single
     * primitive with one open endpoint → that endpoint; otherwise →
     * arithmetic mean of every defined endpoint), then applies the
     * filter using that date and the offset persisted in preferences.
     * User-facing failures (no active layer, no selection, no
     * parseable dates, hidden items in the original selection) are
     * surfaced via JOSM Notifications.</p>
     *
     * <p>No-op if the plugin isn't fully initialised.</p>
     *
     * <p><b>Stable API since v0.3.0.</b></p>
     */
    public static void filterToSelection() {
        TimeFilterPlugin instance = INSTANCE;
        if (instance == null) return;
        instance.controller.filterToSelection(null);
    }

    @Override
    public void mapFrameInitialized(MapFrame oldFrame, MapFrame newFrame) {
        if (newFrame != null) {
            // Always build a fresh dialog. If we got here because the
            // last MapFrame was closed and a new one is being opened
            // (close-then-download flow), the previous dialog instance
            // had its title-bar destroyed and can't be re-docked.
            attachFreshDialog(newFrame);
            if (oldFrame == null) {
                layerListener.install();
            }
        } else if (oldFrame != null) {
            // Last layer closed.
            layerListener.uninstall();
            controller.clear();
            dialog = null;
        }
    }

    private void attachFreshDialog(MapFrame frame) {
        dialog = new TimeFilterDialog(controller);
        frame.addToggleDialog(dialog);
    }

    private void forwardDataChanged() {
        TimeFilterDialog d = dialog;
        if (d != null) d.onDataChanged();
    }
}
