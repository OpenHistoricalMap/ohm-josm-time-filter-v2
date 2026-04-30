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
 * The in-memory paint style needs a one-time cleanup of any phantom row
 * in {@code mappaint.style.entries} that a crashed previous session
 * could have left behind.
 */
public class TimeFilterPlugin extends Plugin {

    private final TimeFilterController controller;
    private final TimeFilterDialog dialog;
    private final DataLayerListener layerListener;

    public TimeFilterPlugin(PluginInformation info) {
        super(info);

        StyleRegistration.cleanupStalePrefs();

        this.controller = new TimeFilterController();
        this.dialog = new TimeFilterDialog(controller);
        this.layerListener = new DataLayerListener(
                ds -> {/* on active layer change: nothing extra to do */},
                dialog::onDataChanged);

        // Mid-session install: JOSM does not call mapFrameInitialized
        // retroactively for an already-open MapFrame, so wire up here
        // if one exists.
        MapFrame existing = MainApplication.getMap();
        if (existing != null) {
            existing.addToggleDialog(dialog);
            layerListener.install();
        }
    }

    @Override
    public void mapFrameInitialized(MapFrame oldFrame, MapFrame newFrame) {
        if (oldFrame == null && newFrame != null) {
            newFrame.addToggleDialog(dialog);
            layerListener.install();
        } else if (oldFrame != null && newFrame == null) {
            layerListener.uninstall();
            controller.clear();
        } else if (oldFrame != null && newFrame != null) {
            newFrame.addToggleDialog(dialog);
        }
    }
}
