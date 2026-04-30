// License: GPL. For details, see LICENSE file.
package org.openhistoricalmap.josm.plugins.timefilter.classify;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.openhistoricalmap.josm.plugins.timefilter.model.Tier;

/**
 * Snapshot of per-primitive tier classifications, keyed by primitive
 * unique-id (long). The instance held by {@link #current()} is published
 * via {@code volatile} so the AWT paint thread always sees a consistent
 * view: workers build a new {@code ClassificationCache} off-EDT, then
 * atomically swap the reference via {@link #set(ClassificationCache)}.
 *
 * The map itself is unmodifiable after construction, removing the need
 * for synchronization on read.
 */
public final class ClassificationCache {

    /** Empty cache published before the first classification run. */
    public static final ClassificationCache EMPTY = new ClassificationCache(
            Collections.emptyMap(), 0, 0, 0, 0);

    private static volatile ClassificationCache current = EMPTY;

    private final Map<Long, Tier> byId;
    private final int brightCount;
    private final int normalCount;
    private final int faintCount;
    private final int unparseableCount;

    public ClassificationCache(Map<Long, Tier> byId,
                                int brightCount, int normalCount, int faintCount,
                                int unparseableCount) {
        this.byId = Collections.unmodifiableMap(new HashMap<>(byId));
        this.brightCount = brightCount;
        this.normalCount = normalCount;
        this.faintCount = faintCount;
        this.unparseableCount = unparseableCount;
    }

    public static ClassificationCache current() { return current; }

    public static void set(ClassificationCache next) {
        current = next == null ? EMPTY : next;
    }

    public Tier get(long uniqueId) {
        return byId.get(uniqueId);
    }

    public boolean isEmpty() { return byId.isEmpty(); }

    public int getBrightCount() { return brightCount; }
    public int getNormalCount() { return normalCount; }
    public int getFaintCount() { return faintCount; }
    public int getUnparseableCount() { return unparseableCount; }
    public int totalClassified() { return byId.size(); }
}
