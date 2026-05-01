// License: GPL. For details, see LICENSE file.
package org.openhistoricalmap.josm.plugins.timefilter.classify;

import static org.junit.Assert.assertEquals;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.Test;
import org.openhistoricalmap.josm.plugins.timefilter.model.DateRange;
import org.openhistoricalmap.josm.plugins.timefilter.model.OhmDate;
import org.openhistoricalmap.josm.plugins.timefilter.model.Tier;
import org.openhistoricalmap.josm.plugins.timefilter.model.TimeWindow;
import org.openhistoricalmap.josm.plugins.timefilter.parse.DateParser;

/**
 * Integration test: exercise the full classify-then-propagate pipeline
 * on a synthetic dataset, the way TimeFilterController would, but
 * without bringing up JOSM static init.
 *
 * The synthetic primitives are plain records here. We use the same
 * tagless-seed-as-FAINT rule, then run RelationPropagator and
 * WayNodePropagator in the same order as the controller.
 */
public class IntegrationPipelineTest {

    /** Minimal stand-in for an OsmPrimitive in this pure-data test. */
    private static final class Prim {
        final long key;          // pre-encoded id
        final String startDate;  // null → open
        final String endDate;    // null → open
        final boolean tagged;    // does it have any tags at all?
        Prim(long key, String s, String e, boolean tagged) {
            this.key = key; this.startDate = s; this.endDate = e; this.tagged = tagged;
        }
    }

    private static DateRange range(Prim p) {
        OhmDate s = p.startDate == null ? OhmDate.NEGATIVE_INFINITY : DateParser.parse(p.startDate);
        OhmDate e = p.endDate == null ? OhmDate.POSITIVE_INFINITY : DateParser.parse(p.endDate);
        if (s == null || e == null) return DateRange.UNPARSEABLE;
        return DateRange.of(s, e);
    }

    /** Run classify + propagators on the inputs; return the final byId map. */
    private static Map<Long, Tier> run(
            List<Prim> primitives,
            long[] relationKeys, List<long[]> relationMembers,
            long[] wayKeys, List<long[]> wayNodes,
            TimeWindow window) {
        Map<Long, Tier> byId = new HashMap<>();
        Set<Long> hasOwnDateTags = new HashSet<>();
        for (Prim p : primitives) {
            DateRange r = range(p);
            Tier t = Classifier.classify(r, window);
            // Mirror controller: tagless primitives default to FAINT seed.
            if (!p.tagged && t == Tier.BRIGHT) {
                t = Tier.FAINT;
            }
            byId.put(p.key, t);
            if (p.startDate != null || p.endDate != null) {
                hasOwnDateTags.add(p.key);
            }
        }
        RelationPropagator.propagateCore(relationKeys, relationMembers, byId, hasOwnDateTags);
        WayNodePropagator.propagateCore(wayKeys, wayNodes, byId, hasOwnDateTags);
        return byId;
    }

    @Test public void multipolygonBuildingWithDatesOnRelationLifesItsTaglessOuter() {
        // type=multipolygon relation owns the dates; its outer way is tagless.
        // Set point inside the relation's range → relation BRIGHT, outer
        // way should also become BRIGHT via propagation.
        long REL = 1, WAY = 2, NODE_A = 3, NODE_B = 4;
        Prim rel = new Prim(REL, "1900", "1950", true);
        Prim way = new Prim(WAY, null, null, /*tagless*/ false);  // way has building=yes but no dates → tagged
        Prim wayTagless = new Prim(WAY, null, null, false);  // alternative: completely tagless
        Prim na = new Prim(NODE_A, null, null, false);
        Prim nb = new Prim(NODE_B, null, null, false);
        TimeWindow w = new TimeWindow(OhmDate.ofYearMonthDay(1925, 6, 15), 0);

        // Use the truly-tagless variant so the seed-as-FAINT rule applies.
        Map<Long, Tier> result = run(
                Arrays.asList(rel, wayTagless, na, nb),
                new long[]{REL}, Arrays.asList(new long[]{WAY}),
                new long[]{WAY}, Arrays.asList(new long[]{NODE_A, NODE_B}),
                w);
        assertEquals(Tier.BRIGHT, result.get(REL));
        assertEquals(Tier.BRIGHT, result.get(WAY));   // lifted by relation
        assertEquals(Tier.BRIGHT, result.get(NODE_A)); // lifted by way
        assertEquals(Tier.BRIGHT, result.get(NODE_B));
    }

    @Test public void chronologyDoesNotLiftDateTaggedChildrenOutsideWindow() {
        // Chronology relation has wide range and is BRIGHT for any modern
        // set point. Its child boundary relation has its own narrow dates
        // that put it OUTSIDE the set point. The chronology must NOT lift
        // the child to BRIGHT.
        long CHRONO = 100, CHILD = 101;
        Prim chrono = new Prim(CHRONO, "1850", null, true);
        Prim child = new Prim(CHILD, "1925-12-08", "1926-02-03", true);
        TimeWindow w = new TimeWindow(OhmDate.ofYearMonthDay(1947, 1, 1), 0);

        Map<Long, Tier> result = run(
                Arrays.asList(chrono, child),
                new long[]{CHRONO, CHILD}, Arrays.asList(new long[]{CHILD}, new long[]{}),
                new long[]{}, Collections.emptyList(),
                w);
        assertEquals(Tier.BRIGHT, result.get(CHRONO));
        assertEquals(Tier.FAINT,  result.get(CHILD));  // own dates are authoritative
    }

