// License: GPL. For details, see LICENSE file.
package org.openhistoricalmap.josm.plugins.timefilter.classify;

import static org.junit.Assert.assertEquals;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Test;
import org.openhistoricalmap.josm.plugins.timefilter.model.Tier;

public class WayNodePropagatorTest {

    private static final long W1 = 100, W2 = 200;
    private static final long N1 = 1, N2 = 2, N3 = 3;

    @Test public void brightWayLiftsTaglessNodes() {
        long[] ways = {W1};
        List<long[]> nodes = Arrays.asList(new long[] {N1, N2, N3});
        Map<Long, Tier> byId = new HashMap<>();
        byId.put(W1, Tier.BRIGHT);
        byId.put(N1, Tier.FAINT);
        byId.put(N2, Tier.FAINT);
        byId.put(N3, Tier.FAINT);
        WayNodePropagator.propagateCore(ways, nodes, byId, java.util.Collections.emptySet());
        assertEquals(Tier.BRIGHT, byId.get(N1));
        assertEquals(Tier.BRIGHT, byId.get(N2));
        assertEquals(Tier.BRIGHT, byId.get(N3));
    }

    @Test public void faintWayLeavesNodesAlone() {
        long[] ways = {W1};
        List<long[]> nodes = Arrays.asList(new long[] {N1});
        Map<Long, Tier> byId = new HashMap<>();
        byId.put(W1, Tier.FAINT);
        byId.put(N1, Tier.FAINT);
        WayNodePropagator.propagateCore(ways, nodes, byId, java.util.Collections.emptySet());
        assertEquals(Tier.FAINT, byId.get(N1));
    }

    @Test public void taggedNodeKeepsItsOwnBrightTier() {
        // A node already classified BRIGHT (e.g. tagged POI) shouldn't be
        // dimmed by a NORMAL parent way — brightest wins.
        long[] ways = {W1};
        List<long[]> nodes = Arrays.asList(new long[] {N1});
        Map<Long, Tier> byId = new HashMap<>();
        byId.put(W1, Tier.NORMAL);
        byId.put(N1, Tier.BRIGHT);
        WayNodePropagator.propagateCore(ways, nodes, byId, java.util.Collections.emptySet());
        assertEquals(Tier.BRIGHT, byId.get(N1));
    }

    @Test public void sharedNodeTakesBrightestParent() {
        // N1 is in both W1 (FAINT) and W2 (BRIGHT). End up BRIGHT.
        long[] ways = {W1, W2};
        List<long[]> nodes = Arrays.asList(new long[] {N1}, new long[] {N1});
        Map<Long, Tier> byId = new HashMap<>();
        byId.put(W1, Tier.FAINT);
        byId.put(W2, Tier.BRIGHT);
        byId.put(N1, Tier.FAINT);
        WayNodePropagator.propagateCore(ways, nodes, byId, java.util.Collections.emptySet());
        assertEquals(Tier.BRIGHT, byId.get(N1));
    }

    @Test public void normalWayPromotesFaintNodes() {
        long[] ways = {W1};
        List<long[]> nodes = Arrays.asList(new long[] {N1});
        Map<Long, Tier> byId = new HashMap<>();
        byId.put(W1, Tier.NORMAL);
        byId.put(N1, Tier.FAINT);
        WayNodePropagator.propagateCore(ways, nodes, byId, java.util.Collections.emptySet());
        assertEquals(Tier.NORMAL, byId.get(N1));
    }

    @Test public void dateTaggedNodeIsNotLiftedByParentWay() {
        long[] ways = {W1};
        List<long[]> nodes = Arrays.asList(new long[] {N1});
        Map<Long, Tier> byId = new HashMap<>();
        byId.put(W1, Tier.BRIGHT);
        byId.put(N1, Tier.FAINT);  // tagged node with end_date outside window
        java.util.Set<Long> dateTagged = new java.util.HashSet<>();
        dateTagged.add(N1);
        WayNodePropagator.propagateCore(ways, nodes, byId, dateTagged);
        assertEquals(Tier.FAINT, byId.get(N1));
    }

    @Test public void missingNodeIdSentinelIsIgnored() {
        long[] ways = {W1};
        List<long[]> nodes = Arrays.asList(new long[] {Long.MIN_VALUE, N1});
        Map<Long, Tier> byId = new HashMap<>();
        byId.put(W1, Tier.BRIGHT);
        byId.put(N1, Tier.FAINT);
        WayNodePropagator.propagateCore(ways, nodes, byId, java.util.Collections.emptySet());
        assertEquals(Tier.BRIGHT, byId.get(N1));
    }
}
