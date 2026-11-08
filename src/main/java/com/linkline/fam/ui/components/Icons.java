package com.linkline.fam.ui.components;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Arc2D;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import javax.swing.Icon;

/** Crisp vector icons drawn with Java2D on a 24-unit grid, so no image files are needed. */
public final class Icons {

    public enum Glyph {
        DASHBOARD, CALENDAR, MUSIC, SPEAKER, HISTORY, SLIDERS, PLAY, PAUSE, STOP, PLUS, TRASH, EDIT,
        REFRESH, SEARCH, CHECK, WAVE, IMPORT, CLOCK, USER, WARN, CLOSE, FOLDER, EXPORT, MINUS, COPY, POWER
    }

    private Icons() {
    }

    public static Icon of(Glyph glyph, int size, Color color) {
        return new GlyphIcon(glyph, size, color);
    }

    /** Paints the glyph onto an existing graphics context (used for the logo and tray icon). */
    public static void paint(Graphics2D g, Glyph glyph, double x, double y, double size, Color color, float stroke) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        g2.translate(x, y);
        double s = size / 24.0;
        g2.scale(s, s);
        g2.setColor(color);
        g2.setStroke(new BasicStroke(stroke, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        draw(g2, glyph);
        g2.dispose();
    }

    private static final class GlyphIcon implements Icon {
        private final Glyph glyph;
        private final int size;
        private final Color color;

        GlyphIcon(Glyph glyph, int size, Color color) {
            this.glyph = glyph;
            this.size = size;
            this.color = color;
        }

        @Override
        public void paintIcon(Component c, Graphics g, int x, int y) {
            Color use = c != null && !c.isEnabled() ? new Color(color.getRed(), color.getGreen(), color.getBlue(), 90) : color;
            Icons.paint((Graphics2D) g, glyph, x, y, size, use, 1.8f);
        }

        @Override
        public int getIconWidth() {
            return size;
        }

        @Override
        public int getIconHeight() {
            return size;
        }
    }

    private static Line2D line(double x1, double y1, double x2, double y2) {
        return new Line2D.Double(x1, y1, x2, y2);
    }

    private static RoundRectangle2D rr(double x, double y, double w, double h, double r) {
        return new RoundRectangle2D.Double(x, y, w, h, r * 2, r * 2);
    }

    private static void draw(Graphics2D g, Glyph glyph) {
        switch (glyph) {
            case DASHBOARD:
                g.draw(rr(3, 3, 7, 7, 2));
                g.draw(rr(14, 3, 7, 7, 2));
                g.draw(rr(3, 14, 7, 7, 2));
                g.draw(rr(14, 14, 7, 7, 2));
                break;
            case CALENDAR:
                g.draw(rr(3, 5, 18, 16, 3));
                g.draw(line(3, 10, 21, 10));
                g.draw(line(8, 3, 8, 7));
                g.draw(line(16, 3, 16, 7));
                break;
            case MUSIC: {
                Path2D p = new Path2D.Double();
                p.moveTo(10, 17.5);
                p.lineTo(10, 5);
                p.lineTo(20, 3);
                p.lineTo(20, 15.5);
                g.draw(p);
                g.draw(new Ellipse2D.Double(5, 15, 5, 5));
                g.draw(new Ellipse2D.Double(15, 13, 5, 5));
                break;
            }
            case SPEAKER: {
                Path2D p = new Path2D.Double();
                p.moveTo(4, 9);
                p.lineTo(8, 9);
                p.lineTo(13, 5);
                p.lineTo(13, 19);
                p.lineTo(8, 15);
                p.lineTo(4, 15);
                p.closePath();
                g.draw(p);
                g.draw(new Arc2D.Double(9, 8, 8, 8, -50, 100, Arc2D.OPEN));
                g.draw(new Arc2D.Double(6, 5, 14, 14, -50, 100, Arc2D.OPEN));
                break;
            }
            case HISTORY:
            case CLOCK: {
                g.draw(new Ellipse2D.Double(3, 3, 18, 18));
                Path2D p = new Path2D.Double();
                p.moveTo(12, 7);
                p.lineTo(12, 12);
                p.lineTo(15.5, 14);
                g.draw(p);
                break;
            }
            case SLIDERS:
                g.draw(line(4, 7, 20, 7));
                g.draw(line(4, 12, 20, 12));
                g.draw(line(4, 17, 20, 17));
                g.fill(new Ellipse2D.Double(7, 4.8, 4.4, 4.4));
                g.fill(new Ellipse2D.Double(13, 9.8, 4.4, 4.4));
                g.fill(new Ellipse2D.Double(6, 14.8, 4.4, 4.4));
                break;
            case PLAY: {
                Path2D p = new Path2D.Double();
                p.moveTo(8, 5);
                p.lineTo(19, 12);
                p.lineTo(8, 19);
                p.closePath();
                g.fill(p);
                break;
            }
            case PAUSE:
                g.fill(rr(6, 5, 4, 14, 1));
                g.fill(rr(14, 5, 4, 14, 1));
                break;
            case STOP:
                g.fill(rr(6, 6, 12, 12, 2));
                break;
            case PLUS:
                g.draw(line(12, 5, 12, 19));
                g.draw(line(5, 12, 19, 12));
                break;
            case TRASH: {
                g.draw(line(4, 7, 20, 7));
                Path2D lid = new Path2D.Double();
                lid.moveTo(9, 7);
                lid.lineTo(9, 4);
                lid.lineTo(15, 4);
                lid.lineTo(15, 7);
                g.draw(lid);
                Path2D body = new Path2D.Double();
                body.moveTo(6, 7);
                body.lineTo(7, 20);
                body.lineTo(17, 20);
                body.lineTo(18, 7);
                g.draw(body);
                g.draw(line(10, 11, 10, 16));
                g.draw(line(14, 11, 14, 16));
                break;
            }
            case EDIT: {
                Path2D p = new Path2D.Double();
                p.moveTo(4, 20);
                p.lineTo(5, 15);
                p.lineTo(16, 4);
                p.lineTo(20, 8);
                p.lineTo(9, 19);
                p.closePath();
                g.draw(p);
                g.draw(line(13, 7, 17, 11));
                break;
            }
            case REFRESH: {
                g.draw(new Arc2D.Double(4, 4, 16, 16, 60, 290, Arc2D.OPEN));
                Path2D p = new Path2D.Double();
                p.moveTo(16.5, 1.8);
                p.lineTo(16, 5.2);
                p.lineTo(12.6, 4.4);
                g.draw(p);
                break;
            }
            case SEARCH:
                g.draw(new Ellipse2D.Double(4, 4, 12, 12));
                g.draw(line(14.5, 14.5, 20, 20));
                break;
            case CHECK: {
                Path2D p = new Path2D.Double();
                p.moveTo(5, 12.5);
                p.lineTo(10, 17.5);
                p.lineTo(19, 7);
                g.draw(p);
                break;
            }
            case WAVE:
                g.draw(line(4, 10, 4, 14));
                g.draw(line(8, 7, 8, 17));
                g.draw(line(12, 4, 12, 20));
                g.draw(line(16, 8, 16, 16));
                g.draw(line(20, 10, 20, 14));
                break;
            case IMPORT:
            case EXPORT: {
                boolean down = glyph == Glyph.IMPORT;
                g.draw(line(12, down ? 3 : 15, 12, down ? 15 : 3));
                Path2D head = new Path2D.Double();
                head.moveTo(7, down ? 10 : 8);
                head.lineTo(12, down ? 15 : 3);
                head.lineTo(17, down ? 10 : 8);
                g.draw(head);
                Path2D tray = new Path2D.Double();
                tray.moveTo(4, 16);
                tray.lineTo(4, 20);
                tray.lineTo(20, 20);
                tray.lineTo(20, 16);
                g.draw(tray);
                break;
            }
            case USER: {
                g.draw(new Ellipse2D.Double(8, 4, 8, 8));
                Path2D p = new Path2D.Double();
                p.moveTo(4, 21);
                p.curveTo(4, 16, 8, 14, 12, 14);
                p.curveTo(16, 14, 20, 16, 20, 21);
                g.draw(p);
                break;
            }
            case WARN: {
                Path2D p = new Path2D.Double();
                p.moveTo(12, 3);
                p.lineTo(22, 20);
                p.lineTo(2, 20);
                p.closePath();
                g.draw(p);
                g.draw(line(12, 9, 12, 14));
                g.fill(new Ellipse2D.Double(11, 16, 2, 2));
                break;
            }
            case CLOSE:
                g.draw(line(6, 6, 18, 18));
                g.draw(line(18, 6, 6, 18));
                break;
            case FOLDER: {
                Path2D p = new Path2D.Double();
                p.moveTo(3, 6);
                p.lineTo(10, 6);
                p.lineTo(12, 9);
                p.lineTo(21, 9);
                p.lineTo(21, 19);
                p.lineTo(3, 19);
                p.closePath();
                g.draw(p);
                break;
            }
            case MINUS:
                g.draw(line(6, 12, 18, 12));
                break;
            case COPY:
                g.draw(rr(8, 8, 12, 12, 2.5));
                Path2D back = new Path2D.Double();
                back.moveTo(16, 8);
                back.lineTo(16, 5.5);
                back.quadTo(16, 4, 14.5, 4);
                back.lineTo(5.5, 4);
                back.quadTo(4, 4, 4, 5.5);
                back.lineTo(4, 14.5);
                back.quadTo(4, 16, 5.5, 16);
                back.lineTo(8, 16);
                g.draw(back);
                break;
            case POWER:
                g.draw(line(12, 3, 12, 11));
                g.draw(new Arc2D.Double(5, 5, 14, 14, 120, 300, Arc2D.OPEN));
                break;
            default:
                break;
        }
    }
}
