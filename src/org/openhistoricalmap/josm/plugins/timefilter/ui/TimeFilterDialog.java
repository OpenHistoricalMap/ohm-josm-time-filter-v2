// License: GPL. For details, see LICENSE file.
package org.openhistoricalmap.josm.plugins.timefilter.ui;

import static org.openstreetmap.josm.tools.I18n.tr;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

import javax.swing.BorderFactory;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JFormattedTextField;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;
import javax.swing.text.NumberFormatter;

import org.openhistoricalmap.josm.plugins.timefilter.TimeFilterController;
import org.openhistoricalmap.josm.plugins.timefilter.classify.ClassificationCache;
import org.openhistoricalmap.josm.plugins.timefilter.model.DateRange;
import org.openhistoricalmap.josm.plugins.timefilter.model.OhmDate;
import org.openhistoricalmap.josm.plugins.timefilter.model.TimeWindow;
import org.openhistoricalmap.josm.plugins.timefilter.parse.DateParser;
import org.openhistoricalmap.josm.plugins.timefilter.parse.PrimitiveDateExtractor;
import org.openhistoricalmap.josm.plugins.timefilter.pref.TimeFilterPreferences;
import org.openstreetmap.josm.data.osm.DataSet;
import org.openstreetmap.josm.data.osm.OsmPrimitive;
import org.openstreetmap.josm.gui.MainApplication;
import org.openstreetmap.josm.gui.Notification;
import org.openstreetmap.josm.gui.dialogs.ToggleDialog;
import org.openstreetmap.josm.gui.widgets.DisableShortcutsOnFocusGainedTextField;
import org.openstreetmap.josm.tools.ImageProvider;
import org.openstreetmap.josm.tools.Shortcut;

/**
 * The dockable side-panel for the time filter.
 *
 *  ┌─ OHM Time Filter ─────────────────────────────────────────┐
 *  │ Filter date [1865-04-15] ± [0] days [FS]                   │
 *  │ [<C][<X][<Y][<M][<D]   [D>][M>][Y>][X>][C>]                │
 *  │ [ Apply ]  ●  [ Clear ]                                    │
 *  │ ⚠ 12 unparseable                                           │
 *  └────────────────────────────────────────────────────────────┘
 */
public final class TimeFilterDialog extends ToggleDialog {

    private static final long serialVersionUID = 1L;

    private static final String DEFAULT_SET_POINT = "1900-01-01";
    private static final int DEFAULT_OFFSET_DAYS = 0;

    private final TimeFilterController controller;
    private final JTextField setPointField;
    private final JSpinner offsetSpinner;
    private final JButton clearBtn;
    private final JLabel filterStatusIcon;
    private final Icon filterOnIcon;
    private final Icon filterOffIcon;
    private final JLabel warningLine;
    private final Color normalBorderColor;

