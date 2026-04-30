// License: GPL. For details, see LICENSE file.
package org.openhistoricalmap.josm.plugins.timefilter.classify;

import static org.junit.Assert.assertEquals;

import org.junit.Test;
import org.openhistoricalmap.josm.plugins.timefilter.model.DateRange;
import org.openhistoricalmap.josm.plugins.timefilter.model.OhmDate;
import org.openhistoricalmap.josm.plugins.timefilter.model.Tier;
import org.openhistoricalmap.josm.plugins.timefilter.model.TimeWindow;

public class ClassifierTest {

    private static final TimeWindow WINDOW =
            new TimeWindow(OhmDate.ofYearMonthDay(1865, 4, 15), 365);

    private static DateRange range(String startTag, String endTag) {
        return DateRange.of(
                startTag == null ? OhmDate.NEGATIVE_INFINITY :
                    org.openhistoricalmap.josm.plugins.timefilter.parse.DateParser.parse(startTag),
                endTag == null ? OhmDate.POSITIVE_INFINITY :
                    org.openhistoricalmap.josm.plugins.timefilter.parse.DateParser.parse(endTag));
    }

    @Test public void brightWhenSetPointFallsInsideRange() {
        // Civil War: 1861-04-12 to 1865-04-09; set point 1865-04-15 with offset 365.
        // Set point 1865-04-15 is AFTER 1865-04-09, so range ends BEFORE set point.
        // Use a different range: feature exists 1860..1870.
        assertEquals(Tier.BRIGHT, Classifier.classify(range("1860", "1870"), WINDOW));
    }

    @Test public void normalWhenLifespanOverlapsWindowButNotSetPoint() {
        // Window: 1864-04-15 .. 1866-04-15. Object only exists 1860..1864-06.
        // 1864-06 is inside window but before set_point 1865-04-15, so end_day(1864-06) > min_day,
        // start_day(1860) < max_day, hence overlap -> NORMAL. But end_day(1864-06) < setPoint, not BRIGHT.
        assertEquals(Tier.NORMAL, Classifier.classify(range("1860", "1864-06"), WINDOW));
    }

    @Test public void faintWhenLifespanIsEntirelyOutsideWindow() {
        // Window: 1864-04-15 .. 1866-04-15. Object exists 1900..1950.
        assertEquals(Tier.FAINT, Classifier.classify(range("1900", "1950"), WINDOW));
        // And entirely before:
        assertEquals(Tier.FAINT, Classifier.classify(range("1700", "1800"), WINDOW));
    }

    @Test public void unparseableIsFaint() {
        assertEquals(Tier.FAINT, Classifier.classify(DateRange.UNPARSEABLE, WINDOW));
    }

    @Test public void missingDatesGiveBright() {
        // No dates at all = "always present" -> hits BRIGHT (start=-INF < setPoint < end=+INF).
        assertEquals(Tier.BRIGHT, Classifier.classify(range(null, null), WINDOW));
    }

    @Test public void missingStartDateAllowsBright() {
        assertEquals(Tier.BRIGHT, Classifier.classify(range(null, "1870"), WINDOW));
    }

    @Test public void offsetZeroNarrowsToSetPointOnly() {
        TimeWindow narrow = new TimeWindow(OhmDate.ofYearMonthDay(1865, 4, 15), 0);
        // Object 1860..1870 contains the set point -> BRIGHT.
        assertEquals(Tier.BRIGHT, Classifier.classify(range("1860", "1870"), narrow));
        // Object 1866..1870 starts after -> FAINT (window is a single day,
        // and 1866-01-01 > 1865-04-15 so no overlap).
        assertEquals(Tier.FAINT, Classifier.classify(range("1866", "1870"), narrow));
    }

    @Test public void endDateEqualToSetPointIsBright() {
        // Boundary inclusivity: an object whose end_date matches the
        // set point exactly should be BRIGHT (its last day is the set point).
        TimeWindow w = new TimeWindow(OhmDate.ofYearMonthDay(1911, 3, 15), 0);
        assertEquals(Tier.BRIGHT, Classifier.classify(range("1900", "1911-03-15"), w));
    }

    @Test public void startDateEqualToSetPointIsBright() {
        // Boundary inclusivity: start_date equal to set point is BRIGHT
        // (its first day is the set point).
        TimeWindow w = new TimeWindow(OhmDate.ofYearMonthDay(1911, 3, 16), 0);
        assertEquals(Tier.BRIGHT, Classifier.classify(range("1911-03-16", "1950"), w));
    }

    @Test public void andLogicNotOr() {
        // Spec: AND, not OR. An object 1900..1950 with set_point=1800,
        // offset=5 would match `end_date > min(window)` (1950 > 1795) but
        // NOT `start_date < max(window)` (1900 < 1805 is false). So FAINT.
        TimeWindow earlyWindow = new TimeWindow(OhmDate.ofYearMonthDay(1800, 1, 1), 5);
        assertEquals(Tier.FAINT, Classifier.classify(range("1900", "1950"), earlyWindow));
    }
}
