package com.linkline.fam.ui.dialogs;

import com.linkline.fam.AppContext;
import com.linkline.fam.model.AudioItem;
import com.linkline.fam.ui.Async;
import com.linkline.fam.ui.AudioActions;
import com.linkline.fam.ui.Theme;
import com.linkline.fam.ui.Toast;
import com.linkline.fam.ui.Ui;
import com.linkline.fam.ui.components.AudioTableModel;
import com.linkline.fam.ui.components.Icons.Glyph;
import com.linkline.fam.ui.components.PlayerBar;
import com.linkline.fam.ui.components.Tables;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Window;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JPanel;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

/** Choose an audio file from the library, preview it, or import a new one. */
public final class AudioPickerDialog extends JDialog {

    private final AppContext ctx;
    private final AudioTableModel model = new AudioTableModel();
    private final JTable table = Tables.create(model);
    private final JTextField search = Ui.search("Search audio files");
    private final JComboBox<AudioTableModel.Filter> filter = Ui.combo(AudioTableModel.Filter.values());
    private final PlayerBar player;
    private final JButton choose = Ui.button("Use this audio", Glyph.CHECK, Ui.Kind.PRIMARY);
    private AudioItem result;
    private long wanted;

    private AudioPickerDialog(Window owner, AppContext ctx, Long currentId) {
        super(owner, "Choose audio", ModalityType.APPLICATION_MODAL);
        this.ctx = ctx;
        this.wanted = currentId == null ? -1 : currentId;
        this.player = new PlayerBar(ctx);

        JPanel root = new JPanel(new BorderLayout(0, 12));
        root.setBorder(Ui.pad(18, 20, 16, 20));

        JButton importBtn = Ui.button("Import new…", Glyph.IMPORT, Ui.Kind.SECONDARY);
        importBtn.addActionListener(e -> AudioActions.importFiles(this, ctx, items -> {
            wanted = items.get(0).id();
            reload();
        }));
        JPanel top = Ui.transparent(new BorderLayout());
        JPanel left = Ui.transparent(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 10, 0));
        left.add(search);
        left.add(filter);
        top.add(left, BorderLayout.WEST);
        top.add(importBtn, BorderLayout.EAST);

        table.setDefaultRenderer(Object.class, new Tables.TextRenderer(true));
        table.getColumnModel().getColumn(0).setCellRenderer(new Tables.TextRenderer(false));
        table.getColumnModel().getColumn(5).setCellRenderer(new Tables.TextRenderer(false));
        Tables.widths(table, 240, 60, 70, 70, 140, 220);

        JButton cancel = Ui.button("Cancel", null, Ui.Kind.SECONDARY);
        cancel.addActionListener(e -> close());
        choose.addActionListener(e -> accept());
        JPanel buttons = Ui.transparent(new java.awt.FlowLayout(java.awt.FlowLayout.RIGHT, 8, 0));
        buttons.add(cancel);
        buttons.add(choose);

        JPanel south = Ui.transparent(new BorderLayout(0, 12));
        south.add(player, BorderLayout.CENTER);
        south.add(buttons, BorderLayout.SOUTH);

        root.add(top, BorderLayout.NORTH);
        root.add(Tables.card(table), BorderLayout.CENTER);
        root.add(south, BorderLayout.SOUTH);
        setContentPane(root);

        search.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e) { applyFilter(); }
            @Override public void removeUpdate(DocumentEvent e) { applyFilter(); }
            @Override public void changedUpdate(DocumentEvent e) { applyFilter(); }
        });
        filter.addActionListener(e -> applyFilter());
        table.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                AudioItem a = model.itemAt(table.getSelectedRow());
                player.setItem(a);
                choose.setEnabled(a != null);
            }
        });
        Tables.onDoubleClick(table, row -> accept());
        choose.setEnabled(false);

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                player.release();
            }
        });
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setMinimumSize(new Dimension(820, 560));
        setSize(900, 600);
        setLocationRelativeTo(owner);
        reload();
    }

    /** Shows the picker and returns the chosen audio, or null if the user cancelled. */
    public static AudioItem pick(Window owner, AppContext ctx, Long currentAudioId) {
        AudioPickerDialog d = new AudioPickerDialog(owner, ctx, currentAudioId);
        d.setVisible(true);
        return d.result;
    }

    private void applyFilter() {
        model.setFilter(search.getText(), (AudioTableModel.Filter) filter.getSelectedItem());
    }

    private void reload() {
        Async.run(() -> AudioTableModel.load(ctx), data -> {
            model.setData(data);
            int row = wanted >= 0 ? model.rowOf(wanted) : -1;
            if (row >= 0) {
                table.setRowSelectionInterval(row, row);
                table.scrollRectToVisible(table.getCellRect(row, 0, true));
            }
        }, e -> Toast.error(this, "Could not load the audio library: " + e.getMessage()));
    }

    private void accept() {
        AudioItem a = model.itemAt(table.getSelectedRow());
        if (a == null) {
            return;
        }
        result = a;
        close();
    }

    private void close() {
        player.release();
        dispose();
    }
}
