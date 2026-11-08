package com.linkline.fam.ui.pages;

import com.linkline.fam.AppContext;
import com.linkline.fam.model.Schedule;
import com.linkline.fam.service.AnnouncementService;
import com.linkline.fam.service.DeviceService;
import com.linkline.fam.service.EventBus;
import com.linkline.fam.service.SchedulerService;
import com.linkline.fam.service.SchedulerService.TimelineItem;
import com.linkline.fam.service.SchedulerService.TimelineStatus;
import com.linkline.fam.ui.Async;
import com.linkline.fam.ui.Dialogs;
import com.linkline.fam.ui.Theme;
import com.linkline.fam.ui.Toast;
import com.linkline.fam.ui.Ui;
import com.linkline.fam.ui.components.Card;
import com.linkline.fam.ui.components.Icons;
import com.linkline.fam.ui.components.Icons.Glyph;
import com.linkline.fam.ui.components.Page;
import com.linkline.fam.ui.components.StatusStyle;
import com.linkline.fam.util.Fmt;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.Timer;

/** The overview: counts, the next announcement with a live countdown, today's timeline and system health. */
public final class DashboardPage extends Page {

    /** Everything the page shows that has to be read from the database. */
    private record Snapshot(int total, int active, List<TimelineItem> today,
                            Optional<SchedulerService.Upcoming> next, boolean databaseOk) {
    }

    /** A number card: caption, big value, small line underneath and a muted icon. */
    private static final class StatCard extends Card {
        final JLabel value = Ui.label("–", 28f, Theme.TEXT, Font.BOLD);
        final JLabel sub = Ui.muted(" ");

        StatCard(String caption, Glyph glyph) {
            super(new BorderLayout(0, 6));
            setBorder(Ui.pad(16, 20, 16, 20));
            JPanel top = Ui.transparent(new BorderLayout());
            top.add(Ui.caption(caption), BorderLayout.WEST);
            top.add(new JLabel(Icons.of(glyph, 18, Theme.TEXT_FAINT)), BorderLayout.EAST);
            add(top, BorderLayout.NORTH);
            add(value, BorderLayout.CENTER);
            add(sub, BorderLayout.SOUTH);
        }
    }

    /** One line in the system status card: indicator dot, name, value. */
    private static final class StatusRow extends JPanel {
        private final JLabel name;
        private final JLabel value = Ui.label("", 13f, Theme.TEXT_MUTED, Font.PLAIN);

        StatusRow(String title) {
            super(new BorderLayout(10, 0));
            setOpaque(false);
            name = Ui.label(title, 13f, Theme.TEXT, Font.PLAIN);
            name.setIcon(Ui.dot(Theme.TEXT_FAINT, 16));
            name.setIconTextGap(10);
            value.setHorizontalAlignment(javax.swing.SwingConstants.RIGHT);
            add(name, BorderLayout.WEST);
            add(value, BorderLayout.CENTER);
            setBorder(Ui.pad(7, 0, 7, 0));
        }

        void set(Color dot, String text) {
            name.setIcon(Ui.dot(dot, 16));
            value.setText(text);
            value.setToolTipText(text);
        }
    }

    /** Vertical list with a thin rail behind the status markers. */
    private static final class Timeline extends JPanel {
        Timeline() {
            setOpaque(false);
            setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            int n = getComponentCount();
            if (n < 2) {
                return;
            }
            Component first = getComponent(0);
            Component last = getComponent(n - 1);
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(Theme.BORDER);
            int x = 10 + 12; // row padding + half of the marker
            g2.drawLine(x, first.getY() + first.getHeight() / 2, x, last.getY() + last.getHeight() / 2);
            g2.dispose();
        }
    }

    private final StatCard totalCard = new StatCard("Total schedules", Glyph.CALENDAR);
    private final StatCard activeCard = new StatCard("Active schedules", Glyph.POWER);
    private final StatCard todayCard = new StatCard("Today's announcements", Glyph.SPEAKER);
    private final StatCard nextCard = new StatCard("Next announcement", Glyph.CLOCK);

