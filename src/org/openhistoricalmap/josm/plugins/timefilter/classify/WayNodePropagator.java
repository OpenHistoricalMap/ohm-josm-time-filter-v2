// License: GPL. For details, see LICENSE file.
package org.openhistoricalmap.josm.plugins.timefilter.classify;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.openhistoricalmap.josm.plugins.timefilter.model.Tier;
import org.openstreetmap.josm.data.osm.Node;
import org.openstreetmap.josm.data.osm.Way;

/**
 * Propagates a way's tier down to its constituent nodes.
 *
 * Rule (same as {@link RelationPropagator}): only lift nodes that lack
 * their own date tags. Tagless way-corners get the parent way's tier;
 * date-tagged nodes (rare, but possible — e.g. a specifically-dated
 * monument that happens to also be a way vertex) stay at their own
 * classification.
 *
 * "Brightest wins" — a node shared by multiple ways takes the brightest.
 *
 * Run AFTER {@link RelationPropagator} so the way's tier already
 * reflects any tier promotion from parent relations.
 */
public final class WayNodePropagator {

    private WayNodePropagator() {}

    /**
     * Mutates {@code byId} in place. JOSM-types overload — converts to
     * pure-data form for the test-friendly core.
     *
     * @param hasOwnDateTags ids of primitives that carry their own
     *        {@code start_date} or {@code end_date}. Skipped here.
     */
    public static void propagate(Iterable<Way> ways, Map<Long, Tier> byId,
                                  Set<Long> hasOwnDateTags) {
        List<long[]> nodeIdsPerWay = new ArrayList<>();
        long[] wayIds = wayIdsAndChildLists(ways, nodeIdsPerWay);
        propagateCore(wayIds, nodeIdsPerWay, byId, hasOwnDateTags);
    }

    private static long[] wayIdsAndChildLists(Iterable<Way> ways, List<long[]> out) {
        List<Long> ids = new ArrayList<>();
        for (Way w : ways) {
            ids.add(PrimitiveKey.of(w));
            List<Node> nodes = w.getNodes();
            long[] nodeIds = new long[nodes.size()];
            for (int i = 0; i < nodes.size(); i++) {
                Node n = nodes.get(i);
                nodeIds[i] = n == null ? Long.MIN_VALUE : PrimitiveKey.of(n);
            }
            out.add(nodeIds);
        }
        long[] arr = new long[ids.size()];
        for (int i = 0; i < arr.length; i++) arr[i] = ids.get(i);
        return arr;
    }

    /** Pure-data propagation core. Tested directly by unit tests. */
    public static void propagateCore(long[] wayIds, List<long[]> nodeIdsPerWay,
                                      Map<Long, Tier> byId, Set<Long> hasOwnDateTags) {
        Set<Long> locked = hasOwnDateTags == null ? Collections.emptySet() : hasOwnDateTags;
        for (int w = 0; w < wayIds.length; w++) {
            Tier wTier = byId.get(wayIds[w]);
            if (wTier == null || wTier == Tier.FAINT) continue;
            for (long nodeId : nodeIdsPerWay.get(w)) {
                if (nodeId == Long.MIN_VALUE) continue;
                if (locked.contains(nodeId)) continue;
                Tier childTier = byId.getOrDefault(nodeId, Tier.FAINT);
                Tier promoted = Tier.brightest(childTier, wTier);
                if (promoted != childTier) {
                    byId.put(nodeId, promoted);
                }
            }
        }
    }

    /** Convenience for tests. */
    public static Map<Long, Tier> propagated(long[] wayIds, List<long[]> nodeIds,
                                              Map<Long, Tier> source) {
        return propagated(wayIds, nodeIds, source, new HashSet<>());
    }

    public static Map<Long, Tier> propagated(long[] wayIds, List<long[]> nodeIds,
                                              Map<Long, Tier> source, Set<Long> hasOwnDateTags) {
        Map<Long, Tier> copy = new HashMap<>(source);
        propagateCore(wayIds, nodeIds, copy, hasOwnDateTags);
        return copy;
    }
}
