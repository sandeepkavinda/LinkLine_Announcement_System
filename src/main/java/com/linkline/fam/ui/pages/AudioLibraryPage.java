package com.linkline.fam.ui.pages;

import com.linkline.fam.AppContext;
import com.linkline.fam.model.AudioItem;
import com.linkline.fam.service.EventBus;
import com.linkline.fam.ui.Async;
import com.linkline.fam.ui.AudioActions;
import com.linkline.fam.ui.Theme;
import com.linkline.fam.ui.Toast;
import com.linkline.fam.ui.Ui;
import com.linkline.fam.ui.components.AudioTableModel;
import com.linkline.fam.ui.components.Icons.Glyph;
import com.linkline.fam.ui.components.Page;
import com.linkline.fam.ui.components.PlayerBar;
import com.linkline.fam.ui.components.Tables;
import java.awt.BorderLayout;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.TransferHandler;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

/** Import, preview, rename and delete audio files. */
public final class AudioLibraryPage extends Page {

    private final AudioTableModel model = new AudioTableModel();
    private final JTable table = Tables.create(model);
    private final JTextField search = Ui.search("Search audio files");
    private final JComboBox<AudioTableModel.Filter> filter = Ui.combo(AudioTableModel.Filter.values());
    private final JLabel count = Ui.muted("");
    private final PlayerBar player;
    private final JButton rename = Ui.button("Rename", Glyph.EDIT, Ui.Kind.GHOST);
    private final JButton delete = Ui.button("Delete", Glyph.TRASH, Ui.Kind.GHOST);
    private long reselect = -1;

    public AudioLibraryPage(AppContext ctx) {
        super(ctx);
        player = new PlayerBar(ctx);
        setBorder(Ui.pad(Theme.PAD));

        JButton importBtn = Ui.button("Import audio", Glyph.IMPORT, Ui.Kind.PRIMARY);
        importBtn.addActionListener(e -> doImport());

        JPanel left = Ui.transparent(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 10, 0));
        left.add(search);
        left.add(filter);
        left.add(count);
        JPanel right = Ui.transparent(new java.awt.FlowLayout(java.awt.FlowLayout.RIGHT, 6, 0));
        right.add(rename);
        right.add(delete);
        right.add(importBtn);
        JPanel bar = Ui.transparent(new BorderLayout());
        bar.add(left, BorderLayout.WEST);
        bar.add(right, BorderLayout.EAST);
        bar.setBorder(Ui.pad(0, 0, 14, 0));

        table.setDefaultRenderer(Object.class, new Tables.TextRenderer(true));
        table.getColumnModel().getColumn(0).setCellRenderer(new Tables.TextRenderer(false));
        table.getColumnModel().getColumn(5).setCellRenderer(new Tables.TextRenderer(false));
        Tables.widths(table, 260, 70, 80, 80, 150, 260);

        JPanel south = Ui.transparent(new BorderLayout());
        south.setBorder(Ui.pad(14, 0, 0, 0));
        south.add(player, BorderLayout.CENTER);

        add(bar, BorderLayout.NORTH);
        add(Tables.card(table), BorderLayout.CENTER);
        add(south, BorderLayout.SOUTH);

