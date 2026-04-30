// License: GPL. For details, see LICENSE file.
package org.openhistoricalmap.josm.plugins.timefilter.parse;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import java.time.LocalDate;
import java.time.Month;

import org.junit.Test;
import org.openhistoricalmap.josm.plugins.timefilter.model.OhmDate;

public class DateParserTest {

    @Test public void rejectsNullAndBlank() {
        assertNull(DateParser.parse(null));
        assertNull(DateParser.parse(""));
        assertNull(DateParser.parse("   "));
    }

    @Test public void rejectsGarbage() {
        assertNull(DateParser.parse("yesterday"));
        assertNull(DateParser.parse("c. 1850"));
        assertNull(DateParser.parse("~1850"));
        assertNull(DateParser.parse("1850/1900"));
        assertNull(DateParser.parse("1850-13"));
        assertNull(DateParser.parse("1850-02-30"));
        assertNull(DateParser.parse("1850-12-32"));
    }

    @Test public void parsesYearOnly() {
        OhmDate d = DateParser.parse("1850");
        assertNotNull(d);
        assertEquals(OhmDate.Precision.YEAR, d.getPrecision());
        assertEquals(LocalDate.of(1850, 1, 1).toEpochDay(), d.earliestEpochDay());
        assertEquals(LocalDate.of(1850, 12, 31).toEpochDay(), d.latestEpochDay());
    }

    @Test public void parsesYearMonth() {
        OhmDate d = DateParser.parse("1850-06");
        assertNotNull(d);
        assertEquals(OhmDate.Precision.YEAR_MONTH, d.getPrecision());
        assertEquals(LocalDate.of(1850, 6, 1).toEpochDay(), d.earliestEpochDay());
        assertEquals(LocalDate.of(1850, 6, 30).toEpochDay(), d.latestEpochDay());
    }

    @Test public void parsesYearMonthDay() {
        OhmDate d = DateParser.parse("1850-06-15");
        assertNotNull(d);
        assertEquals(OhmDate.Precision.YEAR_MONTH_DAY, d.getPrecision());
        long expected = LocalDate.of(1850, 6, 15).toEpochDay();
        assertEquals(expected, d.earliestEpochDay());
        assertEquals(expected, d.latestEpochDay());
    }

    @Test public void parsesNegativeYear() {
        OhmDate d = DateParser.parse("-44");
        assertNotNull(d);
        assertEquals(LocalDate.of(-44, 1, 1).toEpochDay(), d.earliestEpochDay());
        assertEquals(LocalDate.of(-44, 12, 31).toEpochDay(), d.latestEpochDay());
    }

    @Test public void parsesBceFullDate() {
        // Ides of March, 44 BCE (proleptic Gregorian).
        OhmDate d = DateParser.parse("-0044-03-15");
        assertNotNull(d);
        long expected = LocalDate.of(-44, Month.MARCH, 15).toEpochDay();
        assertEquals(expected, d.earliestEpochDay());
    }

    @Test public void parsesYearZero() {
        OhmDate d = DateParser.parse("0000");
        assertNotNull(d);
        assertEquals(LocalDate.of(0, 1, 1).toEpochDay(), d.earliestEpochDay());
    }

    @Test public void parsesPlusSignPositiveYear() {
        OhmDate d = DateParser.parse("+1850");
        assertNotNull(d);
        assertEquals(LocalDate.of(1850, 1, 1).toEpochDay(), d.earliestEpochDay());
    }

    @Test public void parsesSingleDigitYear() {
        OhmDate d = DateParser.parse("9");
        assertNotNull(d);
        assertEquals(LocalDate.of(9, 1, 1).toEpochDay(), d.earliestEpochDay());
    }

    @Test public void leapYearFebruary29() {
        assertNotNull(DateParser.parse("2000-02-29"));
        assertNull(DateParser.parse("1900-02-29"));
        assertNotNull(DateParser.parse("2024-02-29"));
        assertNull(DateParser.parse("2023-02-29"));
    }

    @Test public void trimsWhitespace() {
        OhmDate d = DateParser.parse("  1850-06-15 ");
        assertNotNull(d);
        assertEquals(LocalDate.of(1850, 6, 15).toEpochDay(), d.earliestEpochDay());
    }
}
