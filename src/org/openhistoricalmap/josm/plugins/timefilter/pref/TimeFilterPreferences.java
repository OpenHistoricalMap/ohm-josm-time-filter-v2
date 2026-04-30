// License: GPL. For details, see LICENSE file.
package org.openhistoricalmap.josm.plugins.timefilter.pref;

import org.openstreetmap.josm.spi.preferences.Config;

/**
 * Persistence for the dialog's set_point text and offset days.
 * Stored in JOSM's standard {@code Config.getPref()} so they persist
 * across restarts. Pref keys are namespaced under {@code OHM_Time_Filter.}.
 */
public final class TimeFilterPreferences {

    private static final String KEY_SET_POINT = "OHM_Time_Filter.set_point";
    private static final String KEY_OFFSET_DAYS = "OHM_Time_Filter.offset_days";

    private TimeFilterPreferences() {}

    public static String loadSetPoint(String fallback) {
        return Config.getPref().get(KEY_SET_POINT, fallback);
    }

    public static int loadOffsetDays(int fallback) {
        return Config.getPref().getInt(KEY_OFFSET_DAYS, fallback);
    }

    public static void save(String setPoint, int offsetDays) {
        if (setPoint != null) {
            Config.getPref().put(KEY_SET_POINT, setPoint);
        }
        Config.getPref().putInt(KEY_OFFSET_DAYS, offsetDays);
    }
}
