package com.linkline.fam.ui.components;

import com.linkline.fam.ui.Theme;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.LayoutManager;
import java.awt.RenderingHints;
import java.awt.geom.RoundRectangle2D;
import javax.swing.JPanel;

/** A rounded panel with a subtle border and shadow: the basic building block of every screen. */
public class Card extends JPanel {

    public Card(LayoutManager layout) {
        super(layout);
        setOpaque(false);
        setBorder(javax.swing.BorderFactory.createEmptyBorder(18, 20, 18, 20));
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        int w = getWidth();
        int h = getHeight();
        // soft shadow, one pixel below the card
        g2.setColor(Theme.alpha(java.awt.Color.BLACK, 40));
        g2.fill(new RoundRectangle2D.Double(0, 1.5, w, h - 1.5, Theme.RADIUS * 2, Theme.RADIUS * 2));
        g2.setColor(Theme.SURFACE);
        g2.fill(new RoundRectangle2D.Double(0, 0, w, h - 1.5, Theme.RADIUS * 2, Theme.RADIUS * 2));
        g2.setColor(Theme.BORDER);
        g2.draw(new RoundRectangle2D.Double(0.5, 0.5, w - 1, h - 2.5, Theme.RADIUS * 2, Theme.RADIUS * 2));
        g2.dispose();
        super.paintComponent(g);
    }
}
