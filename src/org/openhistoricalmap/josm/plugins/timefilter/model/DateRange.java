// License: GPL. For details, see LICENSE file.
package org.openhistoricalmap.josm.plugins.timefilter.model;

/**
 * A primitive's lifespan as parsed from {@code start_date} / {@code end_date}.
 * Missing tags are treated as open intervals (NEGATIVE_INFINITY / POSITIVE_INFINITY).
 * Unparseable tags surface as {@link #UNPARSEABLE}, which classifies as faint.
 */
public final class DateRange {

    /** A range whose dates could not be parsed. Distinct from "missing". */
    public static final DateRange UNPARSEABLE = new DateRange(null, null, true);

    private final OhmDate start;
    private final OhmDate end;
    private final boolean unparseable;

    private DateRange(OhmDate start, OhmDate end, boolean unparseable) {
        this.start = start;
        this.end = end;
        this.unparseable = unparseable;
    }

    public static DateRange of(OhmDate start, OhmDate end) {
        return new DateRange(
                start == null ? OhmDate.NEGATIVE_INFINITY : start,
                end == null ? OhmDate.POSITIVE_INFINITY : end,
                false);
    }

    public OhmDate getStart() { return start; }
    public OhmDate getEnd() { return end; }
    public boolean isUnparseable() { return unparseable; }

    /** Earliest day this primitive could have existed (or MIN_VALUE for open start). */
    public long startDay() {
        return start.earliestEpochDay();
    }

    /** Latest day this primitive could have existed (or MAX_VALUE for open end). */
    public long endDay() {
        return end.latestEpochDay();
    }
}
