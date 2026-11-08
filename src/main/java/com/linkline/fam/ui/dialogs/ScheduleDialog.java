package com.linkline.fam.ui.dialogs;

import com.linkline.fam.AppContext;
import com.linkline.fam.audio.AudioDevice;
import com.linkline.fam.model.AudioItem;
import com.linkline.fam.model.IntervalUnit;
import com.linkline.fam.model.RecurrenceType;
import com.linkline.fam.model.Schedule;
import com.linkline.fam.service.EventBus;
import com.linkline.fam.service.PreviewService;
import com.linkline.fam.ui.Async;
import com.linkline.fam.ui.AudioActions;
import com.linkline.fam.ui.Theme;
import com.linkline.fam.ui.Toast;
import com.linkline.fam.ui.Ui;
import com.linkline.fam.ui.components.Icons;
import com.linkline.fam.ui.components.Icons.Glyph;
import com.linkline.fam.util.Fmt;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Window;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.JToggleButton;
import javax.swing.SpinnerDateModel;
import javax.swing.SpinnerNumberModel;

/** Create or edit an announcement schedule. */
public final class ScheduleDialog extends JDialog {

    /** One entry of the output device list. A null id means "use the device selected in FAM". */
    private record DeviceChoice(String id, String name, String label) {
        @Override
        public String toString() {
            return label;
        }
    }

    private final AppContext ctx;
    private final Schedule schedule;
    private boolean saved;

