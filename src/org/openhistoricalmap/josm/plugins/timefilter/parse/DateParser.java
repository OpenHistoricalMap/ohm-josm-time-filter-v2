// License: GPL. For details, see LICENSE file.
package org.openhistoricalmap.josm.plugins.timefilter.parse;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.openhistoricalmap.josm.plugins.timefilter.model.OhmDate;

/**
 * Parses OHM-style date strings into {@link OhmDate}.
 *
 * Accepted forms:
 *   YYYY                e.g. 1850, 0044
 *   YYYY-MM             e.g. 1850-06
 *   YYYY-MM-DD          e.g. 1850-06-15
 *   -YYYY[-MM[-DD]]     BCE / negative years (proleptic Gregorian)
 *   +YYYY[-MM[-DD]]     explicit positive sign
 *
 * Inputs are trimmed; surrounding whitespace is ignored. Anything that
 * doesn't match returns null (caller treats null as unparseable, falling
 * to FAINT).
 *
 * Year width: 1–6 digits accepted to match {@link java.time.LocalDate}'s
 * proleptic Gregorian range without forcing zero-padding on inputs.
 *
 * Approximate markers (~, c., ?) and slash-range forms (YYYY/YYYY) are
 * intentionally NOT parsed in v2 — see plan.
 */
public final class DateParser {

    private static final Pattern PATTERN = Pattern.compile(
            "^\\s*([+-]?)(\\d{1,6})(?:-(\\d{1,2}))?(?:-(\\d{1,2}))?\\s*$");

    private DateParser() {}

    /**
     * @return parsed OhmDate, or null if the input is null/blank/malformed
     *         or the components don't form a valid calendar date.
     */
    public static OhmDate parse(String raw) {
        if (raw == null) return null;
        Matcher m = PATTERN.matcher(raw);
        if (!m.matches()) return null;

        String sign = m.group(1);
        int year;
        try {
            year = Integer.parseInt(m.group(2));
        } catch (NumberFormatException e) {
            return null;
        }
        if ("-".equals(sign)) {
            year = -year;
        }

        String monthGroup = m.group(3);
        String dayGroup = m.group(4);

        if (monthGroup == null) {
            return OhmDate.ofYear(year);
        }
        int month;
        try {
            month = Integer.parseInt(monthGroup);
        } catch (NumberFormatException e) {
            return null;
        }
        if (month < 1 || month > 12) return null;

        if (dayGroup == null) {
            return OhmDate.ofYearMonth(year, month);
        }
        int day;
        try {
            day = Integer.parseInt(dayGroup);
        } catch (NumberFormatException e) {
            return null;
        }
        if (day < 1 || day > daysInMonth(year, month)) return null;

        try {
            return OhmDate.ofYearMonthDay(year, month, day);
        } catch (Exception e) {
            return null;
        }
    }

    private static int daysInMonth(int year, int month) {
        switch (month) {
            case 4: case 6: case 9: case 11: return 30;
            case 2: return isLeapYear(year) ? 29 : 28;
            default: return 31;
        }
    }

    private static boolean isLeapYear(int year) {
        return (year % 4 == 0 && year % 100 != 0) || (year % 400 == 0);
    }
}
