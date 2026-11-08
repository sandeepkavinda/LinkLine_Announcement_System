package com.linkline.fam.ui.pages;

import com.linkline.fam.AppContext;
import com.linkline.fam.audio.AudioDevice;
import com.linkline.fam.audio.DeviceState;
import com.linkline.fam.service.DeviceService;
import com.linkline.fam.service.EventBus;
import com.linkline.fam.service.SettingsService;
import com.linkline.fam.ui.Async;
import com.linkline.fam.ui.Dialogs;
import com.linkline.fam.ui.Theme;
import com.linkline.fam.ui.Toast;
import com.linkline.fam.ui.Ui;
import com.linkline.fam.ui.components.Card;
import com.linkline.fam.ui.components.Icons;
import com.linkline.fam.ui.components.Icons.Glyph;
import com.linkline.fam.ui.components.Page;
import com.linkline.fam.ui.components.Tables;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Font;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTable;
import javax.swing.table.AbstractTableModel;

/** Detects Windows output devices and chooses where announcements play. */
public final class DevicesPage extends Page {

    /** One row: a detected device, or a saved device that is no longer connected. */
    private record Entry(String id, String name, boolean isDefault, DeviceState state, boolean selected) {
    }

    private final class Model extends AbstractTableModel {
        private final String[] cols = {"", "Device", "Status", "Windows"};
        private List<Entry> rows = List.of();

        void set(List<Entry> r) {
            rows = r;
            fireTableDataChanged();
        }

        Entry at(int i) {
            return i >= 0 && i < rows.size() ? rows.get(i) : null;
        }

        @Override public int getRowCount() { return rows.size(); }
        @Override public int getColumnCount() { return cols.length; }
        @Override public String getColumnName(int c) { return cols[c]; }

        @Override
        public Object getValueAt(int r, int c) {
            Entry e = rows.get(r);
            switch (c) {
                case 0:
                    return e.selected() ? Icons.of(Glyph.CHECK, 18, Theme.ACCENT) : null;
                case 1:
                    return new Tables.IconText(Icons.of(Glyph.SPEAKER, 18, e.state().isUsable() ? Theme.TEXT_MUTED : Theme.TEXT_FAINT),
                            e.name() + (e.selected() ? "   · used for announcements" : ""),
                            e.state().isUsable() ? null : Theme.TEXT_FAINT, null);
                case 2:
                    return new Tables.IconText(Ui.dot(e.state().isUsable() ? Theme.SUCCESS : Theme.DANGER, 16),
                            e.state().label(), e.state().isUsable() ? null : Theme.DANGER, null);
                default:
                    return e.isDefault() ? "Default device" : "";
            }
        }
    }

    private final Model model = new Model();
    private final JTable table = Tables.create(model);
    private final JLabel statusIcon = new JLabel();
    private final JLabel statusText = Ui.label("", 15f, Theme.TEXT, Font.BOLD);
    private final JLabel statusSub = Ui.muted("");
    private final JLabel source = Ui.label("", 11.5f, Theme.TEXT_FAINT, Font.PLAIN);
    private final JButton use = Ui.button("Use for announcements", Glyph.CHECK, Ui.Kind.PRIMARY);
    private final JButton useDefault = Ui.button("Use Windows default", null, Ui.Kind.SECONDARY);
    private final JButton refreshBtn = Ui.button("Refresh", Glyph.REFRESH, Ui.Kind.SECONDARY);
    private final JButton test = Ui.button("Test output", Glyph.PLAY, Ui.Kind.SECONDARY);
    private final JComboBox<SettingsService.FallbackMode> fallback = Ui.combo(SettingsService.FallbackMode.values());
    private boolean loading;

    public DevicesPage(AppContext ctx) {
        super(ctx);
        setBorder(Ui.pad(Theme.PAD));

        add(statusCard(), BorderLayout.NORTH);

        table.setRowHeight(48);
        table.getColumnModel().getColumn(0).setCellRenderer(new Tables.IconRenderer());
        table.getColumnModel().getColumn(1).setCellRenderer(new Tables.TextRenderer(false));
        table.getColumnModel().getColumn(2).setCellRenderer(new Tables.TextRenderer(false));
        table.getColumnModel().getColumn(3).setCellRenderer(new Tables.TextRenderer(true));
        Tables.widths(table, -48, 420, 160, 160);

        JPanel center = Ui.transparent(new BorderLayout(0, 10));
        center.setBorder(Ui.pad(16, 0, 0, 0));
        JPanel bar = Ui.transparent(new BorderLayout());
        JPanel left = Ui.transparent(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 8, 0));
        left.add(use);
        left.add(useDefault);
        bar.add(left, BorderLayout.WEST);
        bar.add(refreshBtn, BorderLayout.EAST);
        center.add(bar, BorderLayout.NORTH);
        center.add(Tables.card(table), BorderLayout.CENTER);
        add(center, BorderLayout.CENTER);

