// License: GPL. For details, see LICENSE file.
package org.openhistoricalmap.josm.plugins.timefilter.ui;

import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;

import javax.swing.JTextField;

import org.openstreetmap.josm.gui.widgets.DisableShortcutsOnFocusGainedTextField;

/**
 * Disable JOSM's plain-key global shortcuts (zoom levels, etc.) while a
 * given text-input component holds focus, restoring them on focus loss.
 *
 * Why a helper? JOSM ships
 * {@link DisableShortcutsOnFocusGainedTextField}, which extends
 * {@code JosmTextField} and implements the focus-disable behaviour.
 * That class is the obvious choice for plain text fields. But spinners
 * use a {@link javax.swing.JFormattedTextField} that JSpinner constructs
 * for itself, and replacing it cleanly would mean subclassing
 * {@link javax.swing.JSpinner.NumberEditor}.
 *
 * Instead, this helper installs a {@link FocusListener} on any text
 * input and forwards the focus events through a hidden, fully-featured
 * {@link DisableShortcutsOnFocusGainedTextField} instance. The hidden
 * instance is a {@link java.awt.Component}, which is what the disable
 * machinery's {@code hasToBeDisabled} probe requires.
 */
final class ShortcutSafeFocus {

    private ShortcutSafeFocus() {}

    /**
     * Install the disable-on-focus-gain behaviour on {@code field}.
     * Focus events on {@code field} are trampolined through a hidden
     * {@link DisableShortcutsOnFocusGainedTextField}.
     */
    static void installOn(JTextField field) {
        final DisableShortcutsOnFocusGainedTextField bridge =
                new DisableShortcutsOnFocusGainedTextField();
        field.addFocusListener(new FocusListener() {
            @Override public void focusGained(FocusEvent e) { bridge.focusGained(e); }
            @Override public void focusLost(FocusEvent e)   { bridge.focusLost(e); }
        });
    }
}