        search.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e) { applyFilter(); }
            @Override public void removeUpdate(DocumentEvent e) { applyFilter(); }
            @Override public void changedUpdate(DocumentEvent e) { applyFilter(); }
        });
        filter.addActionListener(e -> applyFilter());
        table.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                selectionChanged();
            }
        });
        rename.addActionListener(e -> {
            AudioItem a = selected();
            if (a != null) {
                AudioActions.rename(this, ctx, a);
            }
        });
        delete.addActionListener(e -> {
            AudioItem a = selected();
            if (a != null) {
                AudioActions.delete(this, ctx, a);
            }
        });
        Tables.onDoubleClick(table, row -> player.togglePlay());
        Tables.selectOnRightClick(table);
        table.setComponentPopupMenu(contextMenu());
        installDropTarget();
        selectionChanged();

        refreshOn(EventBus.Topic.AUDIO, EventBus.Topic.SCHEDULES);
    }

    @Override
    public String title() {
        return "Audio Library";
    }

    @Override
    public String subtitle() {
        return "Announcement audio stored inside FAM";
    }

    @Override
    protected void refresh() {
        AudioItem keep = selected();
        long keepId = keep == null ? -1 : keep.id();
        Async.run(() -> AudioTableModel.load(ctx), data -> {
            model.setData(data);
            long want = reselect >= 0 ? reselect : keepId;
            reselect = -1;
            int row = want >= 0 ? model.rowOf(want) : -1;
            if (row >= 0) {
                table.setRowSelectionInterval(row, row);
            }
            updateCount();
        }, e -> Toast.error(this, "Could not load the audio library: " + e.getMessage()));
    }

    @Override
    protected void visibilityChanged(boolean visible) {
        if (!visible) {
            ctx.preview.stop();
        }
    }

    @Override
    public void dispose() {
        player.release();
        super.dispose();
    }

    private AudioItem selected() {
        return model.itemAt(table.getSelectedRow());
    }

    private void applyFilter() {
        model.setFilter(search.getText(), (AudioTableModel.Filter) filter.getSelectedItem());
        updateCount();
    }

    private void updateCount() {
        int shown = model.getRowCount();
        int total = model.totalCount();
        count.setText(shown == total ? total + (total == 1 ? " file" : " files") : shown + " of " + total);
    }

    private void selectionChanged() {
        AudioItem a = selected();
        player.setItem(a);
        rename.setEnabled(a != null);
        delete.setEnabled(a != null);
    }

    private void doImport() {
        AudioActions.importFiles(this, ctx, items -> {
            reselect = items.get(0).id();
            refresh();
        });
    }

    private JPopupMenu contextMenu() {
        JPopupMenu m = new JPopupMenu();
        JMenuItem play = new JMenuItem("Play / pause preview");
        play.addActionListener(e -> player.togglePlay());
        JMenuItem ren = new JMenuItem("Rename…");
        ren.addActionListener(e -> rename.doClick());
        JMenuItem del = new JMenuItem("Delete…");
        del.addActionListener(e -> delete.doClick());
        m.add(play);
        m.add(ren);
        m.addSeparator();
        m.add(del);
        return m;
    }

    /** Dropping audio files on the page imports them. */
    private void installDropTarget() {
        TransferHandler handler = new TransferHandler() {
            @Override
            public boolean canImport(TransferSupport s) {
                return s.isDataFlavorSupported(DataFlavor.javaFileListFlavor);
            }

            @Override
            @SuppressWarnings("unchecked")
            public boolean importData(TransferSupport s) {
                if (!canImport(s)) {
                    return false;
                }
                try {
                    Transferable t = s.getTransferable();
                    List<File> files = (List<File>) t.getTransferData(DataFlavor.javaFileListFlavor);
                    List<Path> paths = new ArrayList<>();
                    for (File f : files) {
                        paths.add(f.toPath());
                    }
                    Async.run(() -> ctx.library.importFiles(paths), r -> {
                        if (!r.errors().isEmpty()) {
                            com.linkline.fam.ui.Dialogs.error(AudioLibraryPage.this, "Some files could not be imported",
                                    String.join("\n\n", r.errors()));
                        }
                        if (!r.imported().isEmpty()) {
                            reselect = r.imported().get(0).id();
                            Toast.info(AudioLibraryPage.this, "Imported " + r.imported().size() + " audio file(s)");
                            refresh();
                        }
                    }, e -> Toast.error(AudioLibraryPage.this, "Import failed: " + e.getMessage()));
                    return true;
                } catch (Exception e) {
                    return false;
                }
            }
        };
        setTransferHandler(handler);
        table.setTransferHandler(handler);
        table.setDropMode(javax.swing.DropMode.ON);
    }
}
