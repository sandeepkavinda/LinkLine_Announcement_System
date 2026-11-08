package com.linkline.fam.ui.pages;

import com.linkline.fam.AppContext;
import com.linkline.fam.model.Schedule;
import com.linkline.fam.service.EventBus;
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
import com.linkline.fam.ui.dialogs.ScheduleDialog;
import com.linkline.fam.util.Fmt;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Font;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.AbstractTableModel;

/** Create, edit, delete and switch announcement schedules on or off. */
public final class SchedulesPage extends Page {

    private enum View {
        ALL("All schedules"), ACTIVE("Active"), INACTIVE("Inactive");

        private final String label;

        View(String label) {
            this.label = label;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    private static final class Row {
        final Schedule schedule;
        final LocalDateTime next;

        Row(Schedule schedule, LocalDateTime next) {
            this.schedule = schedule;
            this.next = next;
        }
    }

    private final class Model extends AbstractTableModel {
        private final String[] cols = {"", "Announcement", "Time", "Repeats", "Audio", "Output", "Next run"};
        private List<Row> all = List.of();
        private List<Row> view = List.of();

        void set(List<Row> rows) {
            all = rows;
            filter();
        }

        void filter() {
            String q = search.getText().strip().toLowerCase(Locale.ROOT);
            View v = (View) viewFilter.getSelectedItem();
            List<Row> out = new ArrayList<>();
            for (Row r : all) {
                Schedule s = r.schedule;
                if (v == View.ACTIVE && !s.isEnabled() || v == View.INACTIVE && s.isEnabled()) {
                    continue;
                }
                if (!q.isEmpty() && !(s.getName() + " " + s.getDescription() + " "
                        + (s.getAudioName() == null ? "" : s.getAudioName())).toLowerCase(Locale.ROOT).contains(q)) {
                    continue;
                }
                out.add(r);
            }
            view = out;
            fireTableDataChanged();
        }

        Schedule at(int row) {
            return row >= 0 && row < view.size() ? view.get(row).schedule : null;
        }

        int rowOf(long id) {
            for (int i = 0; i < view.size(); i++) {
                if (view.get(i).schedule.getId() == id) {
                    return i;
                }
            }
            return -1;
        }

        @Override public int getRowCount() { return view.size(); }
        @Override public int getColumnCount() { return cols.length; }
        @Override public String getColumnName(int c) { return cols[c]; }

        @Override
        public Object getValueAt(int r, int c) {
            Row row = view.get(r);
            Schedule s = row.schedule;
            switch (c) {
                case 0:
                    return s.isEnabled() ? Ui.dot(Theme.SUCCESS, 16) : Ui.dot(Theme.TEXT_FAINT, 16);
                case 1:
                    return new Tables.TwoLine(null, s.getName(), s.getDescription());
                case 2:
                    return Fmt.time(s.getTime());
                case 3:
                    return s.describeRecurrence();
                case 4:
                    if (s.getAudioId() == null) {
                        return new Tables.IconText(Icons.of(Glyph.WARN, 16, Theme.WARNING), "No audio", Theme.WARNING,
                                "Assign an audio file before this schedule can play.");
                    }
                    return new Tables.IconText(Icons.of(Glyph.MUSIC, 16, Theme.TEXT_MUTED), s.getAudioName());
                case 5:
                    return s.getDeviceName() == null ? "FAM output" : s.getDeviceName();
                default:
                    return s.isEnabled() ? Ui.relativeDateTime(row.next) : "—";
            }
        }
    }

    private final JTextField search = Ui.search("Search schedules");
    private final JComboBox<View> viewFilter = Ui.combo(View.values());
    private final Model model = new Model();
    private final JTable table = Tables.create(model);
    private final JLabel count = Ui.muted("");
    private final JPanel center = new JPanel(new CardLayout());
    private final JButton edit = Ui.button("Edit", Glyph.EDIT, Ui.Kind.GHOST);
    private final JButton duplicate = Ui.button("Duplicate", Glyph.COPY, Ui.Kind.GHOST);
    private final JButton playNow = Ui.button("Play now", Glyph.PLAY, Ui.Kind.GHOST);
    private final JButton toggle = Ui.button("Deactivate", Glyph.POWER, Ui.Kind.GHOST);
    private final JButton delete = Ui.button("Delete", Glyph.TRASH, Ui.Kind.GHOST);
    private long reselect = -1;

    public SchedulesPage(AppContext ctx) {
        super(ctx);
        setBorder(Ui.pad(Theme.PAD));

        JButton add = Ui.button("New schedule", Glyph.PLUS, Ui.Kind.PRIMARY);
        add.addActionListener(e -> create());

        JPanel left = Ui.transparent(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 10, 0));
        left.add(search);
        left.add(viewFilter);
        left.add(count);
        JPanel bar = Ui.transparent(new BorderLayout());
        bar.setBorder(Ui.pad(0, 0, 10, 0));
        bar.add(left, BorderLayout.WEST);
        bar.add(add, BorderLayout.EAST);

        JPanel actions = Ui.transparent(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 4, 0));
        actions.add(edit);
        actions.add(duplicate);
        actions.add(playNow);
        actions.add(toggle);
        actions.add(delete);
        actions.setBorder(Ui.pad(0, 0, 10, 0));
        JPanel north = Ui.transparent(new BorderLayout());
        north.add(bar, BorderLayout.NORTH);
        north.add(actions, BorderLayout.SOUTH);