    private final JLabel nextCaption = Ui.caption("Next announcement");
    private final JLabel nextName = Ui.label("", 22f, Theme.TEXT, Font.BOLD);
    private final JLabel nextTime = Ui.label("", 13.5f, Theme.TEXT_MUTED, Font.PLAIN);
    private final JLabel countdown = Ui.label("--:--:--", 40f, Theme.ACCENT, Font.BOLD);
    private final JLabel countdownCaption = Ui.muted("starts in");
    private final JLabel nextAudio = Ui.label("", 13f, Theme.TEXT, Font.PLAIN);
    private final JLabel nextDevice = Ui.label("", 13f, Theme.TEXT, Font.PLAIN);
    private final JButton stopButton = Ui.button("Stop announcement", Glyph.STOP, Ui.Kind.DANGER);
    private final JPanel nextDetails = Ui.transparent(new GridLayout(0, 1, 0, 8));

    private final StatusRow schedulerRow = new StatusRow("Scheduler");
    private final StatusRow outputRow = new StatusRow("Announcement output");
    private final StatusRow connectionRow = new StatusRow("Device connection");
    private final StatusRow volumeRow = new StatusRow("Volume priority");
    private final StatusRow databaseRow = new StatusRow("Database");
    private final StatusRow appRow = new StatusRow("Application");

    private final Timeline timeline = new Timeline();
    private final JLabel todayDate = Ui.muted("");
    private final Timer ticker = new Timer(1000, e -> tick());

    private Snapshot snapshot;
    private LocalDateTime nextAt;
    private LocalDateTime lastRefresh = LocalDateTime.MIN;

