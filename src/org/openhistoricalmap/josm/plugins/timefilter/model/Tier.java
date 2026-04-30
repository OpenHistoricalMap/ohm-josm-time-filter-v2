// License: GPL. For details, see LICENSE file.
package org.openhistoricalmap.josm.plugins.timefilter.model;

/**
 * Visual emphasis tier assigned to each primitive when the filter is active.
 *
 * Ordered from least to most emphatic so that tier propagation across
 * relation members can use {@code Tier.values()[Math.max(...)]}
 * ("brightest wins") without an extra comparator.
 */
public enum Tier {
    FAINT,
    NORMAL,
    BRIGHT;

    /** Returns the brighter of two tiers. */
    public static Tier brightest(Tier a, Tier b) {
        return a.ordinal() >= b.ordinal() ? a : b;
    }
}
