package com.linkline.fam.ui.pages;

import com.linkline.fam.AppContext;
import com.linkline.fam.AppInfo;
import com.linkline.fam.platform.AutoStart;
import com.linkline.fam.platform.Os;
import com.linkline.fam.service.EventBus;
import com.linkline.fam.service.SettingsService;
import com.linkline.fam.ui.Dialogs;
import com.linkline.fam.ui.Theme;
import com.linkline.fam.ui.Toast;
import com.linkline.fam.ui.Ui;
import com.linkline.fam.ui.components.Card;
import com.linkline.fam.ui.components.Icons.Glyph;
import com.linkline.fam.ui.components.Page;
import com.linkline.fam.util.AppPaths;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSlider;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;

/** Application preferences. Every change is saved immediately. */
public final class SettingsPage extends Page {

    private final JCheckBox duck = new JCheckBox("Lower other applications while an announcement plays");
    private final JSlider duckLevel = new JSlider(5, 95, 50);
    private final JLabel duckValue = Ui.label("50%", 13f, Theme.TEXT, Font.BOLD);
    private final JComboBox<SettingsService.MissedPolicy> missed = Ui.combo(SettingsService.MissedPolicy.values());
    private final JSpinner grace = new JSpinner(new SpinnerNumberModel(10, 1, 720, 1));
    private final JCheckBox clock24 = new JCheckBox("Use 24-hour time");
    private final JCheckBox tray = new JCheckBox("Keep running in the system tray when the window is closed");
    private final JCheckBox autostart = new JCheckBox("Start FAM when I sign in to Windows");
    private final JSpinner retention = new JSpinner(new SpinnerNumberModel(365, 7, 3650, 7));
    private boolean loading;

    public SettingsPage(AppContext ctx) {
        super(ctx);
        setBorder(Ui.pad(Theme.PAD));

        JPanel column = Ui.transparent(null);
        column.setLayout(new BoxLayout(column, BoxLayout.Y_AXIS));

        // ---- volume priority
        Card volume = section("Volume priority");
        row(volume, "Announcement priority",
                "FAM lowers each other application's own volume, never the Windows master volume, and restores it afterwards.",
                duck);
        JPanel level = Ui.transparent(new BorderLayout(12, 0));
        duckLevel.setMajorTickSpacing(10);
        duckLevel.setPreferredSize(new Dimension(220, 28));
        level.add(duckLevel, BorderLayout.CENTER);
        level.add(duckValue, BorderLayout.EAST);
        row(volume, "Lowered volume",
                "Other applications play at this share of their normal volume during an announcement.", level);
        column.add(volume);

        // ---- missed announcements
        column.add(Ui.vgap(16));
        Card missedCard = section("Missed announcements");
        row(missedCard, "When FAM was not running",
                "Announcements that were due while FAM was closed, or the computer was asleep.", missed);
        JPanel graceRow = Ui.transparent(new java.awt.FlowLayout(java.awt.FlowLayout.RIGHT, 8, 0));
        grace.setPreferredSize(new Dimension(80, 34));
        graceRow.add(grace);
        graceRow.add(Ui.muted("minutes"));
        row(missedCard, "Grace period", "Used by “Play if the delay is within the grace period”.", graceRow);
        column.add(missedCard);

        // ---- general
        column.add(Ui.vgap(16));
        Card general = section("General");
        row(general, "Time format", "How times are shown throughout FAM.", clock24);
        row(general, "Background running", "Announcements keep playing while FAM sits in the tray.", tray);
        row(general, "Startup",
                AutoStart.isSupported() || !Os.isWindows()
                        ? "Opens FAM minimized to the tray when Windows starts."
                        : "Available in the installed version of FAM (FAM.exe).",
                autostart);
        row(general, "History retention", "Older history entries are removed automatically.", retentionRow());
        column.add(general);

        // ---- data
        column.add(Ui.vgap(16));
        Card data = section("Data");
        JButton open = Ui.button("Open folder", Glyph.FOLDER, Ui.Kind.SECONDARY);
        open.addActionListener(e -> openDataFolder());
        row(data, "Data folder", AppPaths.dataDir().toString()
                + "\nDatabase, imported audio and logs are stored here. Nothing is sent online.", open);
        column.add(data);

        column.add(Ui.vgap(16));
        JLabel about = Ui.muted(AppInfo.NAME + " " + AppInfo.VERSION + "  ·  " + AppInfo.SUBTITLE);
        about.setAlignmentX(Component.LEFT_ALIGNMENT);
        column.add(about);

        JPanel wrap = Ui.transparent(new BorderLayout());
        wrap.add(column, BorderLayout.NORTH);
        add(Ui.scroll(wrap), BorderLayout.CENTER);

        wire();
        refreshOn(EventBus.Topic.SETTINGS);
    }

    @Override
    public String title() {
        return "Settings";
    }

    @Override
    public String subtitle() {
        return "Preferences are saved as you change them";
    }