    public DashboardPage(AppContext ctx) {
        super(ctx);
        setBorder(Ui.pad(Theme.PAD));

        JPanel content = Ui.transparent(null);
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));

        JPanel stats = Ui.transparent(new GridLayout(1, 4, Theme.GAP, 0));
        stats.add(totalCard);
        stats.add(activeCard);
        stats.add(todayCard);
        stats.add(nextCard);
        stats.setAlignmentX(Component.LEFT_ALIGNMENT);
        content.add(stats);
        content.add(Ui.vgap(Theme.GAP));

        JPanel middle = Ui.transparent(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.fill = GridBagConstraints.BOTH;
        c.weighty = 1;
        c.gridy = 0;
        c.gridx = 0;
        c.weightx = 0.6;
        c.insets = new Insets(0, 0, 0, Theme.GAP / 2);
        middle.add(nextAnnouncementCard(), c);
        c.gridx = 1;
        c.weightx = 0.4;
        c.insets = new Insets(0, Theme.GAP / 2, 0, 0);
        middle.add(systemStatusCard(), c);
        middle.setAlignmentX(Component.LEFT_ALIGNMENT);
        content.add(middle);
        content.add(Ui.vgap(Theme.GAP));

        JComponent today = todayTimelineCard();
        today.setAlignmentX(Component.LEFT_ALIGNMENT);
        content.add(today);

        JPanel wrap = Ui.transparent(new BorderLayout());
        wrap.add(content, BorderLayout.NORTH);
        add(Ui.scroll(wrap), BorderLayout.CENTER);

        refreshOn(EventBus.Topic.SCHEDULES, EventBus.Topic.HISTORY, EventBus.Topic.PLAYBACK,
                EventBus.Topic.DEVICES, EventBus.Topic.SETTINGS, EventBus.Topic.AUDIO);
    }

    @Override
    public String title() {
        return "Dashboard";
    }

    @Override
    public String subtitle() {
        return "Live status of the announcement system";
    }

    // ---- layout ---------------------------------------------------------------------------------

    private JComponent nextAnnouncementCard() {
        Card card = new Card(new BorderLayout(0, 14));
        JPanel head = Ui.transparent(new BorderLayout());
        head.add(nextCaption, BorderLayout.WEST);
        card.add(head, BorderLayout.NORTH);

        JPanel left = Ui.transparent(new GridLayout(0, 1, 0, 4));
        left.add(nextName);
        left.add(nextTime);

        JPanel clock = Ui.transparent(new GridLayout(0, 1, 0, 0));
        countdown.setFont(Theme.mono(38f, Font.BOLD));
        countdown.setHorizontalAlignment(javax.swing.SwingConstants.RIGHT);
        countdownCaption.setHorizontalAlignment(javax.swing.SwingConstants.RIGHT);
        clock.add(countdown);
        clock.add(countdownCaption);

        JPanel top = Ui.transparent(new BorderLayout(20, 0));
        top.add(left, BorderLayout.CENTER);
        top.add(clock, BorderLayout.EAST);

        nextAudio.setIcon(Icons.of(Glyph.MUSIC, 18, Theme.TEXT_MUTED));
        nextAudio.setIconTextGap(10);
        nextDevice.setIcon(Icons.of(Glyph.SPEAKER, 18, Theme.TEXT_MUTED));
        nextDevice.setIconTextGap(10);
        nextDetails.add(nextAudio);
        nextDetails.add(nextDevice);

        JButton test = Ui.button("Test announcement", Glyph.PLAY, Ui.Kind.SECONDARY);
        test.addActionListener(e -> testAnnouncement());
        stopButton.setVisible(false);
        stopButton.addActionListener(e -> {
            Async.run(ctx.announcements::stopCurrent);
            Toast.info(this, "Stopping the announcement…");
        });
        JPanel foot = Ui.transparent(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 0, 0));
        foot.add(test);
        foot.add(Ui.hgap(8));
        foot.add(stopButton);

        JPanel body = Ui.transparent(new BorderLayout(0, 16));
        body.add(top, BorderLayout.NORTH);
        body.add(nextDetails, BorderLayout.CENTER);
        body.add(foot, BorderLayout.SOUTH);
        card.add(body, BorderLayout.CENTER);
        return card;
    }

    private JComponent systemStatusCard() {
        Card card = new Card(new BorderLayout(0, 8));
        card.add(Ui.caption("System status"), BorderLayout.NORTH);
        JPanel rows = Ui.transparent(new GridLayout(0, 1));
        rows.add(schedulerRow);
        rows.add(outputRow);
        rows.add(connectionRow);
        rows.add(volumeRow);
        rows.add(databaseRow);
        rows.add(appRow);
        card.add(rows, BorderLayout.CENTER);
        return card;
    }

    private JComponent todayTimelineCard() {
        Card card = new Card(new BorderLayout(0, 10));
        JPanel head = Ui.transparent(new BorderLayout());
        head.add(Ui.heading("Today's schedule"), BorderLayout.WEST);
        head.add(todayDate, BorderLayout.EAST);
        card.add(head, BorderLayout.NORTH);
        card.add(timeline, BorderLayout.CENTER);
        return card;
    }

    // ---- data -----------------------------------------------------------------------------------

    @Override
    protected void refresh() {
        Async.run(() -> {
            List<Schedule> all = ctx.scheduler.schedules();
            int active = (int) all.stream().filter(Schedule::isEnabled).count();
            return new Snapshot(all.size(), active, ctx.scheduler.today(), ctx.scheduler.nextAnnouncement(),
                    ctx.db.isHealthy());
        }, s -> {
            snapshot = s;
            lastRefresh = LocalDateTime.now();
            apply();
        }, e -> Toast.error(this, "Could not load the dashboard: " + e.getMessage()));
    }

    @Override
    protected void visibilityChanged(boolean visible) {
        if (visible) {
            ticker.start();
        } else {
            ticker.stop();
        }
    }

    private void apply() {
        Snapshot s = snapshot;
        if (s == null) {
            return;
        }
        totalCard.value.setText(String.valueOf(s.total()));
        totalCard.sub.setText(s.total() == 1 ? "announcement schedule" : "announcement schedules");
        activeCard.value.setText(String.valueOf(s.active()));
        int inactive = s.total() - s.active();
        activeCard.sub.setText(inactive == 0 ? "all schedules on" : inactive + " switched off");

        long done = s.today().stream().filter(i -> i.status() == TimelineStatus.COMPLETED).count();
        long failed = s.today().stream().filter(i -> i.status() == TimelineStatus.FAILED).count();
        todayCard.value.setText(String.valueOf(s.today().size()));
        todayCard.sub.setText(done + " completed" + (failed > 0 ? ", " + failed + " failed" : ""));

        if (s.next().isPresent()) {
            SchedulerService.Upcoming n = s.next().get();
            nextAt = n.at();
            nextCard.value.setText(Fmt.time(n.at().toLocalTime()));
            nextCard.sub.setText(n.schedule().getName());
            nextName.setText(n.schedule().getName());
            nextTime.setText(Ui.relativeDateTime(n.at()));
            nextAudio.setText(n.schedule().getAudioName() == null ? "No audio assigned" : n.schedule().getAudioName());
            nextDevice.setText(n.schedule().getDeviceName() != null ? n.schedule().getDeviceName()
                    : ctx.devices.announcementOutputName());
        } else {
            nextAt = null;
            nextCard.value.setText("–");
            nextCard.sub.setText("nothing scheduled");
            nextName.setText("No upcoming announcement");
            nextTime.setText("Create an active schedule with an audio file.");
            nextAudio.setText("–");
            nextDevice.setText(ctx.devices.announcementOutputName());
        }
        buildTimeline(s.today());
        tick();
    }

    private void buildTimeline(List<TimelineItem> items) {
        timeline.removeAll();
        todayDate.setText(Fmt.dateLong(LocalDate.now()));
        if (items.isEmpty()) {
            JLabel empty = Ui.label("Nothing is scheduled for today.", 13f, Theme.TEXT_MUTED, Font.PLAIN);
            empty.setIcon(Icons.of(Glyph.CALENDAR, 18, Theme.TEXT_FAINT));
            empty.setIconTextGap(10);
            empty.setBorder(Ui.pad(14, 10, 14, 10));
            empty.setAlignmentX(Component.LEFT_ALIGNMENT);
            timeline.add(empty);
        } else {
            for (TimelineItem it : items) {
                timeline.add(timelineRow(it));
            }
        }
        timeline.revalidate();
        timeline.repaint();
    }

    private JComponent timelineRow(TimelineItem it) {
        JPanel row = new JPanel(new GridBagLayout()) {
            @Override
            protected void paintComponent(Graphics g) {
                if (it.status() == TimelineStatus.PLAYING) {
                    Graphics2D g2 = (Graphics2D) g.create();
                    g2.setColor(Theme.alpha(Theme.ACCENT, 22));
                    g2.fillRoundRect(0, 2, getWidth(), getHeight() - 4, 12, 12);
                    g2.dispose();
                }
                super.paintComponent(g);
            }
        };
        row.setOpaque(false);
        row.setBorder(Ui.pad(8, 10, 8, 12));
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 56));
        if (it.message() != null && !it.message().isBlank()) {
            row.setToolTipText(it.message());
        }

        GridBagConstraints c = new GridBagConstraints();
        c.gridy = 0;
        c.anchor = GridBagConstraints.WEST;
        c.insets = new Insets(0, 0, 0, 14);
        c.gridx = 0;
        row.add(new JLabel(StatusStyle.marker(it.status(), 24)), c);

        JLabel time = Ui.label(Fmt.time(it.time().toLocalTime()), 13.5f, Theme.TEXT, Font.BOLD);
        time.setPreferredSize(new Dimension(88, 20));
        c.gridx = 1;
        row.add(time, c);

        JPanel texts = Ui.transparent(new GridLayout(2, 1, 0, 1));
        texts.add(Ui.label(it.name(), 13.5f, Theme.TEXT, Font.PLAIN));
        String audio = it.audioName() == null || it.audioName().isBlank() ? "No audio" : it.audioName();
        texts.add(Ui.label(audio, 12f, Theme.TEXT_MUTED, Font.PLAIN));
        c.gridx = 2;
        c.weightx = 1;
        c.fill = GridBagConstraints.HORIZONTAL;
        row.add(texts, c);

        JLabel status = Ui.label(it.status().label(), 13f, StatusStyle.color(it.status()), Font.PLAIN);
        status.setHorizontalAlignment(javax.swing.SwingConstants.RIGHT);
        status.setPreferredSize(new Dimension(100, 20));
        c.gridx = 3;
        c.weightx = 0;
        c.fill = GridBagConstraints.NONE;
        c.anchor = GridBagConstraints.EAST;
        c.insets = new Insets(0, 0, 0, 0);
        row.add(status, c);
        return row;
    }

    // ---- once a second --------------------------------------------------------------------------

    private void tick() {
        // countdown or "now playing"
        Optional<AnnouncementService.Job> job = ctx.announcements.currentJob();
        stopButton.setVisible(job.isPresent());
        if (job.isPresent()) {
            nextCaption.setText("NOW PLAYING");
            nextCaption.setForeground(Theme.ACCENT);
            countdown.setText("LIVE");
            countdownCaption.setText(job.get().scheduleName());
        } else {
            nextCaption.setText("NEXT ANNOUNCEMENT");
            nextCaption.setForeground(Theme.TEXT_FAINT);
            if (nextAt == null) {
                countdown.setText("--:--:--");
                countdownCaption.setText("no announcement scheduled");
            } else {
                Duration left = Duration.between(LocalDateTime.now(), nextAt);
                if (left.isNegative() || left.isZero()) {
                    countdown.setText("00:00:00");
                    countdownCaption.setText("starting");
                    if (Duration.between(lastRefresh, LocalDateTime.now()).getSeconds() >= 2 && isShowingPage()) {
                        refresh(); // pick up the announcement that just started and the one after it
                    }
                } else {
                    countdown.setText(Fmt.countdown(left));
                    countdownCaption.setText("starts in");
                }
            }
        }
        updateSystemStatus(job);
    }

    private void updateSystemStatus(Optional<AnnouncementService.Job> job) {
        SchedulerService sch = ctx.scheduler;
        if (sch.isRunning()) {
            schedulerRow.set(Theme.SUCCESS, "Running");
        } else {
            schedulerRow.set(Theme.DANGER, sch.lastError() == null ? "Not running" : "Stopped: " + sch.lastError());
        }

        outputRow.set(Theme.ACCENT, ctx.devices.announcementOutputName());

        DeviceService.SelectionStatus st = ctx.devices.selectionStatus();
        switch (st.health()) {
            case OK:
                connectionRow.set(Theme.SUCCESS, "Connected");
                break;
            case WARNING:
                connectionRow.set(Theme.WARNING, st.text());
                break;
            default:
                connectionRow.set(Theme.DANGER, st.text());
                break;
        }

        if (!ctx.settings.duckEnabled()) {
            volumeRow.set(Theme.TEXT_FAINT, "Off");
        } else if (ctx.ducker.isSupported()) {
            volumeRow.set(Theme.SUCCESS, "Others lowered to " + ctx.settings.duckPercent() + "%");
        } else {
            volumeRow.set(Theme.WARNING, "Needs Windows");
        }

        boolean dbOk = snapshot == null || snapshot.databaseOk();
        databaseRow.set(dbOk ? Theme.SUCCESS : Theme.DANGER, dbOk ? "Healthy" : "Not responding");

        long secs = Math.max(0, Duration.between(ctx.startedAt, Instant.now()).getSeconds());
        String uptime = secs >= 86400 ? secs / 86400 + "d " + (secs % 86400) / 3600 + "h"
                : secs >= 3600 ? secs / 3600 + "h " + (secs % 3600) / 60 + "m" : Math.max(1, secs / 60) + " min";
        appRow.set(Theme.SUCCESS, job.isPresent() ? "Playing · up " + uptime : "Running · up " + uptime);
    }

    private void testAnnouncement() {
        String out = ctx.devices.announcementOutputName();
        if (!Dialogs.confirm(this, "Test announcement",
                "FAM will play a short test chime through\n" + out + "\n\nOther applications are lowered while it plays.",
                "Play test", false)) {
            return;
        }
        Async.run(() -> ctx.announcements.enqueueTest(null), () -> Toast.info(this, "Test announcement sent"),
                e -> Dialogs.error(this, "Test failed", String.valueOf(e.getMessage())));
    }
}