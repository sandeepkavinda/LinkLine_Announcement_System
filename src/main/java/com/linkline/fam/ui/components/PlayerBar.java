package com.linkline.fam.ui.components;

import com.linkline.fam.AppContext;
import com.linkline.fam.model.AudioItem;
import com.linkline.fam.service.EventBus;
import com.linkline.fam.service.PreviewService;
import com.linkline.fam.ui.Theme;
import com.linkline.fam.ui.Toast;
import com.linkline.fam.ui.Ui;
import com.linkline.fam.ui.components.Icons.Glyph;
import com.linkline.fam.util.Fmt;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.RoundRectangle2D;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.Timer;

/**
 * Play / pause / stop controls with a progress line for previewing the selected audio file.
 * Previews use the Windows default output, never the announcement device.
 */
public final class PlayerBar extends Card {

    private final AppContext ctx;
    private final JButton playPause = Ui.button("", Glyph.PLAY, Ui.Kind.PRIMARY);
    private final JButton stop = Ui.button("", Glyph.STOP, Ui.Kind.SECONDARY);
    private final JLabel title = Ui.label("Select an audio file to preview", 13.5f, Theme.TEXT, Font.BOLD);
    private final JLabel time = Ui.label("0:00 / 0:00", 12.5f, Theme.TEXT_MUTED, Font.PLAIN);
    private final Progress progress = new Progress();
    private final Timer ticker = new Timer(150, e -> update());
    private final Runnable unsubscribe;
    private AudioItem item;

    public PlayerBar(AppContext ctx) {
        super(new BorderLayout(14, 0));
        this.ctx = ctx;
        setBorder(Ui.pad(12, 16, 12, 16));

        JPanel buttons = Ui.transparent(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 8, 0));
        buttons.add(playPause);
        buttons.add(stop);
        add(buttons, BorderLayout.WEST);

        JPanel middle = Ui.transparent(new BorderLayout(0, 6));
        JPanel top = Ui.transparent(new BorderLayout());
        top.add(title, BorderLayout.WEST);
        top.add(time, BorderLayout.EAST);
        middle.add(top, BorderLayout.NORTH);
        middle.add(progress, BorderLayout.CENTER);
        add(middle, BorderLayout.CENTER);

        JLabel note = Ui.label("Preview plays on the Windows default output", 11.5f, Theme.TEXT_FAINT, Font.PLAIN);
        note.setHorizontalAlignment(javax.swing.SwingConstants.RIGHT);
        add(note, BorderLayout.EAST);

        playPause.setToolTipText("Play / pause preview");
        stop.setToolTipText("Stop preview");
        playPause.addActionListener(e -> togglePlay());
        stop.addActionListener(e -> ctx.preview.stop());
        unsubscribe = ctx.bus.subscribe(EventBus.Topic.PREVIEW, this::update);
        update();
    }

    public void setItem(AudioItem newItem) {
        boolean changed = item == null || newItem == null || item.id() != newItem.id();
        item = newItem;
        if (changed && ctx.preview.state() != PreviewService.State.STOPPED
                && (newItem == null || ctx.preview.currentId() != newItem.id())) {
            ctx.preview.stop();
        }
        update();
    }

    public void togglePlay() {
        if (item == null) {
            return;
        }
        PreviewService p = ctx.preview;
        boolean same = p.currentId() == item.id();
        if (same && p.state() == PreviewService.State.PLAYING) {
            p.pause();
        } else if (same && p.state() == PreviewService.State.PAUSED) {
            p.resume();
        } else {
            p.play(item, msg -> SwingUtilities.invokeLater(() -> Toast.error(this, msg)));
        }
    }

    public void release() {
        ticker.stop();
        unsubscribe.run();
        ctx.preview.stop();
    }

    private void update() {
        PreviewService p = ctx.preview;
        boolean mine = item != null && p.currentId() == item.id();
        PreviewService.State st = mine ? p.state() : PreviewService.State.STOPPED;
        playPause.setIcon(Icons.of(st == PreviewService.State.PLAYING ? Glyph.PAUSE : Glyph.PLAY, 16, Color.WHITE));
        playPause.setEnabled(item != null);
        stop.setIcon(Icons.of(Glyph.STOP, 16, Theme.TEXT_MUTED));
        stop.setEnabled(st != PreviewService.State.STOPPED);
        title.setText(item == null ? "Select an audio file to preview" : item.displayName());
        long dur = item == null ? 0 : item.durationMs();
        long pos = mine ? Math.min(p.positionMs(), dur) : 0;
        time.setText(Fmt.duration(pos) + " / " + Fmt.duration(dur));
        progress.set(dur <= 0 ? 0 : pos / (double) dur);
        if (st == PreviewService.State.PLAYING && !ticker.isRunning()) {
            ticker.start();
        } else if (st != PreviewService.State.PLAYING && ticker.isRunning()) {
            ticker.stop();
        }
    }

    private static final class Progress extends JComponent {
        private double value;

        Progress() {
            setPreferredSize(new java.awt.Dimension(10, 6));
        }

        void set(double v) {
            value = Math.max(0, Math.min(1, v));
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int h = 4;
            int y = (getHeight() - h) / 2;
            g2.setColor(Theme.SURFACE_3);
            g2.fill(new RoundRectangle2D.Double(0, y, getWidth(), h, h, h));
            g2.setColor(Theme.ACCENT);
            g2.fill(new RoundRectangle2D.Double(0, y, getWidth() * value, h, h, h));
            g2.dispose();
        }
    }
}
