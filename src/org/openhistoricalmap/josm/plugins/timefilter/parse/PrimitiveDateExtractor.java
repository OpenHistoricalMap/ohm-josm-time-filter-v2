// License: GPL. For details, see LICENSE file.
package org.openhistoricalmap.josm.plugins.timefilter.parse;

import org.openhistoricalmap.josm.plugins.timefilter.model.DateRange;
import org.openhistoricalmap.josm.plugins.timefilter.model.OhmDate;
import org.openstreetmap.josm.data.osm.OsmPrimitive;

/**
 * Extracts a {@link DateRange} from an OSM primitive's {@code start_date}
 * and {@code end_date} tags.
 *
 * Semantics:
 *   - both tags missing            -> [-INF, +INF] (always-present)
 *   - one tag missing              -> open on that side
 *   - both present and parse cleanly -> [start, end]
 *   - any present tag fails to parse -> {@link DateRange#UNPARSEABLE}
 *     (treat as faint; do not silently fall back to open intervals,
 *     since that would hide tagging errors from the editor's view)
 */
public final class PrimitiveDateExtractor {

    private static final String START = "start_date";
    private static final String END = "end_date";

    private PrimitiveDateExtractor() {}

    public static DateRange extract(OsmPrimitive p) {
        String startTag = p.get(START);
        String endTag = p.get(END);

        OhmDate start = startTag == null ? OhmDate.NEGATIVE_INFINITY : DateParser.parse(startTag);
        OhmDate end = endTag == null ? OhmDate.POSITIVE_INFINITY : DateParser.parse(endTag);

        if (start == null || end == null) {
            return DateRange.UNPARSEABLE;
        }
        return DateRange.of(start, end);
    }
}
