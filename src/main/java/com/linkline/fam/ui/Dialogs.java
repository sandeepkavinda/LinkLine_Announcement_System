package com.linkline.fam.ui;

import java.awt.Component;
import java.awt.FileDialog;
import java.awt.Frame;
import java.awt.Window;
import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;

/** Message boxes and the native Windows file dialogs. */
public final class Dialogs {

    private Dialogs() {
    }

    public static void error(Component parent, String title, String message) {
        JOptionPane.showMessageDialog(parent, wrap(message), title, JOptionPane.ERROR_MESSAGE);
    }

    public static void info(Component parent, String title, String message) {
        JOptionPane.showMessageDialog(parent, wrap(message), title, JOptionPane.INFORMATION_MESSAGE);
    }

    /** @return true if the user chose the confirm button */
    public static boolean confirm(Component parent, String title, String message, String confirmText, boolean danger) {
        Object[] options = {confirmText, "Cancel"};
        int r = JOptionPane.showOptionDialog(parent, wrap(message), title, JOptionPane.DEFAULT_OPTION,
                danger ? JOptionPane.WARNING_MESSAGE : JOptionPane.QUESTION_MESSAGE, null, options, options[1]);
        return r == 0;
    }

    public static String prompt(Component parent, String title, String message, String initial) {
        Object r = JOptionPane.showInputDialog(parent, message, title, JOptionPane.PLAIN_MESSAGE, null, null, initial);
        return r == null ? null : r.toString();
    }

    private static String wrap(String message) {
        return message;
    }

    // ---- native file dialogs (java.awt.FileDialog uses the real Windows dialog) -------------------

    private static Frame frameOf(Component c) {
        Window w = c == null ? null : SwingUtilities.getWindowAncestor(c);
        while (w != null && !(w instanceof Frame)) {
            w = w.getOwner();
        }
        return (Frame) w;
    }

    /** Asks for one or more audio files. Returns an empty list when cancelled. */
    public static List<Path> chooseAudioFiles(Component parent) {
        FileDialog fd = new FileDialog(frameOf(parent), "Import audio files", FileDialog.LOAD);
        fd.setMultipleMode(true);
        fd.setFile("*.mp3;*.wav;*.aif;*.aiff;*.au");
        fd.setVisible(true);
        List<Path> result = new ArrayList<>();
        File[] files = fd.getFiles();
        if (files != null) {
            for (File f : files) {
                result.add(f.toPath());
            }
        }
        return result;
    }

    /** Asks where to save a file. Returns null when cancelled. */
    public static Path chooseSaveFile(Component parent, String title, String suggestedName) {
        FileDialog fd = new FileDialog(frameOf(parent), title, FileDialog.SAVE);
        fd.setFile(suggestedName);
        fd.setVisible(true);
        if (fd.getFile() == null || fd.getDirectory() == null) {
            return null;
        }
        return new File(fd.getDirectory(), fd.getFile()).toPath();
    }
}
