// License: GPL. For details, see LICENSE file.
package org.openhistoricalmap.josm.plugins.timefilter.paint;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.openstreetmap.josm.gui.MainApplication;
import org.openstreetmap.josm.gui.layer.MainLayerManager;
import org.openstreetmap.josm.gui.layer.OsmDataLayer;
import org.openstreetmap.josm.gui.mappaint.MapPaintStyles;
import org.openstreetmap.josm.spi.preferences.Config;
import org.openstreetmap.josm.tools.Logging;

/**
 * Lifecycle helper for {@link OhmTierStyleSource}.
 *
 * Why this exists:
 *   {@code MapPaintStyles.addStyle} unconditionally writes the new style
 *   into the {@code mappaint.style.entries} preference list. Our style
 *   has {@code url == null} (in-memory only), so the saved row is a
 *   blank-url stub that JOSM can't reload on the next start — it logs
 *   an error and shows a dead row in Preferences > Map Paint Styles.
 *
 *   Mitigation:
 *   1. {@link #cleanupStalePrefs()} runs at plugin construction to strip
 *      any stale row matching our marker name/title with empty url.
 *   2. {@link #attach}/{@link #detach} wrap the add/remove and clear the
 *      layer paint cache so the change takes effect immediately.
 */
public final class StyleRegistration {

    private StyleRegistration() {}

    public static void attach(OhmTierStyleSource source) {
        MapPaintStyles.addStyle(source);
        clearLayerStyleCaches();
    }

    public static void detach(OhmTierStyleSource source) {
        try {
            MapPaintStyles.removeStyle(source);
        } catch (RuntimeException e) {
            Logging.warn(e);
        }
        // Belt-and-braces: also strip any saved row referencing our marker.
        cleanupStalePrefs();
        clearLayerStyleCaches();
    }

    /** Idempotent: safe to call when nothing is stale. */
    public static void cleanupStalePrefs() {
        List<Map<String, String>> raw = Config.getPref().getListOfMaps(
                "mappaint.style.entries", null);
        if (raw == null || raw.isEmpty()) return;
        List<Map<String, String>> filtered = new ArrayList<>(raw);
        boolean changed = false;
        Iterator<Map<String, String>> it = filtered.iterator();
        while (it.hasNext()) {
            Map<String, String> entry = it.next();
            String url = entry.getOrDefault("url", "");
            String ptoken = entry.getOrDefault("ptoken", "");
            String title = entry.getOrDefault("title", "");
            if (url.isEmpty()
                    && (OhmTierStyleSource.DISPLAY_NAME.equals(ptoken)
                        || OhmTierStyleSource.TITLE.equals(title))) {
                it.remove();
                changed = true;
            }
        }
        if (changed) {
            Config.getPref().putListOfMaps("mappaint.style.entries", filtered);
        }
    }

    /** Force every OSM data layer to recompute styles on next paint. */
    public static void clearLayerStyleCaches() {
        if (MainApplication.getLayerManager() == null) return;
        MainLayerManager mgr = MainApplication.getLayerManager();
        for (OsmDataLayer layer : mgr.getLayersOfType(OsmDataLayer.class)) {
            layer.data.clearMappaintCache();
        }
        if (MainApplication.getMap() != null && MainApplication.getMap().mapView != null) {
            MainApplication.getMap().mapView.repaint();
        }
    }
}
