// License: GPL. For details, see LICENSE file.
package org.openhistoricalmap.josm.plugins.timefilter.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import java.time.LocalDate;

import org.junit.Test;

public class OhmDateTest {

    @Test public void infinitySentinelsOrderCorrectly() {
        assertTrue(OhmDate.NEGATIVE_INFINITY.earliestEpochDay() < LocalDate.of(-9999, 1, 1).toEpochDay());
        assertTrue(OhmDate.POSITIVE_INFINITY.latestEpochDay() > LocalDate.of(9999, 12, 31).toEpochDay());
    }

    @Test public void yearOnlyEarliestIsJan1AndLatestIsDec31() {
        OhmDate d = OhmDate.ofYear(1850);
        assertEquals(LocalDate.of(1850, 1, 1).toEpochDay(), d.earliestEpochDay());
        assertEquals(LocalDate.of(1850, 12, 31).toEpochDay(), d.latestEpochDay());
    }

    @Test public void yearMonthEarliestIs1stAndLatestIsLastDay() {
        OhmDate june = OhmDate.ofYearMonth(1850, 6);
        assertEquals(LocalDate.of(1850, 6, 1).toEpochDay(), june.earliestEpochDay());
        assertEquals(LocalDate.of(1850, 6, 30).toEpochDay(), june.latestEpochDay());

        OhmDate feb2024 = OhmDate.ofYearMonth(2024, 2);
        assertEquals(LocalDate.of(2024, 2, 29).toEpochDay(), feb2024.latestEpochDay());

        OhmDate feb2023 = OhmDate.ofYearMonth(2023, 2);
        assertEquals(LocalDate.of(2023, 2, 28).toEpochDay(), feb2023.latestEpochDay());
    }

    @Test public void fullDateEarliestEqualsLatest() {
        OhmDate d = OhmDate.ofYearMonthDay(1850, 6, 15);
        assertEquals(d.earliestEpochDay(), d.latestEpochDay());
    }

    @Test public void pointDayUsesEarliest() {
        OhmDate y = OhmDate.ofYear(1865);
        assertEquals(y.earliestEpochDay(), y.pointEpochDay());
    }

    @Test public void equalsAndHashCode() {
        assertEquals(OhmDate.ofYear(1850), OhmDate.ofYear(1850));
        assertEquals(OhmDate.ofYearMonthDay(1850, 6, 15), OhmDate.ofYearMonthDay(1850, 6, 15));
        assertNotEquals(OhmDate.ofYear(1850), OhmDate.ofYearMonth(1850, 1));
        assertNotEquals(OhmDate.NEGATIVE_INFINITY, OhmDate.POSITIVE_INFINITY);
    }
}