    public TimeFilterDialog(TimeFilterController controller) {
        super(
            tr("OHM Time Filter"),
            "timefilter",
            tr("Highlight features extant at a chosen date; fade the rest."),
            Shortcut.registerShortcut(
                "OHM_Time_Filter:dialog",
                tr("Toggle: {0}", tr("OHM Time Filter")),
                java.awt.event.KeyEvent.VK_O,
                Shortcut.NONE),
            150);
        this.controller = controller;

        JPanel form = new JPanel(new GridBagLayout());
        form.setBorder(BorderFactory.createEmptyBorder(0, 8, 2, 8));
        GridBagConstraints g = new GridBagConstraints();
        g.insets = new Insets(0, 2, 0, 2);

        // DisableShortcutsOnFocusGainedTextField unregisters JOSM's global
        // single-key/shift-key shortcuts while this field has focus, so
        // typing "1" doesn't trigger JOSM's "set zoom level 1" shortcut.
        setPointField = new DisableShortcutsOnFocusGainedTextField(
                TimeFilterPreferences.loadSetPoint(DEFAULT_SET_POINT));
        setPointField.setColumns(8);
        setPointField.setToolTipText(tr(
                "YYYY, YYYY-MM, or YYYY-MM-DD. Negative years allowed (e.g. -0044-03-15). " +
                "Partial dates resolve to their earliest day — \"1900\" means 1900-01-01."));
        normalBorderColor = setPointField.getBackground();

        SpinnerNumberModel offsetModel = new SpinnerNumberModel(
                TimeFilterPreferences.loadOffsetDays(DEFAULT_OFFSET_DAYS),
                0, Integer.MAX_VALUE, 1);
        offsetSpinner = new JSpinner(offsetModel);
        JSpinner.NumberEditor editor = new JSpinner.NumberEditor(offsetSpinner, "#");
        offsetSpinner.setEditor(editor);
        JFormattedTextField tf = ((JSpinner.DefaultEditor) offsetSpinner.getEditor()).getTextField();
        ((NumberFormatter) tf.getFormatter()).setAllowsInvalid(false);
        tf.setColumns(3);
        offsetSpinner.setToolTipText(tr("Days on each side of the filter date (default 0)."));
        ShortcutSafeFocus.installOn(tf);

        Icon fsIcon = new ImageProvider("ohmtimefilter/set-filter-by-selection")
                .setSize(new Dimension(20, 20)).get();
        JButton fsBtn = new JButton(fsIcon);
        fsBtn.setToolTipText(tr("Filter to selection: average the selected primitives' dates and apply."));
        fsBtn.setMargin(new Insets(2, 4, 2, 4));

        // Row 0: date controls centered in the panel, FS button flush
        // right. BorderLayout.CENTER stretches to fill the remaining
        // width; the inner FlowLayout.CENTER keeps the date controls
        // visually centered in that region. BorderLayout.EAST takes its
        // preferred width and stays anchored at the right edge.
        g.gridx = 0; g.gridy = 0; g.gridwidth = 2;
        g.fill = GridBagConstraints.HORIZONTAL;
        g.weightx = 1;
        g.anchor = GridBagConstraints.CENTER;
        JPanel topRow = new JPanel(new BorderLayout());
        JPanel topCenter = new JPanel(new FlowLayout(FlowLayout.CENTER, 2, 0));
        topCenter.add(new JLabel(tr("Filter date")));
        topCenter.add(setPointField);
        topCenter.add(new JLabel("±"));
        topCenter.add(offsetSpinner);
        topCenter.add(new JLabel(tr("days")));
        topRow.add(topCenter, BorderLayout.CENTER);
        topRow.add(fsBtn, BorderLayout.EAST);
        form.add(topRow, g);
        g.weightx = 0;

        // Row 1: date-shift buttons
        g.gridx = 0; g.gridy = 1; g.gridwidth = 2;
        g.fill = GridBagConstraints.HORIZONTAL; g.anchor = GridBagConstraints.CENTER;
        form.add(buildShiftButtonRow(), g);

        // Row 2: apply / status icon / clear
        g.gridx = 0; g.gridy = 2; g.gridwidth = 2; g.fill = GridBagConstraints.NONE;
        g.anchor = GridBagConstraints.CENTER;
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.CENTER, 6, 0));
        JButton applyBtn = new JButton(tr("Apply"));
        clearBtn = new JButton(tr("Clear"));
        Dimension iconSize = new Dimension(20, 20);
        filterOnIcon = new ImageProvider("ohmtimefilter/filter-on").setSize(iconSize).get();
        filterOffIcon = new ImageProvider("ohmtimefilter/filter-off").setSize(iconSize).get();
        filterStatusIcon = new JLabel(filterOffIcon);
        buttons.add(applyBtn);
        buttons.add(filterStatusIcon);
        buttons.add(clearBtn);
        form.add(buttons, g);

        // Row 3: warnings
        g.gridy = 3; g.fill = GridBagConstraints.HORIZONTAL; g.anchor = GridBagConstraints.WEST;
        warningLine = new JLabel(" ");
        warningLine.setForeground(new Color(0xB36500));
        form.add(warningLine, g);

        applyBtn.addActionListener(e -> doApply());
        clearBtn.addActionListener(e -> doClear());
        fsBtn.addActionListener(e -> doFilterToSelection());
        // Enter in the set-point field triggers Apply.
        setPointField.addActionListener(e -> doApply());

        updateFilterUI();
        // Wrap so the form sits at the top of the dialog body — any extra
        // vertical space the parent gives us goes into the (empty) CENTER
        // region below, instead of stretching the form or showing a scrollbar.
        JPanel topAligned = new JPanel(new BorderLayout());
        topAligned.add(form, BorderLayout.NORTH);
        createLayout(topAligned, false, java.util.Collections.emptyList());
    }

    /** Reflect the controller's active state in the Clear button + status icon. */
    private void updateFilterUI() {
        boolean on = controller.isActive();
        clearBtn.setEnabled(on);
        filterStatusIcon.setIcon(on ? filterOnIcon : filterOffIcon);
        filterStatusIcon.setToolTipText(on ? tr("Filter is ON") : tr("Filter is off"));
    }

    /** Pop a JOSM error notification — used for input/state errors. */
    private static void showError(String message) {
        new Notification(message)
                .setIcon(JOptionPane.ERROR_MESSAGE)
                .show();
    }

    /**
     * One step magnitude (text label, unit name for tooltips, button
     * width, absolute Δ-tuple). The direction (-1 for back, +1 for
     * forward) is supplied by {@link #makeShiftButton}.
     */
    private static final class Magnitude {
        final String label;
        final String unit;
        final int buttonWidth;
        final int absYears, absMonths, absDays;
        Magnitude(String label, String unit, int width, int y, int m, int d) {
            this.label = label; this.unit = unit; this.buttonWidth = width;
            this.absYears = y; this.absMonths = m; this.absDays = d;
        }
    }

    /**
     * Step magnitudes in descending order (largest jump first). The back
     * group displays them in this order (100Y nearest the left arrow
     * point); the forward group displays them reversed (100Y nearest the
     * right arrow point). Per-magnitude widths give the multi-character
     * labels more room and let the single-character labels feel less
     * empty.
     */
    private static final Magnitude[] MAGNITUDES = {
        new Magnitude("100Y", "100 years", 52, 100, 0, 0),
        new Magnitude("10Y",  "10 years",  42,  10, 0, 0),
        new Magnitude("Y",    "1 year",    30,   1, 0, 0),
        new Magnitude("M",    "1 month",   30,   0, 1, 0),
        new Magnitude("D",    "1 day",     30,   0, 0, 1),
    };

    private static final int SHIFT_BTN_HEIGHT = 26;

    private JPanel buildShiftButtonRow() {
        // WrapLayout — when the panel is too narrow to fit both 240-px
        // chevron groups side-by-side, the forward group wraps to a
        // second line below the back group instead of being clipped.
        // vgap=2 keeps the row tight against the date row above while
        // still leaving a small gap between the wrapped chevrons in
        // narrow mode.
        JPanel row = new JPanel(new WrapLayout(WrapLayout.CENTER, 4, 2));

        ShiftButtonGroup back = new ShiftButtonGroup("ohmtimefilter/group-back");
        for (Magnitude m : MAGNITUDES) {
            back.add(makeShiftButton(m, -1));
        }
        row.add(back);

        ShiftButtonGroup forward = new ShiftButtonGroup("ohmtimefilter/group-fwd");
        for (int i = MAGNITUDES.length - 1; i >= 0; i--) {
            forward.add(makeShiftButton(MAGNITUDES[i], +1));
        }
        row.add(forward);

        return row;
    }

    private JButton makeShiftButton(Magnitude m, int direction) {
        JButton b = new JButton(m.label);
        b.setToolTipText(direction < 0
                ? tr("Back {0}", m.unit)
                : tr("Forward {0}", m.unit));
        b.setFont(b.getFont().deriveFont(java.awt.Font.BOLD, 11f));
        b.setMargin(new Insets(0, 2, 0, 2));
        b.setFocusable(false);
        // Force a flat, compact rendering on macOS Aqua so a wider
        // button (100Y) looks identical to a narrower one (Y / M / D)
        // instead of getting bumped to the fuller "regular" style.
        // No-op on non-macOS look-and-feels.
        b.putClientProperty("JButton.buttonType", "square");
        Dimension size = new Dimension(m.buttonWidth, SHIFT_BTN_HEIGHT);
        b.setPreferredSize(size);
        b.setMinimumSize(size);
        b.setMaximumSize(size);
        int years = direction * m.absYears;
        int months = direction * m.absMonths;
        int days = direction * m.absDays;
        b.addActionListener(e -> shiftDate(years, months, days));
        return b;
    }

    /**
     * Shift the filter date by the given (years, months, days) deltas and
     * re-apply. The current text is parsed via {@link DateParser}; partial
     * dates (year-only, year-month) are resolved to their earliest day
     * before shifting, so the field always becomes a full YYYY-MM-DD.
     */
    private void shiftDate(int years, int months, int days) {
        String raw = setPointField.getText();
        OhmDate parsed = DateParser.parse(raw);
        if (parsed == null) {
            markFieldInvalid(true);
            showError(tr("Could not parse ''{0}'' as a date.", raw));
            return;
        }
        markFieldInvalid(false);
        LocalDate ld = LocalDate.ofEpochDay(parsed.earliestEpochDay());
        if (years != 0)  ld = ld.plusYears(years);
        if (months != 0) ld = ld.plusMonths(months);
        if (days != 0)   ld = ld.plusDays(days);
        setPointField.setText(formatLocalDate(ld));
        doApply();
    }

    private static String formatLocalDate(LocalDate ld) {
        int y = ld.getYear();
        if (y < 0) {
            return String.format("-%04d-%02d-%02d", -y, ld.getMonthValue(), ld.getDayOfMonth());
        }
        return String.format("%04d-%02d-%02d", y, ld.getMonthValue(), ld.getDayOfMonth());
    }

    private void doApply() {
        String raw = setPointField.getText();
        int offsetDays = currentOffsetDays();

        // Pre-flight parse to give immediate visual feedback.
        OhmDate parsed = DateParser.parse(raw);
        if (parsed == null) {
            markFieldInvalid(true);
            showError(tr("Could not parse ''{0}'' as a date.", raw));
            return;
        }
        markFieldInvalid(false);

        TimeFilterPreferences.save(raw, offsetDays);
        controller.apply(raw, offsetDays, this::refreshStatus);
        warningLine.setText(" ");
    }

    private int currentOffsetDays() {
        Integer v = (Integer) offsetSpinner.getValue();
        return v == null ? 0 : v;
    }

    private void doClear() {
        controller.clear();
        markFieldInvalid(false);
        warningLine.setText(" ");
        updateFilterUI();
    }

    /**
     * "Filter to Selection": derive a focus date from the currently
     * selected primitives and apply.
     *
     * - 1 selected primitive with one open endpoint: focus = the defined
     *   endpoint (start.earliest if start is defined, end.latest if end is).
     * - Otherwise: focus = average of every defined endpoint across all
     *   selected primitives (open ends are skipped).
     *
     * If applying that date hides any of the original selection (FAINT
     * tier → setDisabledState(true)), surface a warning so the user knows
     * the average couldn't cover everything.
     */
    private void doFilterToSelection() {
        DataSet ds = MainApplication.getLayerManager() == null ? null
                : MainApplication.getLayerManager().getEditDataSet();
        if (ds == null) {
            showError(tr("No active OSM data layer."));
            return;
        }
        Collection<OsmPrimitive> selected = ds.getSelected();
        if (selected.isEmpty()) {
            showError(tr("Nothing selected."));
            return;
        }
        Long focusEpoch = computeFocusEpoch(selected);
        if (focusEpoch == null) {
            showError(tr("Selection has no defined dates."));
            return;
        }

        LocalDate focus = LocalDate.ofEpochDay(focusEpoch);
        String formatted = formatLocalDate(focus);
        setPointField.setText(formatted);
        markFieldInvalid(false);

        // Snapshot before apply — the controller deselects FAINT primitives,
        // and the post-apply ds.getSelected() would no longer contain them.
        Set<OsmPrimitive> snapshot = new HashSet<>(selected);

        int offsetDays = currentOffsetDays();
        TimeFilterPreferences.save(formatted, offsetDays);
        controller.apply(formatted, offsetDays, () -> {
            refreshStatus();
            int hidden = 0;
            for (OsmPrimitive p : snapshot) {
                if (p.isDisabledAndHidden()) hidden++;
            }
            if (hidden > 0) {
                NumberFormat nf = NumberFormat.getIntegerInstance();
                showError(tr("Filter date hides {0} of {1} selected items.",
                        nf.format(hidden), nf.format(snapshot.size())));
            }
        });
        warningLine.setText(" ");
    }

    private static Long computeFocusEpoch(Collection<OsmPrimitive> selected) {
        if (selected.size() == 1) {
            OsmPrimitive only = selected.iterator().next();
            DateRange r = PrimitiveDateExtractor.extract(only);
            if (r.isUnparseable()) return null;
            boolean startDef = !r.getStart().isInfinity();
            boolean endDef = !r.getEnd().isInfinity();
            if (startDef && !endDef) return r.getStart().earliestEpochDay();
            if (!startDef && endDef) return r.getEnd().latestEpochDay();
            if (startDef && endDef) {
                return (r.getStart().earliestEpochDay() + r.getEnd().latestEpochDay()) / 2;
            }
            return null;
        }
        long sum = 0;
        int count = 0;
        for (OsmPrimitive p : selected) {
            DateRange r = PrimitiveDateExtractor.extract(p);
            if (r.isUnparseable()) continue;
            if (!r.getStart().isInfinity()) {
                sum += r.getStart().earliestEpochDay();
                count++;
            }
            if (!r.getEnd().isInfinity()) {
                sum += r.getEnd().latestEpochDay();
                count++;
            }
        }
        if (count == 0) return null;
        return sum / count;
    }

    private void refreshStatus() {
        updateFilterUI();
        TimeFilterController.Result r = controller.getLastResult();
        if (!r.ok) {
            warningLine.setText(" ");
            if (r.error != null) showError(r.error);
            return;
        }
        ClassificationCache c = r.cache;
        if (r.window != null && c.getUnparseableCount() > 0) {
            warningLine.setText(tr("{0} primitives have unparseable dates",
                    NumberFormat.getIntegerInstance().format(c.getUnparseableCount())));
        } else {
            warningLine.setText(" ");
        }
    }

    private void markFieldInvalid(boolean invalid) {
        setPointField.setBackground(invalid ? new Color(0xFFE6E6) : normalBorderColor);
    }

    /** Called by the controller's data-change debounce when something changes. */
    public void onDataChanged() {
        if (!controller.isActive()) return;
        SwingUtilities.invokeLater(() -> {
            OhmDate sp = DateParser.parse(setPointField.getText());
            if (sp == null) return;
            try {
                controller.refreshIfActive(new TimeWindow(sp, currentOffsetDays()));
            } catch (IllegalArgumentException ex) {
                // unparseable / invalid — no-op
            }
        });
    }

    /** Cleans up any registered shortcuts/listeners. Called on plugin destroy. */
    @Override
    public void destroy() {
        super.destroy();
    }
}