        Card fb = new Card(new BorderLayout(16, 0));
        fb.setBorder(Ui.pad(14, 20, 14, 20));
        JPanel fbText = Ui.transparent(new java.awt.GridLayout(2, 1, 0, 2));
        fbText.add(Ui.heading("If the selected device is disconnected"));
        fbText.add(Ui.muted("Decides what happens when an announcement is due and its device is not available."));
        fb.add(fbText, BorderLayout.CENTER);
        fb.add(fallback, BorderLayout.EAST);
        JPanel south = Ui.transparent(new BorderLayout());
        south.setBorder(Ui.pad(16, 0, 0, 0));
        south.add(fb, BorderLayout.CENTER);
        add(south, BorderLayout.SOUTH);

        fallback.setSelectedItem(ctx.settings.fallbackMode());
        fallback.addActionListener(e -> {
            if (!loading && fallback.getSelectedItem() instanceof SettingsService.FallbackMode m) {
                ctx.settings.set(SettingsService.DEVICE_FALLBACK, m.name());
            }
        });

        table.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                selectionChanged();
            }
        });
        Tables.onDoubleClick(table, row -> useSelected());
        use.addActionListener(e -> useSelected());
        useDefault.addActionListener(e -> {
            ctx.settings.setDevice(null, null);
            Toast.info(this, "Announcements now use the Windows default output");
        });
        refreshBtn.addActionListener(e -> {
            refreshBtn.setEnabled(false);
            Async.run(ctx.devices::refresh, () -> {
                refreshBtn.setEnabled(true);
                refresh();
                Toast.info(this, "Device list updated");
            }, err -> {
                refreshBtn.setEnabled(true);
                Dialogs.error(this, "Cannot refresh", String.valueOf(err.getMessage()));
            });
        });
        test.addActionListener(e -> testOutput());
        selectionChanged();

        refreshOn(EventBus.Topic.DEVICES, EventBus.Topic.SETTINGS);
    }

    @Override
    public String title() {
        return "Audio Devices";
    }

    @Override
    public String subtitle() {
        return "Choose where announcements play";
    }

    private JPanel statusCard() {
        Card card = new Card(new BorderLayout(16, 0));
        JPanel texts = Ui.transparent(new java.awt.GridLayout(3, 1, 0, 3));
        texts.add(statusText);
        texts.add(statusSub);
        texts.add(source);
        card.add(statusIcon, BorderLayout.WEST);
        card.add(texts, BorderLayout.CENTER);
        card.add(test, BorderLayout.EAST);
        return card;
    }

    @Override
    protected void refresh() {
        loading = true;
        DeviceService ds = ctx.devices;
        DeviceService.SelectionStatus st = ds.selectionStatus();
        Color c = st.health() == DeviceService.Health.OK ? Theme.SUCCESS
                : st.health() == DeviceService.Health.WARNING ? Theme.WARNING : Theme.DANGER;
        statusIcon.setIcon(Ui.dot(c, 28));
        statusText.setText(st.text());
        boolean explicit = ctx.settings.deviceId() != null;
        statusSub.setText(explicit
                ? "Announcements play on this device. Other Windows audio keeps using its own output."
                : "No device chosen. Announcements play on the Windows default output.");
        source.setText("Detected with " + ds.catalogDescription()
                + (ds.lastError() == null ? "" : " — " + ds.lastError()));

        String selId = ctx.settings.deviceId();
        String selName = ctx.settings.deviceName();
        List<Entry> rows = new ArrayList<>();
        boolean found = false;
        AudioDevice chosen = ds.selectedDevice().orElse(null);
        for (AudioDevice d : ds.devices()) {
            boolean sel = chosen != null && d.equals(chosen);
            found |= sel;
            rows.add(new Entry(d.id(), d.name(), d.isDefault(), d.state(), sel));
        }
        if (selId != null && !found) {
            rows.add(new Entry(selId, selName == null ? selId : selName, false, DeviceState.NOT_PRESENT, true));
        }
        // usable devices first, then the rest
        rows.sort((a, b) -> Boolean.compare(b.state().isUsable(), a.state().isUsable()));
        model.set(rows);
        fallback.setSelectedItem(ctx.settings.fallbackMode());
        useDefault.setEnabled(explicit);
        loading = false;
        selectionChanged();
    }

    private void selectionChanged() {
        Entry e = model.at(table.getSelectedRow());
        use.setEnabled(e != null && e.state().isUsable() && !e.selected());
    }

    private void useSelected() {
        Entry e = model.at(table.getSelectedRow());
        if (e == null || !e.state().isUsable()) {
            return;
        }
        ctx.settings.setDevice(e.id(), e.name());
        Toast.info(this, "Announcements will play on “" + e.name() + "”");
    }

    private void testOutput() {
        String out = ctx.devices.announcementOutputName();
        if (Dialogs.confirm(this, "Test announcement output",
                "FAM will play a short test chime through\n" + out + "\n\nOther applications are lowered while it plays.",
                "Play test", false)) {
            Async.run(() -> ctx.announcements.enqueueTest(null), () -> Toast.info(this, "Test chime sent"),
                    e -> Dialogs.error(this, "Test failed", String.valueOf(e.getMessage())));
        }
    }
}
