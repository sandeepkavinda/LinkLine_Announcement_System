package com.linkline.fam.ui;

import com.linkline.fam.ui.components.Icons;
import com.linkline.fam.ui.components.Icons.Glyph;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.RoundRectangle2D;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JLayeredPane;
import javax.swing.JPanel;
import javax.swing.JRootPane;
import javax.swing.SwingUtilities;
import javax.swing.Timer;

/** Short confirmation messages that appear in the bottom right corner and fade away by themselves. */
public final class Toast {

    private static JComponent current;

    private Toast() {
    }

    public static void info(Component anchor, String message) {
        show(anchor, message, false);
    }

    public static void error(Component anchor, String message) {
        show(anchor, message, true);
    }

    public static void show(Component anchor, String message, boolean error) {
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingUtilities.invokeLater(() -> show(anchor, message, error));
            return;
        }
        JRootPane root = anchor == null ? null : SwingUtilities.getRootPane(anchor);
        if (root == null) {
            return;
        }
        JLayeredPane layer = root.getLayeredPane();
        if (current != null && current.getParent() != null) {
            current.getParent().remove(current);
        }
        Color accent = error ? Theme.DANGER : Theme.SUCCESS;
        JPanel box = new JPanel(new java.awt.BorderLayout(10, 0)) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(Theme.SURFACE_3);
                g2.fill(new RoundRectangle2D.Double(0, 0, getWidth(), getHeight(), 20, 20));
                g2.setColor(Theme.alpha(accent, 150));
                g2.draw(new RoundRectangle2D.Double(0.5, 0.5, getWidth() - 1, getHeight() - 1, 20, 20));
                g2.dispose();
                super.paintComponent(g);
            }
        };
        box.setOpaque(false);
        box.setBorder(Ui.pad(11, 14, 11, 16));
        JLabel icon = new JLabel(Icons.of(error ? Glyph.WARN : Glyph.CHECK, 18, accent));
        JLabel text = Ui.label("<html><body style='width:300px'>" + escape(message) + "</body></html>", 13f, Theme.TEXT,
                java.awt.Font.PLAIN);
        box.add(icon, java.awt.BorderLayout.WEST);
        box.add(text, java.awt.BorderLayout.CENTER);
        Dimension size = box.getPreferredSize();
        box.setSize(size);
        box.setLocation(layer.getWidth() - size.width - 24, layer.getHeight() - size.height - 24);
        layer.add(box, JLayeredPane.POPUP_LAYER);
        layer.repaint();
        current = box;

        Timer t = new Timer(error ? 6000 : 3500, e -> {
            if (box.getParent() != null) {
                box.getParent().remove(box);
                layer.repaint();
            }
            if (current == box) {
                current = null;
            }
        });
        t.setRepeats(false);
        t.start();
    }

    private static String escape(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\n", "<br>");
    }
}
