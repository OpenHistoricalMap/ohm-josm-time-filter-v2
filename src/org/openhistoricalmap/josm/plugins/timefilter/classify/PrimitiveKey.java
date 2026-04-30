// License: GPL. For details, see LICENSE file.
package org.openhistoricalmap.josm.plugins.timefilter.classify;

import org.openstreetmap.josm.data.osm.INode;
import org.openstreetmap.josm.data.osm.IPrimitive;
import org.openstreetmap.josm.data.osm.IWay;

/**
 * Encodes a primitive's (type, unique-id) pair into a single {@code long}
 * suitable for use as a {@code Map<Long, Tier>} key.
 *
 * Why: {@link IPrimitive#getUniqueId()} returns the bare OSM id, which
 * is shared across types — node 100, way 100, and relation 100 all
 * coexist on the OSM server. A raw {@code Map<Long, Tier>} keyed on
 * {@code getUniqueId()} would silently overwrite when iterating
 * mixed-type primitives that happen to share an id.
 *
 * Encoding: {@code id * 4 + typeOrdinal}, where typeOrdinal is
 * 0 = node, 1 = way, 2 = relation. Multiplying by 4 (instead of 3)
 * keeps arithmetic to a power of two and leaves headroom for an
 * eventual fourth primitive type. Negative ids (new primitives, not
 * yet uploaded) are uniquely handled because they're already unique
 * within a type and the multiplication preserves uniqueness across
 * types.
 */
public final class PrimitiveKey {

    private PrimitiveKey() {}

    public static long of(IPrimitive p) {
        long type = (p instanceof INode) ? 0L
                  : (p instanceof IWay)  ? 1L
                  : 2L;
        return p.getUniqueId() * 4L + type;
    }
}
