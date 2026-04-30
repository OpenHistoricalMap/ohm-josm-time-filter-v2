// License: GPL. For details, see LICENSE file.
package org.openhistoricalmap.josm.plugins.timefilter.model;

import java.time.LocalDate;
import java.time.Month;
import java.util.Objects;

/**
 * A date in OHM's tag conventions: signed proleptic-Gregorian year plus
 * optional month and day. Wraps {@link LocalDate} but preserves which
 * components were originally specified, so a partial date like
 * {@code start_date=1850} can be resolved to its earliest possible day
 * (1850-01-01) when used as a start, or its latest possible day
 * (1850-12-31) when used as an end.
 *
 * Comparisons are performed via the resolved epoch-day (a signed long),
 * so BCE dates and far-future dates are naturally ordered.
 */
public final class OhmDate {

    /** Sentinel for the past (open-start interval). */
    public static final OhmDate NEGATIVE_INFINITY = new OhmDate(Long.MIN_VALUE);

    /** Sentinel for the future (open-end interval). */
    public static final OhmDate POSITIVE_INFINITY = new OhmDate(Long.MAX_VALUE);

    /** Granularity of a parsed date: which components were explicit. */
    public enum Precision { YEAR, YEAR_MONTH, YEAR_MONTH_DAY }

    private final int year;
    private final int month;
    private final int day;
    private final Precision precision;
    private final long sentinelEpochDay;

    private OhmDate(int year, int month, int day, Precision precision) {
        this.year = year;
        this.month = month;
        this.day = day;
        this.precision = precision;
        this.sentinelEpochDay = 0;
    }

    private OhmDate(long sentinel) {
        this.year = 0;
        this.month = 0;
        this.day = 0;
        this.precision = null;
        this.sentinelEpochDay = sentinel;
    }

    public static OhmDate ofYear(int year) {
        return new OhmDate(year, 1, 1, Precision.YEAR);
    }

    public static OhmDate ofYearMonth(int year, int month) {
        return new OhmDate(year, month, 1, Precision.YEAR_MONTH);
    }

    public static OhmDate ofYearMonthDay(int year, int month, int day) {
        return new OhmDate(year, month, day, Precision.YEAR_MONTH_DAY);
    }

    public boolean isInfinity() {
        return precision == null;
    }

    public Precision getPrecision() {
        return precision;
    }

    /**
     * Resolved day for use as the *start* of an interval — earliest possible
     * day given the precision (Jan 1 for year-only, 1st for year-month).
     */
    public long earliestEpochDay() {
        if (isInfinity()) return sentinelEpochDay;
        switch (precision) {
            case YEAR:
                return LocalDate.of(year, 1, 1).toEpochDay();
            case YEAR_MONTH:
                return LocalDate.of(year, month, 1).toEpochDay();
            case YEAR_MONTH_DAY:
            default:
                return LocalDate.of(year, month, day).toEpochDay();
        }
    }

    /**
     * Resolved day for use as the *end* of an interval — latest possible day
     * given the precision (Dec 31 for year-only, last day of month otherwise).
     */
    public long latestEpochDay() {
        if (isInfinity()) return sentinelEpochDay;
        switch (precision) {
            case YEAR:
                return LocalDate.of(year, Month.DECEMBER, 31).toEpochDay();
            case YEAR_MONTH:
                return LocalDate.of(year, month, 1).plusMonths(1).minusDays(1).toEpochDay();
            case YEAR_MONTH_DAY:
            default:
                return LocalDate.of(year, month, day).toEpochDay();
        }
    }

    /**
     * Resolved day for use as a *point* (the user's set_point). Uses the
     * earliest day of the precision; rationale documented in README.
     */
    public long pointEpochDay() {
        return earliestEpochDay();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof OhmDate)) return false;
        OhmDate other = (OhmDate) o;
        if (this.precision == null || other.precision == null) {
            return this.sentinelEpochDay == other.sentinelEpochDay
                    && this.precision == other.precision;
        }
        return year == other.year && month == other.month && day == other.day
                && precision == other.precision;
    }

    @Override
    public int hashCode() {
        if (precision == null) return Long.hashCode(sentinelEpochDay);
        return Objects.hash(year, month, day, precision);
    }

    @Override
    public String toString() {
        if (precision == null) {
            if (sentinelEpochDay == Long.MIN_VALUE) return "-INF";
            if (sentinelEpochDay == Long.MAX_VALUE) return "+INF";
            return "OhmDate(sentinel=" + sentinelEpochDay + ")";
        }
        switch (precision) {
            case YEAR: return String.format("%04d", year);
            case YEAR_MONTH: return String.format("%04d-%02d", year, month);
            case YEAR_MONTH_DAY:
            default: return String.format("%04d-%02d-%02d", year, month, day);
        }
    }
}