    private JPanel retentionRow() {
        JPanel p = Ui.transparent(new java.awt.FlowLayout(java.awt.FlowLayout.RIGHT, 8, 0));
        retention.setPreferredSize(new Dimension(90, 34));
        p.add(retention);
        p.add(Ui.muted("days"));
        return p;
    }

    // ---- layout helpers -------------------------------------------------------------------------

    private static Card section(String title) {
        Card card = new Card(new GridBagLayout());
        card.setBorder(Ui.pad(16, 22, 8, 22));
        card.setAlignmentX(Component.LEFT_ALIGNMENT);
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0;
        c.gridy = 0;
        c.gridwidth = 2;
        c.anchor = GridBagConstraints.WEST;
        c.insets = new Insets(0, 0, 8, 0);
        card.add(Ui.caption(title), c);
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));
        return card;
    }

    private static void row(Card card, String title, String description, JComponent control) {
        int y = card.getComponentCount();
        GridBagConstraints c = new GridBagConstraints();
        c.gridy = y;
        c.insets = new Insets(10, 0, 10, 0);
        c.anchor = GridBagConstraints.WEST;

        JPanel text = Ui.transparent(new java.awt.GridLayout(0, 1, 0, 2));
        text.add(Ui.label(title, 13.5f, Theme.TEXT, Font.BOLD));
        for (String line : description.split("\n")) {
            text.add(Ui.label("<html><body style='width:380px'>" + line.replace("&", "&amp;").replace("<", "&lt;")
                    + "</body></html>", 12f, Theme.TEXT_MUTED, Font.PLAIN));
        }
        c.gridx = 0;
        c.weightx = 1;
        c.fill = GridBagConstraints.HORIZONTAL;
        card.add(text, c);
        c.gridx = 1;
        c.weightx = 0;
        c.fill = GridBagConstraints.NONE;
        c.anchor = GridBagConstraints.EAST;
        c.insets = new Insets(10, 24, 10, 0);
        card.add(control, c);
    }

    // ---- state ------------------------------------------------------------------------------------

    @Override
    protected void refresh() {
        loading = true;
        SettingsService s = ctx.settings;
        duck.setSelected(s.duckEnabled());
        duckLevel.setValue(s.duckPercent());
        duckLevel.setEnabled(s.duckEnabled());
        duckValue.setText(s.duckPercent() + "%");
        missed.setSelectedItem(s.missedPolicy());
        grace.setValue(s.graceMinutes());
        grace.setEnabled(s.missedPolicy() == SettingsService.MissedPolicy.GRACE);
        clock24.setSelected(s.clock24());
        tray.setSelected(s.minimizeToTray());
        boolean autoOk = Os.isWindows() && AutoStart.isSupported();
        autostart.setEnabled(autoOk);
        autostart.setSelected(autoOk && AutoStart.isEnabled());
        retention.setValue(s.retentionDays());
        loading = false;
    }

    private void wire() {
        SettingsService s = ctx.settings;
        duck.addActionListener(e -> {
            if (!loading) {
                s.setBool(SettingsService.DUCK_ENABLED, duck.isSelected());
            }
        });
        duckLevel.addChangeListener(e -> {
            duckValue.setText(duckLevel.getValue() + "%");
            if (!loading && !duckLevel.getValueIsAdjusting()) {
                s.setInt(SettingsService.DUCK_PERCENT, duckLevel.getValue());
            }
        });
        missed.addActionListener(e -> {
            if (!loading && missed.getSelectedItem() instanceof SettingsService.MissedPolicy p) {
                s.set(SettingsService.MISSED_POLICY, p.name());
            }
        });
        grace.addChangeListener(e -> {
            if (!loading) {
                s.setInt(SettingsService.MISSED_GRACE_MIN, (Integer) grace.getValue());
            }
        });
        clock24.addActionListener(e -> {
            if (!loading) {
                s.setBool(SettingsService.CLOCK_24H, clock24.isSelected());
                Toast.info(this, "Time format updated");
            }
        });
        tray.addActionListener(e -> {
            if (!loading) {
                s.setBool(SettingsService.MINIMIZE_TO_TRAY, tray.isSelected());
            }
        });
        retention.addChangeListener(e -> {
            if (!loading) {
                s.setInt(SettingsService.RETENTION_DAYS, (Integer) retention.getValue());
            }
        });
        autostart.addActionListener(e -> {
            if (loading) {
                return;
            }
            try {
                AutoStart.set(autostart.isSelected());
                s.setBool(SettingsService.AUTOSTART, autostart.isSelected());
            } catch (RuntimeException ex) {
                autostart.setSelected(!autostart.isSelected());
                Dialogs.error(this, "Cannot change startup setting", String.valueOf(ex.getMessage()));
            }
        });
    }

    private void openDataFolder() {
        try {
            java.awt.Desktop.getDesktop().open(AppPaths.dataDir().toFile());
        } catch (Exception e) {
            Dialogs.error(this, "Cannot open folder", AppPaths.dataDir() + "\n\n" + e.getMessage());
        }
    }
}
