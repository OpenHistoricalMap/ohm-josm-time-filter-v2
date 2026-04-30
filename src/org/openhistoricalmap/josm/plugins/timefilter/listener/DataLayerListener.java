// License: GPL. For details, see LICENSE file.
package org.openhistoricalmap.josm.plugins.timefilter.listener;

import java.util.function.Consumer;
import javax.swing.Timer;

import org.openstreetmap.josm.data.osm.DataSet;
import org.openstreetmap.josm.data.osm.event.AbstractDatasetChangedEvent;
import org.openstreetmap.josm.data.osm.event.DataChangedEvent;
import org.openstreetmap.josm.data.osm.event.DataSetListener;
import org.openstreetmap.josm.data.osm.event.NodeMovedEvent;
import org.openstreetmap.josm.data.osm.event.PrimitivesAddedEvent;
import org.openstreetmap.josm.data.osm.event.PrimitivesRemovedEvent;
import org.openstreetmap.josm.data.osm.event.RelationMembersChangedEvent;
import org.openstreetmap.josm.data.osm.event.TagsChangedEvent;
import org.openstreetmap.josm.data.osm.event.WayNodesChangedEvent;
import org.openstreetmap.josm.gui.MainApplication;
import org.openstreetmap.josm.gui.layer.LayerManager;
import org.openstreetmap.josm.gui.layer.MainLayerManager.ActiveLayerChangeEvent;
import org.openstreetmap.josm.gui.layer.MainLayerManager.ActiveLayerChangeListener;
import org.openstreetmap.josm.gui.layer.OsmDataLayer;

/**
 * Tracks the active OSM data layer + its dataset events, and fires a
 * debounced "something changed, re-classify" callback to the controller.
 *
 * Why debounce: dataset edits often arrive in bursts (e.g., during a
 * paste or a multi-tag change). Re-classifying every primitive on every
 * event would stutter on big layers, so we coalesce into one re-run
 * 400 ms after the last event.
 */
public final class DataLayerListener implements ActiveLayerChangeListener,
                                                 LayerManager.LayerChangeListener,
                                                 DataSetListener {

    private static final int DEBOUNCE_MS = 400;

    private final Consumer<DataSet> onActiveChange;
    private final Runnable onDataChange;
    private final Timer debouncer;
    private DataSet attached;

    public DataLayerListener(Consumer<DataSet> onActiveChange, Runnable onDataChange) {
        this.onActiveChange = onActiveChange;
        this.onDataChange = onDataChange;
        this.debouncer = new Timer(DEBOUNCE_MS, e -> onDataChange.run());
        this.debouncer.setRepeats(false);
    }

    public void install() {
        MainApplication.getLayerManager().addActiveLayerChangeListener(this);
        MainApplication.getLayerManager().addLayerChangeListener(this);
        attachToActive();
    }

    public void uninstall() {
        debouncer.stop();
        detach();
        MainApplication.getLayerManager().removeActiveLayerChangeListener(this);
        MainApplication.getLayerManager().removeLayerChangeListener(this);
    }

    private void attachToActive() {
        OsmDataLayer layer = MainApplication.getLayerManager().getEditLayer();
        DataSet next = layer == null ? null : layer.data;
        if (next == attached) return;
        detach();
        attached = next;
        if (attached != null) {
            attached.addDataSetListener(this);
        }
        onActiveChange.accept(attached);
    }

    private void detach() {
        if (attached != null) {
            attached.removeDataSetListener(this);
            attached = null;
        }
    }

    private void scheduleRefresh() {
        debouncer.restart();
    }

    @Override public void activeOrEditLayerChanged(ActiveLayerChangeEvent e) { attachToActive(); }
    @Override public void layerAdded(LayerManager.LayerAddEvent e) {}
    @Override public void layerRemoving(LayerManager.LayerRemoveEvent e) {}
    @Override public void layerOrderChanged(LayerManager.LayerOrderChangeEvent e) {}

    @Override public void primitivesAdded(PrimitivesAddedEvent event) { scheduleRefresh(); }
    @Override public void primitivesRemoved(PrimitivesRemovedEvent event) { scheduleRefresh(); }
    @Override public void tagsChanged(TagsChangedEvent event) { scheduleRefresh(); }
    @Override public void nodeMoved(NodeMovedEvent event) { /* coords don't affect tiers */ }
    @Override public void wayNodesChanged(WayNodesChangedEvent event) { /* topology change, no date impact */ }
    @Override public void relationMembersChanged(RelationMembersChangedEvent event) { scheduleRefresh(); }
    @Override public void otherDatasetChange(AbstractDatasetChangedEvent event) {}
    @Override public void dataChanged(DataChangedEvent event) { scheduleRefresh(); }
}