    private final JTextField name = new JTextField();
    private final JTextField description = new JTextField();
    private final JSpinner time = new JSpinner(new SpinnerDateModel(new Date(), null, null, Calendar.MINUTE));
    private final JCheckBox active = new JCheckBox("Active");
    private final JComboBox<RecurrenceType> recurrence = Ui.combo(RecurrenceType.values());
    private final JToggleButton[] days = new JToggleButton[7];
    private final JSpinner intervalN = new JSpinner(new SpinnerNumberModel(1, 1, 365, 1));
    private final JComboBox<IntervalUnit> intervalUnit = Ui.combo(IntervalUnit.values());
    private final JSpinner startDate = new JSpinner(new SpinnerDateModel(new Date(), null, null, Calendar.DAY_OF_MONTH));
    private final JCheckBox hasEnd = new JCheckBox("Ends on");
    private final JSpinner endDate = new JSpinner(new SpinnerDateModel(new Date(), null, null, Calendar.DAY_OF_MONTH));
    private final JSpinner repeatCount = new JSpinner(new SpinnerNumberModel(1, 1, 20, 1));
    private final JSpinner repeatGap = new JSpinner(new SpinnerNumberModel(5, 0, 600, 1));
    private final JComboBox<DeviceChoice> device = new JComboBox<>();
    private final JTextArea notes = new JTextArea(3, 20);
    private final JLabel audioLabel = Ui.label("", 13.5f, Theme.TEXT, Font.BOLD);
    private final JLabel audioInfo = Ui.muted("");
    private final JButton previewBtn = Ui.button("Preview", Glyph.PLAY, Ui.Kind.GHOST);
    private final JLabel error = Ui.label("", 12.5f, Theme.DANGER, Font.PLAIN);
    private final JPanel recurrenceDetail = Ui.transparent(new GridBagLayout());
    private final JPanel intervalRow = Ui.transparent(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 8, 0));
    private final JPanel daysRow = Ui.transparent(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 6, 0));
    private final JLabel recurrenceHint = Ui.muted("");
    private final Runnable unsubscribe;

    private AudioItem audio;

    private ScheduleDialog(Window owner, AppContext ctx, Schedule existing) {
        super(owner, existing == null ? "New announcement schedule" : "Edit schedule", ModalityType.APPLICATION_MODAL);
        this.ctx = ctx;
        this.schedule = existing == null ? new Schedule() : existing.copy();

        String pattern = Fmt.isClock24() ? "HH:mm" : "hh:mm a";
        time.setEditor(new JSpinner.DateEditor(time, pattern));
        startDate.setEditor(new JSpinner.DateEditor(startDate, "d MMM yyyy"));
        endDate.setEditor(new JSpinner.DateEditor(endDate, "d MMM yyyy"));
        description.putClientProperty("JTextField.placeholderText", "Optional");
        name.putClientProperty("JTextField.placeholderText", "For example: Lunch break");
        notes.setLineWrap(true);
        notes.setWrapStyleWord(true);
        notes.setFont(Theme.font(13f));
        notes.setBorder(Ui.pad(6, 8, 6, 8));
        notes.putClientProperty("JTextField.placeholderText", "Optional notes for other administrators");

        buildDays();
        buildRecurrenceDetail();
        populate();

        JPanel form = new JPanel(new GridBagLayout());
        form.setBorder(Ui.pad(8, 24, 8, 24));
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0;
        c.weightx = 1;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.insets = new Insets(0, 0, 10, 0);
        int y = 0;

        c.gridy = y++;
        form.add(section("Announcement"), c);
        c.gridy = y++;
        form.add(field("Name", name), c);
        c.gridy = y++;
        form.add(field("Description", description), c);
        c.gridy = y++;
        JPanel timeRow = Ui.transparent(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 16, 0));
        time.setPreferredSize(new Dimension(130, 34));
        timeRow.add(time);
        timeRow.add(active);
        form.add(field("Time", timeRow), c);

        c.gridy = y++;
        form.add(section("Audio"), c);
        c.gridy = y++;
        form.add(audioPanel(), c);

        c.gridy = y++;
        form.add(section("Repeat"), c);
        c.gridy = y++;
        form.add(field("Repeats", recurrence), c);
        c.gridy = y++;
        form.add(recurrenceDetail, c);
        c.gridy = y++;
        JPanel dates = Ui.transparent(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 10, 0));
        startDate.setPreferredSize(new Dimension(130, 34));
        endDate.setPreferredSize(new Dimension(130, 34));
        dates.add(startDate);
        dates.add(hasEnd);
        dates.add(endDate);
        form.add(field("Starts on", dates), c);

        c.gridy = y++;
        form.add(section("Playback"), c);
        c.gridy = y++;
        form.add(field("Output", device), c);
        c.gridy = y++;
        JPanel plays = Ui.transparent(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 8, 0));
        repeatCount.setPreferredSize(new Dimension(70, 34));
        repeatGap.setPreferredSize(new Dimension(80, 34));
        plays.add(repeatCount);
        plays.add(Ui.muted("time(s), with"));
        plays.add(repeatGap);
        plays.add(Ui.muted("seconds between"));
        form.add(field("Play", plays), c);

        c.gridy = y++;
        form.add(section("Notes"), c);
        c.gridy = y++;
        form.add(notes, c);

        c.gridy = y;
        c.weighty = 1;
        form.add(Ui.transparent(new BorderLayout()), c);

        JButton cancel = Ui.button("Cancel", null, Ui.Kind.SECONDARY);
        JButton save = Ui.button("Save schedule", Glyph.CHECK, Ui.Kind.PRIMARY);
        cancel.addActionListener(e -> close());
        save.addActionListener(e -> save(save));
        getRootPane().setDefaultButton(save);

        JPanel footer = new JPanel(new BorderLayout(12, 0));
        footer.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, Theme.BORDER), Ui.pad(14, 24, 14, 24)));
        error.setVerticalAlignment(javax.swing.SwingConstants.CENTER);
        footer.add(error, BorderLayout.CENTER);
        JPanel buttons = Ui.transparent(new java.awt.FlowLayout(java.awt.FlowLayout.RIGHT, 8, 0));
        buttons.add(cancel);
        buttons.add(save);
        footer.add(buttons, BorderLayout.EAST);

        JPanel root = new JPanel(new BorderLayout());
        root.add(Ui.scroll(form), BorderLayout.CENTER);
        root.add(footer, BorderLayout.SOUTH);
        setContentPane(root);

        previewBtn.addActionListener(e -> togglePreview());
        unsubscribe = ctx.bus.subscribe(EventBus.Topic.PREVIEW, this::updatePreviewButton);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                close();
            }
        });
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        setMinimumSize(new Dimension(640, 520));
        setSize(700, 780);
        setLocationRelativeTo(owner);
    }

    /** Opens the dialog; pass null to create a new schedule. @return true if a schedule was saved */
    public static boolean open(Window owner, AppContext ctx, Schedule existing) {
        ScheduleDialog d = new ScheduleDialog(owner, ctx, existing);
        d.setVisible(true);
        return d.saved;
    }

    // ---- building blocks ------------------------------------------------------------------

    private static JComponent section(String title) {
        JPanel p = Ui.transparent(new BorderLayout());
        p.setBorder(Ui.pad(10, 0, 0, 0));
        p.add(Ui.caption(title), BorderLayout.WEST);
        return p;
    }

    private static JComponent field(String label, JComponent input) {
        JPanel p = Ui.transparent(new BorderLayout(12, 0));
        JLabel l = Ui.label(label, 13f, Theme.TEXT_MUTED, Font.PLAIN);
        l.setPreferredSize(new Dimension(92, 34));
        p.add(l, BorderLayout.WEST);
        p.add(input, BorderLayout.CENTER);
        return p;
    }

    private void buildDays() {
        String[] names = {"Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"};
        for (int i = 0; i < 7; i++) {
            JToggleButton t = new JToggleButton(names[i]);
            t.setFont(Theme.font(12f));
            t.setFocusable(false);
            t.putClientProperty("FlatLaf.style", "arc: 8; selectedBackground: " + Theme.hex(Theme.ACCENT)
                    + "; selectedForeground: #FFFFFF; margin: 5,9,5,9");
            days[i] = t;
            daysRow.add(t);
        }
    }

    private void buildRecurrenceDetail() {
        intervalN.setPreferredSize(new Dimension(70, 34));
        intervalRow.add(Ui.muted("Every"));
        intervalRow.add(intervalN);
        intervalRow.add(intervalUnit);

        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0;
        c.weightx = 1;
        c.anchor = GridBagConstraints.WEST;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.insets = new Insets(0, 104, 6, 0);
        c.gridy = 0;
        recurrenceDetail.add(intervalRow, c);
        c.gridy = 1;
        recurrenceDetail.add(daysRow, c);
        c.gridy = 2;
        c.insets = new Insets(0, 104 + 6, 0, 0);
        recurrenceDetail.add(recurrenceHint, c);
        recurrence.addActionListener(e -> updateRecurrenceDetail());
        intervalUnit.addActionListener(e -> updateRecurrenceDetail());
    }

    private void updateRecurrenceDetail() {
        RecurrenceType r = (RecurrenceType) recurrence.getSelectedItem();
        boolean custom = r == RecurrenceType.CUSTOM;
        boolean weeks = custom && intervalUnit.getSelectedItem() == IntervalUnit.WEEKS;
        intervalRow.setVisible(custom);
        daysRow.setVisible(r == RecurrenceType.SELECTED_DAYS || weeks);
        String hint = "";
        if (r == RecurrenceType.ONCE) {
            hint = "Plays once, on the start date.";
        } else if (weeks) {
            hint = "Pick the days of the week; none selected means the weekday of the start date.";
        } else if (custom) {
            hint = "Counted from the start date.";
        }
        recurrenceHint.setText(hint);
        recurrenceHint.setVisible(!hint.isEmpty());
        recurrenceDetail.revalidate();
        recurrenceDetail.repaint();
    }

    private JComponent audioPanel() {
        JPanel box = new JPanel(new BorderLayout(12, 0));
        box.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Theme.BORDER, 1, true), Ui.pad(12, 14, 12, 14)));
        box.setBackground(Theme.SURFACE_2);

        JPanel texts = Ui.transparent(new java.awt.GridLayout(2, 1, 0, 2));
        texts.add(audioLabel);
        texts.add(audioInfo);
        box.add(texts, BorderLayout.CENTER);

        JButton choose = Ui.button("Choose…", Glyph.MUSIC, Ui.Kind.SECONDARY);
        JButton imp = Ui.button("Import…", Glyph.IMPORT, Ui.Kind.GHOST);
        choose.addActionListener(e -> {
            AudioItem picked = AudioPickerDialog.pick(this, ctx, audio == null ? null : audio.id());
            if (picked != null) {
                setAudio(picked);
            }
        });
        imp.addActionListener(e -> AudioActions.importFiles(this, ctx, items -> setAudio(items.get(0))));
        JPanel buttons = Ui.transparent(new java.awt.FlowLayout(java.awt.FlowLayout.RIGHT, 6, 0));
        buttons.add(previewBtn);
        buttons.add(imp);
        buttons.add(choose);
        box.add(buttons, BorderLayout.EAST);
        return box;
    }

    // ---- state ----------------------------------------------------------------------------------

    private void populate() {
        Schedule s = schedule;
        name.setText(s.getName());
        description.setText(s.getDescription());
        notes.setText(s.getNotes());
        time.setValue(toDate(s.getTime()));
        active.setSelected(s.getId() == 0 || s.isEnabled());
        recurrence.setSelectedItem(s.getRecurrence());
        intervalN.setValue(Math.max(1, s.getIntervalN()));
        intervalUnit.setSelectedItem(s.getIntervalUnit());
        DayOfWeek[] all = DayOfWeek.values();
        for (int i = 0; i < 7; i++) {
            days[i].setSelected(s.hasDay(all[i]));
        }
        startDate.setValue(toDate(s.getStartDate()));
        hasEnd.setSelected(s.getEndDate() != null);
        endDate.setValue(toDate(s.getEndDate() == null ? s.getStartDate().plusMonths(1) : s.getEndDate()));
        endDate.setEnabled(hasEnd.isSelected());
        hasEnd.addActionListener(e -> endDate.setEnabled(hasEnd.isSelected()));
        repeatCount.setValue(Math.max(1, s.getRepeatCount()));
        repeatGap.setValue(Math.max(0, s.getRepeatGapSec()));
        updateRecurrenceDetail();

        device.addItem(new DeviceChoice(null, null, "Use FAM's announcement output"));
        String savedId = s.getDeviceId();
        boolean found = savedId == null;
        for (AudioDevice d : ctx.devices.devices()) {
            if (d.isUsable() || Objects.equals(d.id(), savedId)) {
                device.addItem(new DeviceChoice(d.id(), d.name(), d.name() + (d.isDefault() ? "  (Windows default)" : "")));
            }
            if (Objects.equals(d.id(), savedId)) {
                found = true;
            }
        }
        if (!found) {
            device.addItem(new DeviceChoice(savedId, s.getDeviceName(),
                    (s.getDeviceName() == null ? savedId : s.getDeviceName()) + "  (not connected)"));
        }
        for (int i = 0; i < device.getItemCount(); i++) {
            if (Objects.equals(device.getItemAt(i).id(), savedId)) {
                device.setSelectedIndex(i);
            }
        }

        if (s.getAudioId() != null) {
            ctx.schedules.audioOf(s).ifPresentOrElse(this::setAudio, () -> setAudio(null));
        } else {
            setAudio(null);
        }
    }

    private void setAudio(AudioItem item) {
        audio = item;
        updatePreviewButton();
        if (item == null) {
            audioLabel.setIcon(Icons.of(Glyph.WARN, 18, Theme.WARNING));
            audioLabel.setText("No audio selected");
            audioInfo.setText("Choose a file from the library or import a new one.");
            return;
        }
        audioLabel.setIcon(Icons.of(Glyph.MUSIC, 18, Theme.ACCENT));
        audioLabel.setIconTextGap(8);
        audioLabel.setText(item.displayName());
        audioInfo.setText(item.format() + " · " + Fmt.duration(item.durationMs()) + " · " + Fmt.size(item.sizeBytes()));
        Async.run(() -> ctx.scheduleRepo.findByAudio(item.id()), using -> {
            if (audio == null || audio.id() != item.id()) {
                return;
            }
            List<String> others = new ArrayList<>();
            for (Schedule o : using) {
                if (o.getId() != schedule.getId()) {
                    others.add(o.getName());
                }
            }
            if (!others.isEmpty()) {
                audioInfo.setText(audioInfo.getText() + " · Also used by " + String.join(", ", others));
            }
        }, e -> { });
    }

    private void togglePreview() {
        if (audio == null) {
            return;
        }
        PreviewService p = ctx.preview;
        if (p.currentId() == audio.id() && p.state() != PreviewService.State.STOPPED) {
            p.stop();
        } else {
            p.play(audio, msg -> javax.swing.SwingUtilities.invokeLater(() -> Toast.error(this, msg)));
        }
    }

    private void updatePreviewButton() {
        boolean playing = audio != null && ctx.preview.currentId() == audio.id()
                && ctx.preview.state() != PreviewService.State.STOPPED;
        previewBtn.setText(playing ? "Stop" : "Preview");
        previewBtn.setIcon(Icons.of(playing ? Glyph.STOP : Glyph.PLAY, 16, Theme.TEXT_MUTED));
        previewBtn.setEnabled(audio != null);
    }

    // ---- save -----------------------------------------------------------------------------------

    private void save(JButton saveButton) {
        error.setText("");
        Schedule s = schedule;
        s.setName(name.getText().strip());
        s.setDescription(description.getText().strip());
        s.setNotes(notes.getText().strip());
        s.setTime(toTime((Date) time.getValue()));
        s.setEnabled(active.isSelected());
        s.setRecurrence((RecurrenceType) recurrence.getSelectedItem());
        s.setIntervalN((Integer) intervalN.getValue());
        s.setIntervalUnit((IntervalUnit) intervalUnit.getSelectedItem());
        int mask = 0;
        DayOfWeek[] all = DayOfWeek.values();
        for (int i = 0; i < 7; i++) {
            if (days[i].isSelected()) {
                mask |= Schedule.bit(all[i]);
            }
        }
        // Days only matter for the two day-based modes; clear them otherwise so stale values never apply.
        boolean usesDays = s.getRecurrence() == RecurrenceType.SELECTED_DAYS
                || (s.getRecurrence() == RecurrenceType.CUSTOM && s.getIntervalUnit() == IntervalUnit.WEEKS);
        s.setDaysMask(usesDays ? mask : 0);
        s.setStartDate(toDate((Date) startDate.getValue()));
        s.setEndDate(hasEnd.isSelected() ? toDate((Date) endDate.getValue()) : null);
        s.setRepeatCount((Integer) repeatCount.getValue());
        s.setRepeatGapSec((Integer) repeatGap.getValue());
        DeviceChoice dc = (DeviceChoice) device.getSelectedItem();
        s.setDeviceId(dc == null ? null : dc.id());
        s.setDeviceName(dc == null ? null : dc.name());
        s.setAudioId(audio == null ? null : audio.id());
        s.setAudioName(audio == null ? null : audio.displayName());

        try {
            ctx.schedules.validate(s);
        } catch (IllegalArgumentException e) {
            error.setText(e.getMessage());
            return;
        }
        saveButton.setEnabled(false);
        Async.run(() -> ctx.schedules.save(s), id -> {
            saved = true;
            close();
        }, e -> {
            saveButton.setEnabled(true);
            error.setText(e.getMessage() == null ? "The schedule could not be saved." : e.getMessage());
        });
    }

    private void close() {
        ctx.preview.stop();
        unsubscribe.run();
        dispose();
    }

    // ---- date helpers -----------------------------------------------------------------------------

    private static Date toDate(LocalTime t) {
        return Date.from(LocalDate.now().atTime(t).atZone(ZoneId.systemDefault()).toInstant());
    }

    private static Date toDate(LocalDate d) {
        return Date.from(d.atStartOfDay(ZoneId.systemDefault()).toInstant());
    }

    private static LocalTime toTime(Date d) {
        return LocalTime.ofInstant(d.toInstant(), ZoneId.systemDefault());
    }

    private static LocalDate toDate(Date d) {
        return d.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
    }
}
