package com.linkline.fam.ui.pages;

import com.linkline.fam.AppContext;
import com.linkline.fam.model.HistoryEntry;
import com.linkline.fam.model.PlayStatus;
import com.linkline.fam.service.EventBus;
import com.linkline.fam.ui.Async;
import com.linkline.fam.ui.Dialogs;
import com.linkline.fam.ui.Theme;
import com.linkline.fam.ui.Toast;
import com.linkline.fam.ui.Ui;
import com.linkline.fam.ui.components.Icons.Glyph;
import com.linkline.fam.ui.components.Page;
import com.linkline.fam.ui.components.StatusStyle;
import com.linkline.fam.ui.components.Tables;
import com.linkline.fam.util.Fmt;
import java.awt.BorderLayout;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.Timer;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.AbstractTableModel;

/** Every announcement FAM has played, skipped or failed, newest first. */
public final class HistoryPage extends Page {

    private enum Range {
        TODAY("Today"), WEEK("Last 7 days"), MONTH("Last 30 days"), ALL("All time");

        private final String label;

        Range(String label) {
            this.label = label;
        }

        LocalDateTime from() {
            LocalDate today = LocalDate.now();
            switch (this) {
                case TODAY:
                    return today.atStartOfDay();
                case WEEK:
                    return today.minusDays(6).atStartOfDay();
                case MONTH:
                    return today.minusDays(29).atStartOfDay();
                default:
                    return null;
            }
        }

        @Override
        public String toString() {
            return label;
        }
    }

    /** Wraps "all statuses" so it can sit in the same combo as the real ones. */
    private record StatusFilter(PlayStatus status, String label) {
        @Override
        public String toString() {
            return label;
        }
    }

    private static final int LIMIT = 1000;

    private final class Model extends AbstractTableModel {
        private final String[] cols = {"Planned", "Announcement", "Audio", "Output", "Trigger", "Status", "Details"};
        private List<HistoryEntry> rows = List.of();

        void set(List<HistoryEntry> r) {
            rows = r;
            fireTableDataChanged();
        }

        @Override public int getRowCount() { return rows.size(); }
        @Override public int getColumnCount() { return cols.length; }
        @Override public String getColumnName(int c) { return cols[c]; }

        @Override
        public Object getValueAt(int r, int c) {
            HistoryEntry h = rows.get(r);
            switch (c) {
                case 0:
                    return Fmt.dateTime(h.plannedAt());
                case 1:
                    return h.scheduleName();
                case 2:
                    return h.audioName();
                case 3:
                    return h.deviceName();
                case 4:
                    return h.trigger().toString();
                case 5:
                    return new Tables.IconText(StatusStyle.marker(h.status(), 20), h.status().label(),
                            h.status() == PlayStatus.FAILED ? Theme.DANGER
                            : h.status() == PlayStatus.COMPLETED ? Theme.SUCCESS : null, null);
                default:
                    return h.message();
            }
        }
    }

    private final Model model = new Model();
    private final JTable table = Tables.create(model);
    private final JComboBox<Range> range = Ui.combo(Range.values());
    private final JComboBox<StatusFilter> status;
    private final JTextField search = Ui.search("Search announcements");
    private final JLabel count = Ui.muted("");
    private final Timer searchDelay = new Timer(250, e -> refresh());

    public HistoryPage(AppContext ctx) {
        super(ctx);
        setBorder(Ui.pad(Theme.PAD));

        List<StatusFilter> statuses = new ArrayList<>();
        statuses.add(new StatusFilter(null, "All statuses"));
        for (PlayStatus s : PlayStatus.values()) {
            if (s != PlayStatus.QUEUED) {
                statuses.add(new StatusFilter(s, s.label()));
            }
        }
        status = Ui.combo(statuses.toArray(new StatusFilter[0]));
        range.setSelectedItem(Range.WEEK);
        searchDelay.setRepeats(false);

        JButton export = Ui.button("Export CSV", Glyph.EXPORT, Ui.Kind.SECONDARY);
        JButton clear = Ui.button("Clear history", Glyph.TRASH, Ui.Kind.GHOST);
        export.addActionListener(e -> export());
        clear.addActionListener(e -> clear());

        JPanel left = Ui.transparent(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 10, 0));
        left.add(search);
        left.add(range);
        left.add(status);
        left.add(count);
        JPanel right = Ui.transparent(new java.awt.FlowLayout(java.awt.FlowLayout.RIGHT, 6, 0));
        right.add(clear);
        right.add(export);
        JPanel bar = Ui.transparent(new BorderLayout());
        bar.setBorder(Ui.pad(0, 0, 12, 0));
        bar.add(left, BorderLayout.WEST);
        bar.add(right, BorderLayout.EAST);

