package com.linkline.fam.ui;

import com.formdev.flatlaf.FlatDarkLaf;
import com.formdev.flatlaf.FlatLaf;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GraphicsEnvironment;
import java.awt.Insets;
import java.util.HashMap;
import java.util.Map;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.UIManager;

/** FAM's design tokens (colors, type) and the FlatLaf dark theme setup. */
public final class Theme {

    // Surfaces, darkest to lightest
    public static final Color BG = new Color(0x0D0F13);
    public static final Color SIDEBAR = new Color(0x111419);
    public static final Color SURFACE = new Color(0x161A20);
    public static final Color SURFACE_2 = new Color(0x1C2128);
    public static final Color SURFACE_3 = new Color(0x232932);
    public static final Color BORDER = new Color(0x262C36);
    public static final Color BORDER_SOFT = new Color(0x1E232B);

    // Text
    public static final Color TEXT = new Color(0xE8EBF1);
    public static final Color TEXT_MUTED = new Color(0x8E96A4);
    public static final Color TEXT_FAINT = new Color(0x5E6674);

    // Accent and status colors (used sparingly)
    public static final Color ACCENT = new Color(0x5B8CFF);
    public static final Color ACCENT_HOVER = new Color(0x7AA2FF);
    public static final Color SUCCESS = new Color(0x3DBE8B);
    public static final Color WARNING = new Color(0xE2A93B);
    public static final Color DANGER = new Color(0xE5575B);

    public static final int RADIUS = 12;
    public static final int PAD = 24;
    public static final int GAP = 16;

    private static String family;

    private Theme() {
    }

    public static Color alpha(Color c, int alpha) {
        return new Color(c.getRed(), c.getGreen(), c.getBlue(), alpha);
    }

    public static String hex(Color c) {
        return String.format("#%02X%02X%02X", c.getRed(), c.getGreen(), c.getBlue());
    }

    public static synchronized String family() {
        if (family == null) {
            String[] preferred = {"Segoe UI Variable Text", "Segoe UI", "Inter", "SF Pro Text", "Helvetica Neue", "Roboto", "Noto Sans"};
            String found = Font.SANS_SERIF;
            java.util.Set<String> available = new java.util.HashSet<>(
                    java.util.Arrays.asList(GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames()));
            for (String p : preferred) {
                if (available.contains(p)) {
                    found = p;
                    break;
                }
            }
            family = found;
        }
        return family;
    }

    public static Font font(float size) {
        return font(size, Font.PLAIN);
    }

    public static Font font(float size, int style) {
        return new Font(family(), style, Math.round(size)).deriveFont(style, size);
    }

    public static Font mono(float size, int style) {
        return new Font(Font.MONOSPACED, style, Math.round(size)).deriveFont(style, size);
    }

    /** Installs FlatLaf with FAM's dark palette. Call once, before creating any window. */
    public static void install() {
        Map<String, String> extra = new HashMap<>();
        extra.put("@accentColor", hex(ACCENT));
        extra.put("@background", hex(BG));
        extra.put("@foreground", hex(TEXT));
        extra.put("@selectionBackground", "#25396B");
        extra.put("@selectionForeground", hex(TEXT));
        FlatLaf.setGlobalExtraDefaults(extra);
        FlatDarkLaf.setup();

        UIManager.put("defaultFont", font(13));
        UIManager.put("Component.arc", 10);
        UIManager.put("Button.arc", 10);
        UIManager.put("TextComponent.arc", 10);
        UIManager.put("Component.focusWidth", 1);
        UIManager.put("Component.focusColor", ACCENT);
        UIManager.put("Component.borderColor", BORDER);
        UIManager.put("Component.disabledBorderColor", BORDER_SOFT);

        UIManager.put("Panel.background", BG);
        UIManager.put("Button.background", SURFACE_2);
        UIManager.put("Button.hoverBackground", SURFACE_3);
        UIManager.put("TextField.background", SURFACE_2);
        UIManager.put("TextArea.background", SURFACE_2);
        UIManager.put("FormattedTextField.background", SURFACE_2);
        UIManager.put("PasswordField.background", SURFACE_2);
        UIManager.put("Spinner.background", SURFACE_2);
        UIManager.put("ComboBox.background", SURFACE_2);
        UIManager.put("ComboBox.buttonBackground", SURFACE_2);
        UIManager.put("Spinner.buttonBackground", SURFACE_2);
        UIManager.put("PopupMenu.background", SURFACE_2);
        UIManager.put("PopupMenu.borderColor", BORDER);
        UIManager.put("List.background", SURFACE_2);
        UIManager.put("ToolTip.background", SURFACE_3);
        UIManager.put("ToolTip.foreground", TEXT);
        UIManager.put("OptionPane.background", SURFACE);
        UIManager.put("Dialog.background", SURFACE);

        UIManager.put("ScrollBar.width", 10);
        UIManager.put("ScrollBar.thumbArc", 999);
        UIManager.put("ScrollBar.thumbInsets", new Insets(2, 2, 2, 2));
        UIManager.put("ScrollBar.track", BG);
        UIManager.put("ScrollBar.thumb", SURFACE_3);
        UIManager.put("ScrollBar.hoverThumbColor", new Color(0x323A46));
        UIManager.put("ScrollBar.pressedThumbColor", new Color(0x3C4655));
        UIManager.put("ScrollPane.border", javax.swing.BorderFactory.createEmptyBorder());

        UIManager.put("Table.background", SURFACE);
        UIManager.put("Table.foreground", TEXT);
        UIManager.put("Table.rowHeight", 46);
        UIManager.put("Table.showHorizontalLines", true);
        UIManager.put("Table.showVerticalLines", false);
        UIManager.put("Table.intercellSpacing", new Dimension(0, 1));
        UIManager.put("Table.gridColor", BORDER_SOFT);
        UIManager.put("Table.selectionBackground", new Color(0x22304F));
        UIManager.put("Table.selectionForeground", TEXT);
        UIManager.put("Table.selectionInactiveBackground", new Color(0x1E2A44));
        UIManager.put("Table.selectionInactiveForeground", TEXT);
        UIManager.put("TableHeader.background", SURFACE);
        UIManager.put("TableHeader.foreground", TEXT_MUTED);
        UIManager.put("TableHeader.bottomSeparatorColor", BORDER);
        UIManager.put("TableHeader.separatorColor", SURFACE);
        UIManager.put("TableHeader.height", 38);

        UIManager.put("TitlePane.background", BG);
        UIManager.put("TitlePane.inactiveBackground", BG);
        UIManager.put("TitlePane.foreground", TEXT_MUTED);
        UIManager.put("TitlePane.embeddedForeground", TEXT_MUTED);

        JFrame.setDefaultLookAndFeelDecorated(true);
        JDialog.setDefaultLookAndFeelDecorated(true);
    }
}
