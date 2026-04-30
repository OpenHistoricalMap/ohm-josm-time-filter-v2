// License: GPL. For details, see LICENSE file.
package org.openhistoricalmap.josm.plugins.timefilter.classify;

import static org.junit.Assert.assertEquals;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Test;
import org.openhistoricalmap.josm.plugins.timefilter.model.Tier;

/**
 * Tests the pure-data propagation core. The JOSM-types overload is a
 * thin adapter that calls into this same logic, exercised in
 * integration testing with a live dataset.
 */
public class RelationPropagatorTest {

    private static final long W = 100, R1 = 200, R2 = 300, R_OUTER = 400, R_INNER = 500;

    @Test public void brighterRelationPromotesMemberWay() {
        long[] rels = {R1};
        List<long[]> members = Arrays.asList(new long[] {W});
        Map<Long, Tier> byId = new HashMap<>();
        byId.put(W, Tier.FAINT);
        byId.put(R1, Tier.BRIGHT);
        RelationPropagator.propagateCore(rels, members, byId, java.util.Collections.emptySet());
        assertEquals(Tier.BRIGHT, byId.get(W));
    }

    @Test public void multipleParentsPickBrightest() {
        long[] rels = {R1, R2};
        List<long[]> members = Arrays.asList(new long[] {W}, new long[] {W});
        Map<Long, Tier> byId = new HashMap<>();
        byId.put(W, Tier.FAINT);
        byId.put(R1, Tier.NORMAL);
        byId.put(R2, Tier.BRIGHT);
        RelationPropagator.propagateCore(rels, members, byId, java.util.Collections.emptySet());
        assertEquals(Tier.BRIGHT, byId.get(W));
    }

    @Test public void faintRelationDoesNotPropagate() {
        long[] rels = {R1};
        List<long[]> members = Arrays.asList(new long[] {W});
        Map<Long, Tier> byId = new HashMap<>();
        byId.put(W, Tier.NORMAL);
        byId.put(R1, Tier.FAINT);
        RelationPropagator.propagateCore(rels, members, byId, java.util.Collections.emptySet());
        assertEquals(Tier.NORMAL, byId.get(W));
    }

    @Test public void wayOwnTierWinsWhenAlreadyBrighterThanRelation() {
        long[] rels = {R1};
        List<long[]> members = Arrays.asList(new long[] {W});
        Map<Long, Tier> byId = new HashMap<>();
        byId.put(W, Tier.BRIGHT);
        byId.put(R1, Tier.NORMAL);
        RelationPropagator.propagateCore(rels, members, byId, java.util.Collections.emptySet());
        assertEquals(Tier.BRIGHT, byId.get(W));
    }

    @Test public void nestedRelationsConvergeAfterTwoPasses() {
        // R_OUTER (BRIGHT) -> R_INNER -> W. One pass promotes R_INNER,
        // a second pass propagates the new R_INNER tier to W.
        long[] rels = {R_OUTER, R_INNER};
        List<long[]> members = Arrays.asList(new long[] {R_INNER}, new long[] {W});
        Map<Long, Tier> byId = new HashMap<>();
        byId.put(W, Tier.FAINT);
        byId.put(R_INNER, Tier.FAINT);
        byId.put(R_OUTER, Tier.BRIGHT);
        RelationPropagator.propagateCore(rels, members, byId, java.util.Collections.emptySet());
        RelationPropagator.propagateCore(rels, members, byId, java.util.Collections.emptySet());
        assertEquals(Tier.BRIGHT, byId.get(R_INNER));
        assertEquals(Tier.BRIGHT, byId.get(W));
    }

    @Test public void dateTaggedMemberIsAuthoritativeOverParent() {
        // Chronology pattern: a chronology relation (BRIGHT for the user's
        // set point because of its wide span) mustn't lift a child whose
        // own date tags say it's outside the window.
        long[] rels = {R1};
        List<long[]> members = Arrays.asList(new long[] {W});
        Map<Long, Tier> byId = new HashMap<>();
        byId.put(W, Tier.FAINT);          // child boundary outside set point
        byId.put(R1, Tier.BRIGHT);        // chronology spans set point
        java.util.Set<Long> dateTagged = new java.util.HashSet<>();
        dateTagged.add(W);                 // child has its own start_date/end_date
        RelationPropagator.propagateCore(rels, members, byId, dateTagged);
        assertEquals(Tier.FAINT, byId.get(W));
    }

    @Test public void missingMemberIdSentinelIsIgnored() {
        long[] rels = {R1};
        List<long[]> members = Arrays.asList(new long[] {Long.MIN_VALUE, W});
        Map<Long, Tier> byId = new HashMap<>();
        byId.put(W, Tier.FAINT);
        byId.put(R1, Tier.BRIGHT);
        RelationPropagator.propagateCore(rels, members, byId, java.util.Collections.emptySet());
        assertEquals(Tier.BRIGHT, byId.get(W));
    }
}