        table.setDefaultRenderer(Object.class, new Tables.TextRenderer(true));
        table.getColumnModel().getColumn(1).setCellRenderer(new Tables.TextRenderer(false));
        table.getColumnModel().getColumn(5).setCellRenderer(new Tables.TextRenderer(false));
        Tables.widths(table, 150, 170, 150, 150, 80, 110, 360);

        add(bar, BorderLayout.NORTH);
        add(Tables.card(table), BorderLayout.CENTER);

        range.addActionListener(e -> refresh());
        status.addActionListener(e -> refresh());
        search.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e) { searchDelay.restart(); }
            @Override public void removeUpdate(DocumentEvent e) { searchDelay.restart(); }
            @Override public void changedUpdate(DocumentEvent e) { searchDelay.restart(); }
        });
        refreshOn(EventBus.Topic.HISTORY);
    }

    @Override
    public String title() {
        return "Announcement History";
    }

    @Override
    public String subtitle() {
        return "What played, when, and through which output";
    }

    @Override
    protected void refresh() {
        LocalDateTime from = ((Range) range.getSelectedItem()).from();
        PlayStatus st = ((StatusFilter) status.getSelectedItem()).status();
        String text = search.getText();
        Async.run(() -> ctx.history.search(from, null, st, text, LIMIT), rows -> {
            model.set(rows);
            count.setText(rows.size() >= LIMIT ? "Latest " + LIMIT : rows.size() + (rows.size() == 1 ? " entry" : " entries"));
        }, e -> Toast.error(this, "Could not load the history: " + e.getMessage()));
    }

    private void export() {
        Path target = Dialogs.chooseSaveFile(this, "Export history", "fam-history.csv");
        if (target == null) {
            return;
        }
        List<HistoryEntry> rows = new ArrayList<>(model.rows);
        Async.run(() -> {
            StringBuilder sb = new StringBuilder("﻿Planned,Started,Finished,Announcement,Audio,Output,Trigger,Status,Details\r\n");
            for (HistoryEntry h : rows) {
                sb.append(csv(Fmt.dateTimeSeconds(h.plannedAt()))).append(',')
                        .append(csv(Fmt.dateTimeSeconds(h.startedAt()))).append(',')
                        .append(csv(Fmt.dateTimeSeconds(h.finishedAt()))).append(',')
                        .append(csv(h.scheduleName())).append(',')
                        .append(csv(h.audioName())).append(',')
                        .append(csv(h.deviceName())).append(',')
                        .append(csv(h.trigger().toString())).append(',')
                        .append(csv(h.status().label())).append(',')
                        .append(csv(h.message())).append("\r\n");
            }
            try {
                Files.writeString(target, sb.toString(), StandardCharsets.UTF_8);
            } catch (IOException e) {
                throw new IllegalStateException("Could not write the file: " + e.getMessage(), e);
            }
        }, () -> Toast.info(this, "Exported " + rows.size() + " entries"),
                e -> Dialogs.error(this, "Export failed", String.valueOf(e.getMessage())));
    }

    private static String csv(String v) {
        String s = v == null ? "" : v;
        return "\"" + s.replace("\"", "\"\"") + "\"";
    }

    private void clear() {
        if (Dialogs.confirm(this, "Clear history",
                "Delete all finished history entries?\nSchedules and audio files are not affected.", "Clear history", true)) {
            Async.run(ctx.history::clearAll, () -> {
                ctx.bus.publish(EventBus.Topic.HISTORY);
                Toast.info(this, "History cleared");
            }, e -> Dialogs.error(this, "Cannot clear history", String.valueOf(e.getMessage())));
        }
    }
}