        table.setRowHeight(54);
        table.getColumnModel().getColumn(0).setCellRenderer(new Tables.IconRenderer());
        table.getColumnModel().getColumn(1).setCellRenderer(new Tables.TwoLineRenderer());
        table.getColumnModel().getColumn(4).setCellRenderer(new Tables.TextRenderer(false));
        for (int c : new int[] {2, 3, 5, 6}) {
            table.getColumnModel().getColumn(c).setCellRenderer(new Tables.TextRenderer(true));
        }
        Tables.widths(table, -48, 260, 90, 170, 190, 150, 160);

        center.setOpaque(false);
        center.add(Tables.card(table), "table");
        center.add(emptyState(), "empty");

        add(north, BorderLayout.NORTH);
        add(center, BorderLayout.CENTER);

        search.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e) { filterChanged(); }
            @Override public void removeUpdate(DocumentEvent e) { filterChanged(); }
            @Override public void changedUpdate(DocumentEvent e) { filterChanged(); }
        });
        viewFilter.addActionListener(e -> filterChanged());
        table.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                selectionChanged();
            }
        });
        Tables.onDoubleClick(table, row -> edit());
        Tables.selectOnRightClick(table);
        table.setComponentPopupMenu(contextMenu());
        table.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                int col = table.columnAtPoint(e.getPoint());
                int row = table.rowAtPoint(e.getPoint());
                if (col == 0 && row >= 0 && e.getButton() == MouseEvent.BUTTON1) {
                    toggleActive(model.at(row));
                }
            }
        });
        edit.addActionListener(e -> edit());
        duplicate.addActionListener(e -> duplicate());
        playNow.addActionListener(e -> playNow());
        toggle.addActionListener(e -> toggleActive(selected()));
        delete.addActionListener(e -> delete());
        selectionChanged();

        refreshOn(EventBus.Topic.SCHEDULES, EventBus.Topic.AUDIO);
    }

    @Override
    public String title() {
        return "Announcement Schedules";
    }

    @Override
    public String subtitle() {
        return "When announcements play and which audio they use";
    }

    private JPanel emptyState() {
        Card card = new Card(new BorderLayout());
        JPanel box = Ui.transparent(null);
        box.setLayout(new BoxLayout(box, BoxLayout.Y_AXIS));
        JLabel icon = new JLabel(Icons.of(Glyph.CALENDAR, 40, Theme.TEXT_FAINT));
        JLabel head = Ui.label("No announcement schedules yet", 16f, Theme.TEXT, Font.BOLD);
        JLabel sub = Ui.muted("Create a schedule, assign an audio file and FAM plays it on time, every time.");
        JButton create = Ui.button("Create your first schedule", Glyph.PLUS, Ui.Kind.PRIMARY);
        create.addActionListener(e -> create());
        for (JComponent c : new JComponent[] {icon, head, sub, create}) {
            c.setAlignmentX(0.5f);
        }
        box.add(Box.createVerticalGlue());
        box.add(icon);
        box.add(Ui.vgap(12));
        box.add(head);
        box.add(Ui.vgap(4));
        box.add(sub);
        box.add(Ui.vgap(18));
        box.add(create);
        box.add(Box.createVerticalGlue());
        card.add(box, BorderLayout.CENTER);
        return card;
    }

    @Override
    protected void refresh() {
        Schedule keep = selected();
        long keepId = keep == null ? -1 : keep.getId();
        Async.run(() -> {
            LocalDateTime now = LocalDateTime.now();
            List<Row> rows = new ArrayList<>();
            for (Schedule s : ctx.schedules.list()) {
                rows.add(new Row(s, s.isEnabled() ? s.nextOccurrence(now) : null));
            }
            return rows;
        }, rows -> {
            model.set(rows);
            long want = reselect >= 0 ? reselect : keepId;
            reselect = -1;
            int r = want >= 0 ? model.rowOf(want) : -1;
            if (r >= 0) {
                table.setRowSelectionInterval(r, r);
            }
            updateCount(rows.size());
            ((CardLayout) center.getLayout()).show(center, rows.isEmpty() ? "empty" : "table");
        }, e -> Toast.error(this, "Could not load schedules: " + e.getMessage()));
    }

    private void filterChanged() {
        model.filter();
        updateCount(model.all.size());
    }

    private void updateCount(int total) {
        int shown = model.getRowCount();
        count.setText(shown == total ? total + (total == 1 ? " schedule" : " schedules") : shown + " of " + total);
    }

    private Schedule selected() {
        return model.at(table.getSelectedRow());
    }

    private void selectionChanged() {
        Schedule s = selected();
        boolean has = s != null;
        edit.setEnabled(has);
        duplicate.setEnabled(has);
        playNow.setEnabled(has);
        toggle.setEnabled(has);
        delete.setEnabled(has);
        if (has) {
            toggle.setText(s.isEnabled() ? "Deactivate" : "Activate");
        }
    }

    // ---- actions --------------------------------------------------------------------------------

    private void create() {
        if (ScheduleDialog.open(SwingUtilities.getWindowAncestor(this), ctx, null)) {
            Toast.info(this, "Schedule created");
        }
    }

    private void edit() {
        Schedule s = selected();
        if (s != null && ScheduleDialog.open(SwingUtilities.getWindowAncestor(this), ctx, s)) {
            reselect = s.getId();
            Toast.info(this, "Schedule saved");
        }
    }

    private void duplicate() {
        Schedule s = selected();
        if (s == null) {
            return;
        }
        Schedule copy = s.copy();
        copy.setId(0);
        copy.setName(s.getName() + " (copy)");
        copy.setCreatedAt(LocalDateTime.now());
        copy.setUpdatedAt(LocalDateTime.now());
        Async.run(() -> ctx.schedules.save(copy), id -> {
            reselect = id;
            Toast.info(this, "Duplicated “" + s.getName() + "”");
        }, e -> Dialogs.error(this, "Cannot duplicate", String.valueOf(e.getMessage())));
    }

    private void toggleActive(Schedule s) {
        if (s == null) {
            return;
        }
        boolean enable = !s.isEnabled();
        Async.run(() -> ctx.schedules.setEnabled(s, enable),
                () -> Toast.info(this, "“" + s.getName() + "” is now " + (enable ? "active" : "inactive")),
                e -> Dialogs.error(this, "Cannot change schedule", String.valueOf(e.getMessage())));
    }

    private void playNow() {
        Schedule s = selected();
        if (s == null) {
            return;
        }
        String out = s.getDeviceName() != null ? s.getDeviceName() : ctx.devices.announcementOutputName();
        if (!Dialogs.confirm(this, "Play announcement now",
                "Play “" + s.getName() + "” now through\n" + out + "?", "Play now", false)) {
            return;
        }
        Async.run(() -> ctx.schedules.playNow(s), () -> Toast.info(this, "Playing “" + s.getName() + "”"),
                e -> Dialogs.error(this, "Cannot play", String.valueOf(e.getMessage())));
    }

    private void delete() {
        Schedule s = selected();
        if (s == null) {
            return;
        }
        if (Dialogs.confirm(this, "Delete schedule",
                "Delete “" + s.getName() + "”?\nIts audio file stays in the library and past runs stay in the history.",
                "Delete", true)) {
            Async.run(() -> ctx.schedules.delete(s.getId()), () -> Toast.info(this, "Schedule deleted"),
                    e -> Dialogs.error(this, "Cannot delete", String.valueOf(e.getMessage())));
        }
    }

    private JPopupMenu contextMenu() {
        JPopupMenu m = new JPopupMenu();
        JMenuItem e = new JMenuItem("Edit…");
        e.addActionListener(a -> edit());
        JMenuItem d = new JMenuItem("Duplicate");
        d.addActionListener(a -> duplicate());
        JMenuItem p = new JMenuItem("Play now…");
        p.addActionListener(a -> playNow());
        JMenuItem t = new JMenuItem("Activate / deactivate");
        t.addActionListener(a -> toggleActive(selected()));
        JMenuItem x = new JMenuItem("Delete…");
        x.addActionListener(a -> delete());
        m.add(e);
        m.add(d);
        m.add(p);
        m.add(t);
        m.addSeparator();
        m.add(x);
        return m;
    }
}
