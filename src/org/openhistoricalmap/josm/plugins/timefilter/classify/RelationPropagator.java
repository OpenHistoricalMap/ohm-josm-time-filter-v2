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
import org.openstreetmap.josm.data.osm.OsmPrimitive;
import org.openstreetmap.josm.data.osm.Relation;
import org.openstreetmap.josm.data.osm.RelationMember;

/**
 * Propagates a relation's tier down to its member primitives.
 *
 * Rule: "relation can only lift members that have no own date tags;
 * tie-break = brightest".
 *
 *   - Tagless or non-date-tagged members (e.g. the outer way of a
 *     multipolygon) inherit their parent's tier when the parent is
 *     brighter.
 *   - Members with their own {@code start_date} or {@code end_date}
 *     are authoritative — the parent doesn't override them. This is
 *     critical for chronology relations whose children carry their
 *     own time-slice dates: without the rule, the chronology's wide
 *     date range would brighten every child boundary regardless of
 *     whether that boundary applies at the user's set point.
 *
 * Multipolygons, routes, boundaries, chronologies, and any other
 * relation type all use the same rule. Recursion across nested
 * relations isn't done explicitly — running the propagator once
 * across every relation handles a single layer of nesting; deeply
 * nested chronologies-of-chronologies would need multiple passes.
 */
public final class RelationPropagator {

    private RelationPropagator() {}

    /**
     * Mutates {@code byId} in place by propagating each relation's tier
     * to its members. JOSM-types overload — converts to the pure-data
     * core ({@link #propagateCore}) so tests can exercise the algorithm
     * without bringing up JOSM's static initializers.
     *
     * @param hasOwnDateTags ids of primitives that carry their own
     *        {@code start_date} or {@code end_date}. These are skipped
     *        during propagation regardless of parent tier.
     */
    public static void propagate(Iterable<Relation> relations, Map<Long, Tier> byId,
                                  Set<Long> hasOwnDateTags) {
        List<long[]> memberIdsPerRelation = new ArrayList<>();
        long[] relationIds = relationIdsAndChildLists(relations, memberIdsPerRelation);
        propagateCore(relationIds, memberIdsPerRelation, byId, hasOwnDateTags);
    }

    private static long[] relationIdsAndChildLists(Iterable<Relation> relations, List<long[]> out) {
        List<Long> ids = new ArrayList<>();
        for (Relation r : relations) {
            ids.add(r.getUniqueId());
            long[] memberIds = new long[r.getMembersCount()];
            int i = 0;
            for (RelationMember member : r.getMembers()) {
                OsmPrimitive child = member.getMember();
                memberIds[i++] = child == null ? Long.MIN_VALUE : child.getUniqueId();
            }
            out.add(memberIds);
        }
        long[] arr = new long[ids.size()];
        for (int i = 0; i < arr.length; i++) arr[i] = ids.get(i);
        return arr;
    }

    /**
     * Pure-data propagation core. Takes parallel arrays of relation ids
     * and per-relation member-id lists. Used directly by tests; the
     * JOSM-types overload above adapts to it.
     */
    public static void propagateCore(long[] relationIds, List<long[]> memberIdsPerRelation,
                                      Map<Long, Tier> byId, Set<Long> hasOwnDateTags) {
        Set<Long> locked = hasOwnDateTags == null ? Collections.emptySet() : hasOwnDateTags;
        for (int r = 0; r < relationIds.length; r++) {
            Tier rTier = byId.get(relationIds[r]);
            if (rTier == null || rTier == Tier.FAINT) continue;
            for (long memberId : memberIdsPerRelation.get(r)) {
                if (memberId == Long.MIN_VALUE) continue;
                if (locked.contains(memberId)) continue;
                Tier childTier = byId.getOrDefault(memberId, Tier.FAINT);
                Tier promoted = Tier.brightest(childTier, rTier);
                if (promoted != childTier) {
                    byId.put(memberId, promoted);
                }
            }
        }
    }

    /** Convenience for tests: copy then propagate the pure-data form. */
    public static Map<Long, Tier> propagated(long[] relationIds, List<long[]> memberIds,
                                              Map<Long, Tier> source) {
        return propagated(relationIds, memberIds, source, new HashSet<>());
    }

    public static Map<Long, Tier> propagated(long[] relationIds, List<long[]> memberIds,
                                              Map<Long, Tier> source, Set<Long> hasOwnDateTags) {
        Map<Long, Tier> copy = new HashMap<>(source);
        propagateCore(relationIds, memberIds, copy, hasOwnDateTags);
        return copy;
    }
}
