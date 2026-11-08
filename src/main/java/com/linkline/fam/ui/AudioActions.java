package com.linkline.fam.ui;

import com.linkline.fam.AppContext;
import com.linkline.fam.model.AudioItem;
import com.linkline.fam.model.Schedule;
import com.linkline.fam.service.AudioLibraryService;
import com.linkline.fam.ui.components.Card;
import com.linkline.fam.ui.components.Icons;
import com.linkline.fam.ui.components.Icons.Glyph;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Window;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;

/** Audio operations that need dialogs: import, rename and the safe delete flow. */
public final class AudioActions {

    private AudioActions() {
    }

    /** Opens the native file dialog and imports the chosen files. {@code onImported} gets the new items. */
    public static void importFiles(Component parent, AppContext ctx, Consumer<List<AudioItem>> onImported) {
        List<Path> files = Dialogs.chooseAudioFiles(parent);
        if (files.isEmpty()) {
            return;
        }
        Toast.info(parent, files.size() == 1 ? "Importing " + files.get(0).getFileName() + "…"
                : "Importing " + files.size() + " files…");
        Async.run(() -> ctx.library.importFiles(files), result -> {
            if (!result.errors().isEmpty()) {
                Dialogs.error(parent, "Some files could not be imported",
                        String.join("\n\n", result.errors()));
            }
            if (!result.imported().isEmpty()) {
                int n = result.imported().size();
                Toast.info(parent, n == 1 ? "Imported “" + result.imported().get(0).displayName() + "”"
                        : "Imported " + n + " audio files");
                onImported.accept(result.imported());
            }
        }, e -> Dialogs.error(parent, "Import failed", String.valueOf(e.getMessage())));
    }

    public static void rename(Component parent, AppContext ctx, AudioItem item) {
        String name = Dialogs.prompt(parent, "Rename audio", "Display name:", item.displayName());
        if (name == null) {
            return;
        }
        Async.run(() -> ctx.library.rename(item.id(), name), () -> Toast.info(parent, "Renamed"),
                e -> Dialogs.error(parent, "Cannot rename", String.valueOf(e.getMessage())));
    }

    /**
     * Deletes an audio file. If schedules still use it the user must first choose a replacement
     * file or accept that those schedules are switched off.
     */
    public static void delete(Component parent, AppContext ctx, AudioItem item) {
        Async.run(() -> ctx.scheduleRepo.findByAudio(item.id()), using -> {
            if (using.isEmpty()) {
                if (Dialogs.confirm(parent, "Delete audio file",
                        "Delete “" + item.displayName() + "”?\nThe file will be removed from FAM. This cannot be undone.",
                        "Delete", true)) {
                    runDelete(parent, ctx, item, null, false);
                }
            } else {
                new InUseDialog(SwingUtilities.getWindowAncestor(parent), ctx, item, using).setVisible(true);
            }
        }, e -> Dialogs.error(parent, "Cannot delete", String.valueOf(e.getMessage())));
    }

    private static void runDelete(Component parent, AppContext ctx, AudioItem item, Long replacement, boolean reassign) {
        Async.run(() -> {
            try {
                if (reassign) {
                    ctx.library.reassignAndDelete(item, replacement);
                } else {
                    ctx.library.delete(item);
                }
            } catch (AudioLibraryService.AudioInUseException e) {
                throw new IllegalStateException(e.getMessage(), e);
            }
        }, () -> Toast.info(parent, "Deleted “" + item.displayName() + "”"),
                e -> Dialogs.error(parent, "Cannot delete", String.valueOf(e.getMessage())));
    }

    /** Lists the schedules that use the file and asks what to do with them before it can be deleted. */
    private static final class InUseDialog extends JDialog {

        private static final String NONE = "No audio (switch these schedules off)";

        InUseDialog(Window owner, AppContext ctx, AudioItem item, List<Schedule> using) {
            super(owner, "This audio file is in use", ModalityType.APPLICATION_MODAL);
            JPanel body = new JPanel();
            body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
            body.setBorder(Ui.pad(22, 24, 18, 24));

            JLabel head = Ui.label("“" + item.displayName() + "” is assigned to " + using.size()
                    + (using.size() == 1 ? " schedule" : " schedules"), 15f, Theme.TEXT, Font.BOLD);
            JLabel sub = Ui.muted("Choose what these schedules should use instead before the file is deleted.");
            Ui.alignLeft(head, sub);
            body.add(head);
            body.add(Ui.vgap(4));
            body.add(sub);
            body.add(Ui.vgap(14));

            Card list = new Card(new BorderLayout());
            list.setBorder(Ui.pad(10, 14, 10, 14));
            JPanel rows = Ui.transparent(new java.awt.GridLayout(0, 1, 0, 6));
            for (Schedule s : using) {
                JLabel l = Ui.label(s.getName() + "   " + com.linkline.fam.util.Fmt.time(s.getTime()), 13f, Theme.TEXT,
                        Font.PLAIN);
                l.setIcon(Icons.of(Glyph.CALENDAR, 16, Theme.TEXT_MUTED));
                l.setIconTextGap(8);
                rows.add(l);
            }
            list.add(rows, BorderLayout.CENTER);
            list.setAlignmentX(Component.LEFT_ALIGNMENT);
            body.add(list);
            body.add(Ui.vgap(16));

            JLabel use = Ui.caption("Use this audio instead");
            use.setAlignmentX(Component.LEFT_ALIGNMENT);
            body.add(use);
            body.add(Ui.vgap(6));
            List<Object> choices = new ArrayList<>();
            for (AudioItem a : ctx.library.list()) {
                if (a.id() != item.id()) {
                    choices.add(a);
                }
            }
            choices.add(NONE);
            JComboBox<Object> combo = new JComboBox<>(choices.toArray());
            combo.setAlignmentX(Component.LEFT_ALIGNMENT);
            combo.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));
            body.add(combo);

            JButton cancel = Ui.button("Cancel", null, Ui.Kind.SECONDARY);
            JButton ok = Ui.button("Reassign and delete", Glyph.TRASH, Ui.Kind.DANGER);
            cancel.addActionListener(e -> dispose());
            ok.addActionListener(e -> {
                Object sel = combo.getSelectedItem();
                Long replacement = sel instanceof AudioItem a ? a.id() : null;
                String what = replacement == null
                        ? "These schedules will have no audio and will be switched off."
                        : "These schedules will use “" + sel + "”.";
                if (Dialogs.confirm(this, "Delete audio file", what + "\n\nDelete “" + item.displayName() + "” now?",
                        "Delete", true)) {
                    dispose();
                    runDelete(owner, ctx, item, replacement, true);
                }
            });
            JPanel buttons = Ui.transparent(new java.awt.FlowLayout(java.awt.FlowLayout.RIGHT, 8, 0));
            buttons.add(cancel);
            buttons.add(ok);
            buttons.setAlignmentX(Component.LEFT_ALIGNMENT);
            body.add(Ui.vgap(20));
            body.add(buttons);

            setContentPane(body);
            setMinimumSize(new Dimension(480, 0));
            pack();
            setLocationRelativeTo(owner);
        }
    }
}
