// License: GPL. For details, see LICENSE file.
package org.openhistoricalmap.josm.plugins.timefilter.paint;

import java.awt.Color;
import java.util.Map;

import org.openhistoricalmap.josm.plugins.timefilter.classify.ClassificationCache;
import org.openhistoricalmap.josm.plugins.timefilter.model.Tier;
import org.openstreetmap.josm.data.osm.IPrimitive;
import org.openstreetmap.josm.gui.mappaint.Cascade;
import org.openstreetmap.josm.gui.mappaint.MultiCascade;
import org.openstreetmap.josm.gui.mappaint.StyleKeys;
import org.openstreetmap.josm.gui.mappaint.mapcss.MapCSSStyleSource;

/**
 * In-memory MapPaint style that fades NORMAL-tier primitives by
 * multiplying their cascade colors and opacity values.
 *
 * Used for the "in-window-but-not-set-point" tier: items the user wants
 * to see *and* click on (selectable), but visually de-emphasised so the
 * BRIGHT-tier items pop. Unlike using JOSM's {@code disabled} flag, a
 * cascade-level fade leaves {@code isDisabled()} false and therefore
 * keeps the primitive selectable.
 *
 * BRIGHT items are untouched (left at full intensity).
 * FAINT items are skipped here too — the controller takes care of those
 * via the {@code disabled+hidden} flag pair, so they don't render at all.
 *
 * Why subclass {@link MapCSSStyleSource} (with empty CSS)?
 *   {@link org.openstreetmap.josm.gui.mappaint.MapPaintStyles#addStyle}
 *   short-circuits when the source's url is null and the source is a
 *   MapCSSStyleSource — returning the same instance instead of building
 *   a fresh MapCSSStyleSource from the url. Other StyleSource subclasses
 *   would be discarded.
 *
 * The empty CSS parses to zero rules, so the parent {@code apply()} does
 * nothing; we override and skip the super call.
 */
public class OhmTierStyleSource extends MapCSSStyleSource {

    public static final String DISPLAY_NAME = "OHM_Time_Filter (in-memory)";
    public static final String TITLE = "OHM_Time_Filter (live)";

    private static final float NORMAL_ALPHA = 0.45f;

    public OhmTierStyleSource() {
        super("");
        this.name = DISPLAY_NAME;
        this.title = TITLE;
    }

    @Override
    public void apply(MultiCascade mc, IPrimitive osm, double scale, boolean pretendWayIsClosed) {
        ClassificationCache cache = ClassificationCache.current();
        if (cache.isEmpty()) {
            return;
        }
        Tier tier = cache.get(osm.getUniqueId());
        if (tier != Tier.NORMAL) {
            return;
        }
        for (Map.Entry<String, Cascade> layer : mc.getLayers()) {
            Cascade c = layer.getValue();
            applyAlpha(c, StyleKeys.COLOR, NORMAL_ALPHA);
            applyAlpha(c, StyleKeys.FILL_COLOR, NORMAL_ALPHA);
            scaleOpacity(c, StyleKeys.TEXT_OPACITY, NORMAL_ALPHA);
            scaleOpacity(c, StyleKeys.TEXT_HALO_OPACITY, NORMAL_ALPHA);
            scaleOpacity(c, StyleKeys.ICON_OPACITY, NORMAL_ALPHA);
            scaleOpacity(c, StyleKeys.REPEAT_IMAGE_OPACITY, NORMAL_ALPHA);
        }
    }

    private static void scaleOpacity(Cascade c, String key, float alpha) {
        Float existing = c.get(key, 1.0f, Float.class);
        c.put(key, existing * alpha);
    }

    private static void applyAlpha(Cascade c, String key, float alpha) {
        Color existing = c.get(key, null, Color.class);
        if (existing == null) {
            return;
        }
        int a = Math.round(existing.getAlpha() * alpha);
        Color faded = new Color(
                existing.getRed(),
                existing.getGreen(),
                existing.getBlue(),
                Math.max(0, Math.min(255, a)));
        c.put(key, faded);
    }
}
