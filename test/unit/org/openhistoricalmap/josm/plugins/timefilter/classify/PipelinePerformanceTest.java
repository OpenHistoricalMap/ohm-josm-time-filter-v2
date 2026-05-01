// License: GPL. For details, see LICENSE file.
package org.openhistoricalmap.josm.plugins.timefilter.classify;

import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import org.junit.Test;
import org.openhistoricalmap.josm.plugins.timefilter.model.DateRange;
import org.openhistoricalmap.josm.plugins.timefilter.model.OhmDate;
import org.openhistoricalmap.josm.plugins.timefilter.model.Tier;
import org.openhistoricalmap.josm.plugins.timefilter.model.TimeWindow;
import org.openhistoricalmap.josm.plugins.timefilter.parse.DateParser;

/**
 * Synthetic benchmark for the classifier + propagator pipeline.
 *
 * Goal: get a rough floor for how long classify+propagate takes on a
 * 100k-primitive dataset, so we know whether each Apply / shift-button
 * click is going to feel snappy on real OHM data.
 *
 * This is JOSM-free: we use plain {@code long} keys, our own DateRange
 * objects, and the pure-data {@code propagateCore} entry points. The
 * actual JOSM-types overload of the propagators adds a small constant
 * factor (one cast per member) that's not measured here.
 *
 * The test asserts an upper bound that's loose by an order of magnitude
 * compared to typical wall-clock on a developer laptop, so it should
 * stay green on slow CI runners.
 */
public class PipelinePerformanceTest {

    private static final int PRIMITIVES = 100_000;
    private static final int RELATIONS = 1_000;
    private static final int MEMBERS_PER_RELATION = 10;
    private static final int WAYS_WITH_NODES = 30_000;
    private static final int NODES_PER_WAY = 5;

    @Test public void classifyAndPropagate100kPrimitives() {
        Random rng = new Random(42);

        // Build dates: a uniform spread of (start, end) pairs spanning the
        // 1700-2100 window so most primitives fall on either side of
        // typical set points. Some primitives have only one date set.
        long nextKey = 0;
        Map<Long, Tier> byId = new HashMap<>(PRIMITIVES * 2);
        Set<Long> hasOwnDateTags = new HashSet<>(PRIMITIVES * 2);
        TimeWindow window = new TimeWindow(OhmDate.ofYearMonthDay(1900, 1, 1), 0);

        long t0 = System.nanoTime();
        for (int i = 0; i < PRIMITIVES; i++) {
            int startYear = 1700 + rng.nextInt(400);
            int span = 1 + rng.nextInt(200);
            int endYear = Math.min(2100, startYear + span);
            String startStr = String.format("%04d", startYear);
            String endStr = String.format("%04d", endYear);
            DateRange r = DateRange.of(DateParser.parse(startStr), DateParser.parse(endStr));
            Tier t = Classifier.classify(r, window);
            long key = nextKey++;
            byId.put(key, t);
            hasOwnDateTags.add(key);
        }
        long classifyMs = (System.nanoTime() - t0) / 1_000_000L;

        // Build relation-member arrays: each relation has 10 members, picked
        // from already-classified primitives.
        long[] relationKeys = new long[RELATIONS];
        List<long[]> memberIdsPerRelation = new ArrayList<>(RELATIONS);
        for (int r = 0; r < RELATIONS; r++) {
            long relKey = nextKey++;
            relationKeys[r] = relKey;
            // Make some relations BRIGHT so propagation actually happens.
            byId.put(relKey, r % 3 == 0 ? Tier.BRIGHT : Tier.FAINT);
            long[] members = new long[MEMBERS_PER_RELATION];
            for (int m = 0; m < MEMBERS_PER_RELATION; m++) {
                members[m] = rng.nextInt(PRIMITIVES);  // refer to a classified prim
            }
            memberIdsPerRelation.add(members);
        }

        // Build way-node arrays similarly.
        long[] wayKeys = new long[WAYS_WITH_NODES];
        List<long[]> nodeIdsPerWay = new ArrayList<>(WAYS_WITH_NODES);
        for (int w = 0; w < WAYS_WITH_NODES; w++) {
            long wayKey = nextKey++;
            wayKeys[w] = wayKey;
            byId.put(wayKey, w % 4 == 0 ? Tier.BRIGHT : Tier.FAINT);
            long[] nodes = new long[NODES_PER_WAY];
            for (int n = 0; n < NODES_PER_WAY; n++) {
                nodes[n] = rng.nextInt(PRIMITIVES);
            }
            nodeIdsPerWay.add(nodes);
        }

        long t1 = System.nanoTime();
        RelationPropagator.propagateCore(relationKeys, memberIdsPerRelation, byId, hasOwnDateTags);
        long relMs = (System.nanoTime() - t1) / 1_000_000L;

        long t2 = System.nanoTime();
        WayNodePropagator.propagateCore(wayKeys, nodeIdsPerWay, byId, hasOwnDateTags);
        long wayMs = (System.nanoTime() - t2) / 1_000_000L;

        long total = classifyMs + relMs + wayMs;
        System.out.printf("classify %d prims: %d ms; relprop %d rels: %d ms; waynode %d ways: %d ms; total %d ms%n",
                PRIMITIVES, classifyMs, RELATIONS, relMs, WAYS_WITH_NODES, wayMs, total);

        // Loose bound — typical dev laptop runs this in <500 ms; CI has
        // more variance.
        assertTrue("pipeline should complete within 5s on a 100k synthetic dataset; took " + total + " ms",
                total < 5000);
    }

    /**
     * Shim to silence an "unused import" if the imports list grows. Not
     * called from anywhere; exists so future maintainers see why
     * Collections is imported.
     */
    @SuppressWarnings("unused")
    private static void _shim() {
        Collections.emptyList();
    }
}
