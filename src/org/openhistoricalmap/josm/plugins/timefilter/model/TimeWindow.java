// License: GPL. For details, see LICENSE file.
package org.openhistoricalmap.josm.plugins.timefilter.model;

/**
 * The temporal slice driven by user input: a focal date {@code setPoint}
 * and a symmetric ± offset in days. Stores the resolved epoch-day values
 * directly so classification is integer comparison only.
 */
public final class TimeWindow {

    private final OhmDate setPoint;
    private final int offsetDays;
    private final long setPointDay;
    private final long minDay;
    private final long maxDay;

    public TimeWindow(OhmDate setPoint, int offsetDays) {
        if (setPoint == null || setPoint.isInfinity()) {
            throw new IllegalArgumentException("setPoint must be a concrete date");
        }
        if (offsetDays < 0) {
            throw new IllegalArgumentException("offsetDays must be >= 0");
        }
        this.setPoint = setPoint;
        this.offsetDays = offsetDays;
        this.setPointDay = setPoint.pointEpochDay();
        this.minDay = setPointDay - offsetDays;
        this.maxDay = setPointDay + offsetDays;
    }

    public OhmDate getSetPoint() { return setPoint; }
    public int getOffsetDays() { return offsetDays; }
    public long getSetPointDay() { return setPointDay; }
    public long getMinDay() { return minDay; }
    public long getMaxDay() { return maxDay; }
}
