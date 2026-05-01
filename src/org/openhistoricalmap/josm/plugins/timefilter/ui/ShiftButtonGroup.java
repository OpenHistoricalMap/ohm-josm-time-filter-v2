// License: GPL. For details, see LICENSE file.
package org.openhistoricalmap.josm.plugins.timefilter.ui;

import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.Image;

import javax.swing.JPanel;

import org.openstreetmap.josm.tools.ImageProvider;

/**
 * Chevron-shaped container that paints a {@code group-back.svg} or
 * {@code group-fwd.svg} arrow as its background and arranges its child
 * buttons in a single horizontal row centered within the chevron's
 * rectangular interior.
 *
 * The 212×42 dimensions match the SVG viewBox; FlowLayout {@code hgap=2,
 * vgap=8} centers five 32×26 buttons with ~22 px clearance on each end —
 * enough to keep the buttons clear of the chevron's left point
 * ({@code x=4..18}) and right-side indent ({@code x=198..208}).
 */
final class ShiftButtonGroup extends JPanel {

    private static final long serialVersionUID = 1L;
    private static final Dimension SIZE = new Dimension(212, 42);

    private final Image background;

    ShiftButtonGroup(String backgroundIconName) {
        super(new FlowLayout(FlowLayout.CENTER, 2, 8));
        setOpaque(false);
        background = new ImageProvider(backgroundIconName)
                .setSize(SIZE).get().getImage();
        setPreferredSize(SIZE);
        setMinimumSize(SIZE);
        setMaximumSize(SIZE);
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        g.drawImage(background, 0, 0, getWidth(), getHeight(), this);
    }
}
