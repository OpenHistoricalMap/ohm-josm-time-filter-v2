// License: GPL. For details, see LICENSE file.
package org.openhistoricalmap.josm.plugins.timefilter.classify;

import org.openhistoricalmap.josm.plugins.timefilter.model.DateRange;
import org.openhistoricalmap.josm.plugins.timefilter.model.Tier;
import org.openhistoricalmap.josm.plugins.timefilter.model.TimeWindow;

/**
 * Maps a (DateRange, TimeWindow) pair to a {@link Tier}.
 *
 * Rules:
 *   BRIGHT  : start_day  <=  setPointDay   AND   end_day   >=  setPointDay
 *             (object existed at the focal date — boundaries inclusive,
 *              so an object whose end_date equals the set point is still
 *              BRIGHT on its final day)
 *   NORMAL  : start_day  <=  windowMaxDay  AND   end_day   >=  windowMinDay
 *             (object's lifespan overlaps the time window — inclusive
 *              endpoints; AND, not OR)
 *   FAINT   : everything else, including unparseable
 */
public final class Classifier {

    private Classifier() {}

    public static Tier classify(DateRange range, TimeWindow window) {
        if (range == null || range.isUnparseable()) return Tier.FAINT;

        long startDay = range.startDay();
        long endDay = range.endDay();
        long setPoint = window.getSetPointDay();

        if (startDay <= setPoint && endDay >= setPoint) {
            return Tier.BRIGHT;
        }
        if (startDay <= window.getMaxDay() && endDay >= window.getMinDay()) {
            return Tier.NORMAL;
        }
        return Tier.FAINT;
    }
}
