package com.linkline.fam.ui.components;

import com.linkline.fam.ui.Theme;
import com.linkline.fam.ui.components.Icons.Glyph;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.RoundRectangle2D;
import javax.swing.JComponent;
import javax.swing.Timer;

/** A sidebar entry: icon and label, a soft hover fade and a clear selected state. */
public final class NavItem extends JComponent {

    private final Glyph glyph;
    private final String label;
    private boolean selected;
    private float hover; // 0..1, animated
    private boolean hovering;
    private final Timer fade = new Timer(16, e -> step());

    public NavItem(Glyph glyph, String label, Runnable onClick) {
        this.glyph = glyph;
        this.label = label;
        setOpaque(false);
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        setPreferredSize(new Dimension(200, 40));
        setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
        setFocusable(true);
        addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                hovering = true;
                fade.start();
            }

            @Override
            public void mouseExited(MouseEvent e) {
                hovering = false;
                fade.start();
            }

            @Override
            public void mouseClicked(MouseEvent e) {
                onClick.run();
            }
        });
    }

    public void setSelected(boolean selected) {
        this.selected = selected;
        repaint();
    }

    private void step() {
        float target = hovering ? 1f : 0f;
        hover += (target - hover) * 0.35f;
        if (Math.abs(target - hover) < 0.02f) {
            hover = target;
            fade.stop();
        }
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        int w = getWidth();
        int h = getHeight();
        if (selected) {
            g2.setColor(Theme.SURFACE_3);
            g2.fill(new RoundRectangle2D.Double(0, 0, w, h, 12, 12));
            g2.setColor(Theme.ACCENT);
            g2.fill(new RoundRectangle2D.Double(0, 10, 3, h - 20, 3, 3));
        } else if (hover > 0f) {
            g2.setColor(Theme.alpha(Theme.SURFACE_3, Math.round(150 * hover)));
            g2.fill(new RoundRectangle2D.Double(0, 0, w, h, 12, 12));
        }
        Color fg = selected ? Theme.TEXT : blend(Theme.TEXT_MUTED, Theme.TEXT, hover);
        Icons.paint(g2, glyph, 16, (h - 20) / 2.0, 20, selected ? Theme.ACCENT : fg, 1.8f);
        // Same weight whether selected or not, so the text never grows and gets cut off.
        g2.setFont(Theme.font(13.5f, java.awt.Font.PLAIN));
        g2.setColor(fg);
        FontMetrics fm = g2.getFontMetrics();
        int textX = 46;
        int room = w - textX - 12;
        String shown = label;
        if (fm.stringWidth(shown) > room) {
            while (shown.length() > 1 && fm.stringWidth(shown + "…") > room) {
                shown = shown.substring(0, shown.length() - 1);
            }
            shown = shown.stripTrailing() + "…";
            setToolTipText(label);
        }
        g2.drawString(shown, textX, (h - fm.getHeight()) / 2 + fm.getAscent());
        g2.dispose();
    }

    private static Color blend(Color a, Color b, float t) {
        return new Color(
                Math.round(a.getRed() + (b.getRed() - a.getRed()) * t),
                Math.round(a.getGreen() + (b.getGreen() - a.getGreen()) * t),
                Math.round(a.getBlue() + (b.getBlue() - a.getBlue()) * t));
    }
}