    @Test public void simpleBuildingWithEndDateBeforeSetpointIsFaintAndNodesFollow() {
        // The paint-shop scenario: end_date pre-1890, set point 1890. The
        // way is FAINT; its tagless corner nodes follow it down.
        long WAY = 200, N1 = 201, N2 = 202, N3 = 203;
        Prim way = new Prim(WAY, "1886-01-06", "1889-06-06", true);
        Prim n1 = new Prim(N1, null, null, false);
        Prim n2 = new Prim(N2, null, null, false);
        Prim n3 = new Prim(N3, null, null, false);
        TimeWindow w = new TimeWindow(OhmDate.ofYearMonthDay(1890, 6, 1), 0);

        Map<Long, Tier> result = run(
                Arrays.asList(way, n1, n2, n3),
                new long[]{}, Collections.emptyList(),
                new long[]{WAY}, Arrays.asList(new long[]{N1, N2, N3}),
                w);
        assertEquals(Tier.FAINT, result.get(WAY));
        assertEquals(Tier.FAINT, result.get(N1));  // tagless seed, parent FAINT, stays
        assertEquals(Tier.FAINT, result.get(N2));
        assertEquals(Tier.FAINT, result.get(N3));
    }

    @Test public void inclusiveBoundaryAtEndDateIsBright() {
        // Regression: endDate equal to set point should be BRIGHT (inclusive).
        long WAY = 300;
        Prim way = new Prim(WAY, "1900", "1911-03-15", true);
        TimeWindow w = new TimeWindow(OhmDate.ofYearMonthDay(1911, 3, 15), 0);
        Map<Long, Tier> result = run(
                Arrays.asList(way),
                new long[]{}, Collections.emptyList(),
                new long[]{WAY}, Collections.<long[]>singletonList(new long[]{}),
                w);
        assertEquals(Tier.BRIGHT, result.get(WAY));
    }

    @Test public void taggedNodeWithOwnDatesIsNotLiftedByItsParentWay() {
        // A node tagged as a POI with its own end_date should NOT be lifted
        // by a BRIGHT parent way. Date-tagged members are authoritative.
        long WAY = 400, NODE = 401;
        Prim way = new Prim(WAY, "1900", null, true);    // BRIGHT
        Prim node = new Prim(NODE, "1900", "1910", true); // FAINT for set point 2000
        TimeWindow w = new TimeWindow(OhmDate.ofYearMonthDay(2000, 1, 1), 0);
        Map<Long, Tier> result = run(
                Arrays.asList(way, node),
                new long[]{}, Collections.emptyList(),
                new long[]{WAY}, Arrays.asList(new long[]{NODE}),
                w);
        assertEquals(Tier.BRIGHT, result.get(WAY));
        assertEquals(Tier.FAINT,  result.get(NODE));
    }

    @Test public void primitiveKeyEncodingDoesNotCollideAcrossTypes() {
        // Pure-data exercise of the encoding contract. We don't import
        // PrimitiveKey here (it touches IPrimitive), but we replicate the
        // formula and assert keys are distinct for same-id-different-type.
        long node100 = 100L * 4 + 0;
        long way100  = 100L * 4 + 1;
        long rel100  = 100L * 4 + 2;
        Set<Long> seen = new HashSet<>(Arrays.asList(node100, way100, rel100));
        assertEquals("encoded keys must be distinct across types", 3, seen.size());
        // And new-primitive negative ids stay unique within a type:
        long newNode = -1L * 4 + 0;
        long newWay  = -1L * 4 + 1;
        seen.add(newNode); seen.add(newWay);
        assertEquals(5, seen.size());
    }

    @Test public void unparseableDatesClassifyAsFaint() {
        // Mirror PrimitiveDateExtractor's behaviour: a tag that fails to
        // parse marks the primitive UNPARSEABLE → FAINT. (Constructed
        // directly here since DateParser would reject the bad string.)
        long WAY = 500;
        TimeWindow w = new TimeWindow(OhmDate.ofYearMonthDay(2000, 1, 1), 0);
        Tier t = Classifier.classify(DateRange.UNPARSEABLE, w);
        assertEquals(Tier.FAINT, t);
        // Wire it through the pipeline shape too, just for sanity.
        Map<Long, Tier> byId = new HashMap<>();
        byId.put(WAY, t);
        RelationPropagator.propagateCore(new long[]{}, Collections.<long[]>emptyList(),
                byId, Collections.<Long>emptySet());
        assertEquals(Tier.FAINT, byId.get(WAY));
    }

    // Suppress unused-variable warning for the alternative-construction
    // demonstration in the first test.
    @SuppressWarnings("unused")
    private static List<Prim> _unusedShim() {
        return new ArrayList<>();
    }
}
