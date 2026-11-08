package com.linkline.fam.ui;

import com.linkline.fam.ui.components.Icons;
import com.linkline.fam.ui.components.Icons.Glyph;
import com.linkline.fam.util.Fmt;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;
import java.time.LocalDate;
import java.time.LocalDateTime;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.border.Border;

/** Small factory methods so every screen builds the same looking controls. */
public final class Ui {

    public enum Kind { PRIMARY, SECONDARY, DANGER, GHOST }

    private Ui() {
    }

    // ---- text -----------------------------------------------------------------------------

    public static JLabel label(String text, float size, Color color, int style) {
        JLabel l = new JLabel(text);
        l.setFont(Theme.font(size, style));
        l.setForeground(color);
        return l;
    }

    public static JLabel text(String text) {
        return label(text, 13f, Theme.TEXT, Font.PLAIN);
    }

    public static JLabel muted(String text) {
        return label(text, 12.5f, Theme.TEXT_MUTED, Font.PLAIN);
    }

    public static JLabel heading(String text) {
        return label(text, 14f, Theme.TEXT, Font.BOLD);
    }

    /** Small uppercase caption used above values and form sections. */
    public static JLabel caption(String text) {
        JLabel l = label(text.toUpperCase(), 11f, Theme.TEXT_FAINT, Font.BOLD);
        return l;
    }

    // ---- controls -------------------------------------------------------------------------

    public static JButton button(String text, Glyph glyph, Kind kind) {
        JButton b = new JButton(text);
        b.setFont(Theme.font(13f, Font.PLAIN));
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        b.setFocusable(false);
        Color iconColor = Theme.TEXT_MUTED;
        switch (kind) {
            case PRIMARY:
                b.setBackground(Theme.ACCENT);
                b.setForeground(Color.WHITE);
                iconColor = Color.WHITE;
                b.putClientProperty("FlatLaf.style",
                        "hoverBackground: " + Theme.hex(Theme.ACCENT_HOVER) + "; pressedBackground: #4A78E0; "
                                + "borderWidth: 0; focusWidth: 0; arc: 10; margin: 6,14,6,14");
                break;
            case DANGER:
                b.setForeground(Theme.DANGER);
                iconColor = Theme.DANGER;
                b.putClientProperty("FlatLaf.style", "arc: 10; margin: 6,14,6,14");
                break;
            case GHOST:
                b.putClientProperty("JButton.buttonType", "toolBarButton");
                b.putClientProperty("FlatLaf.style", "arc: 10; margin: 6,10,6,10");
                b.setForeground(Theme.TEXT);
                break;
            case SECONDARY:
            default:
                b.setForeground(Theme.TEXT);
                b.putClientProperty("FlatLaf.style", "arc: 10; margin: 6,14,6,14");
                break;
        }
        if (glyph != null) {
            b.setIcon(Icons.of(glyph, 16, iconColor));
            b.setIconTextGap(8);
        }
        return b;
    }

    public static JButton iconButton(Glyph glyph, String tooltip) {
        JButton b = new JButton(Icons.of(glyph, 18, Theme.TEXT_MUTED));
        b.setToolTipText(tooltip);
        b.setFocusable(false);
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        b.putClientProperty("JButton.buttonType", "toolBarButton");
        b.putClientProperty("FlatLaf.style", "arc: 8; margin: 6,6,6,6");
        return b;
    }

    public static JTextField search(String placeholder) {
        JTextField f = new JTextField(22);
        f.putClientProperty("JTextField.placeholderText", placeholder);
        f.putClientProperty("JTextField.leadingIcon", Icons.of(Glyph.SEARCH, 16, Theme.TEXT_FAINT));
        f.putClientProperty("JTextField.showClearButton", Boolean.TRUE);
        f.setFont(Theme.font(13f));
        return f;
    }

    public static <T> JComboBox<T> combo(T[] items) {
        JComboBox<T> c = new JComboBox<>(items);
        c.setFont(Theme.font(13f));
        c.setFocusable(false);
        return c;
    }

    public static JScrollPane scroll(Component view) {
        JScrollPane sp = new JScrollPane(view);
        sp.setBorder(BorderFactory.createEmptyBorder());
        sp.setOpaque(false);
        sp.getViewport().setOpaque(false);
        sp.getVerticalScrollBar().setUnitIncrement(18);
        return sp;
    }

    public static Border pad(int top, int left, int bottom, int right) {
        return BorderFactory.createEmptyBorder(top, left, bottom, right);
    }

    public static Border pad(int all) {
        return pad(all, all, all, all);
    }

    public static Component hgap(int w) {
        return Box.createHorizontalStrut(w);
    }

    public static Component vgap(int h) {
        return Box.createVerticalStrut(h);
    }

    public static JPanel transparent(java.awt.LayoutManager lm) {
        JPanel p = new JPanel(lm);
        p.setOpaque(false);
        return p;
    }

    public static void alignLeft(JComponent... comps) {
        for (JComponent c : comps) {
            c.setAlignmentX(Component.LEFT_ALIGNMENT);
        }
    }

    // ---- status indicators (icons instead of text badges) --------------------------------------

    /** A small filled dot, used for connection and health states. */
    public static Icon dot(Color color, int size) {
        return new Icon() {
            @Override
            public void paintIcon(Component c, Graphics g, int x, int y) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                double d = size * 0.55;
                g2.setColor(Theme.alpha(color, 60));
                g2.fill(new Ellipse2D.Double(x, y, size, size));
                g2.setColor(color);
                g2.fill(new Ellipse2D.Double(x + (size - d) / 2, y + (size - d) / 2, d, d));
                g2.dispose();
            }

            @Override
            public int getIconWidth() {
                return size;
            }

            @Override
            public int getIconHeight() {
                return size;
            }
        };
    }

    /** A round marker with a glyph inside, used on the dashboard timeline. */
    public static Icon marker(Glyph glyph, Color color, int size) {
        return new Icon() {
            @Override
            public void paintIcon(Component c, Graphics g, int x, int y) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(Theme.SURFACE);
                g2.fill(new Ellipse2D.Double(x, y, size, size));
                g2.setColor(Theme.alpha(color, 36));
                g2.fill(new Ellipse2D.Double(x, y, size, size));
                g2.setColor(Theme.alpha(color, 150));
                g2.setStroke(new java.awt.BasicStroke(1.2f));
                g2.draw(new Ellipse2D.Double(x + 0.6, y + 0.6, size - 1.2, size - 1.2));
                double inner = size * 0.56;
                Icons.paint(g2, glyph, x + (size - inner) / 2, y + (size - inner) / 2, inner, color, 2.2f);
                g2.dispose();
            }

            @Override
            public int getIconWidth() {
                return size;
            }

            @Override
            public int getIconHeight() {
                return size;
            }
        };
    }

    public static Dimension fixed(int w, int h) {
        return new Dimension(w, h);
    }

    // ---- display formatting -----------------------------------------------------------------

    /** "Today, 03:30 PM", "Tomorrow, 09:00 AM" or a full date. */
    public static String relativeDateTime(LocalDateTime dt) {
        if (dt == null) {
            return "—";
        }
        LocalDate today = LocalDate.now();
        LocalDate d = dt.toLocalDate();
        String day;
        if (d.equals(today)) {
            day = "Today";
        } else if (d.equals(today.plusDays(1))) {
            day = "Tomorrow";
        } else if (d.equals(today.minusDays(1))) {
            day = "Yesterday";
        } else {
            day = Fmt.date(d);
        }
        return day + ", " + Fmt.time(dt.toLocalTime());
    }
}
