package com.linkline.fam.ui;

import com.linkline.fam.ui.components.Icons;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.util.List;

/** The FAM logo: a rounded square in the accent color with a sound-wave glyph. Drawn in code, no image files. */
public final class AppIcon {

    private AppIcon() {
    }

    public static BufferedImage image(int size) {
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(Theme.ACCENT);
        g.fill(new RoundRectangle2D.Double(0, 0, size, size, size * 0.28, size * 0.28));
        double inner = size * 0.58;
        Icons.paint(g, Icons.Glyph.WAVE, (size - inner) / 2, (size - inner) / 2, inner, Color.WHITE, 2.4f);
        g.dispose();
        return img;
    }

    public static List<BufferedImage> images() {
        return List.of(image(16), image(24), image(32), image(48), image(64), image(128), image(256));
    }
}
