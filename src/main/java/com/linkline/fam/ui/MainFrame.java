package com.linkline.fam.ui;

import com.linkline.fam.AppContext;
import com.linkline.fam.AppInfo;
import com.linkline.fam.service.DeviceService;
import com.linkline.fam.service.EventBus;
import com.linkline.fam.ui.components.Icons;
import com.linkline.fam.ui.components.Icons.Glyph;
import com.linkline.fam.ui.components.NavItem;
import com.linkline.fam.ui.components.Page;
import com.linkline.fam.ui.pages.AudioLibraryPage;
import com.linkline.fam.ui.pages.DashboardPage;
import com.linkline.fam.ui.pages.DevicesPage;
import com.linkline.fam.ui.pages.HistoryPage;
import com.linkline.fam.ui.pages.SchedulesPage;
import com.linkline.fam.ui.pages.SettingsPage;
import com.linkline.fam.util.Fmt;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import javax.swing.AbstractAction;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.KeyStroke;
import javax.swing.Timer;

/** The application window: sidebar, header and the page area. */
public final class MainFrame extends JFrame {

    private record Health(Color color, String text, String detail) {
    }

    private final AppContext ctx;
    private final List<Page> pages = new ArrayList<>();
    private final List<NavItem> navItems = new ArrayList<>();
    private final CardLayout cards = new CardLayout();
    private final JPanel pageArea = new JPanel(cards);
    private final JLabel pageTitle = Ui.label("", 20f, Theme.TEXT, Font.BOLD);
    private final JLabel pageSubtitle = Ui.muted("");
    private final JLabel clock = Ui.label("", 13f, Theme.TEXT, Font.BOLD);
    private final JLabel date = Ui.muted("");
    private final JLabel headerStatus = Ui.label("", 12.5f, Theme.TEXT_MUTED, Font.PLAIN);
    private final JLabel sidebarStatus = Ui.label("", 12f, Theme.TEXT_MUTED, Font.PLAIN);
    private final Timer ticker = new Timer(1000, e -> tick());
    private int current = -1;
    private boolean pulse;

    public MainFrame(AppContext ctx, Runnable onExit, Runnable onClose) {
        super(AppInfo.NAME);
        this.ctx = ctx;
        setIconImages(AppIcon.images());
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                onClose.run();
            }
        });

        pages.add(new DashboardPage(ctx));
        pages.add(new SchedulesPage(ctx));
        pages.add(new AudioLibraryPage(ctx));
        pages.add(new DevicesPage(ctx));
        pages.add(new HistoryPage(ctx));
        pages.add(new SettingsPage(ctx));
        for (int i = 0; i < pages.size(); i++) {
            pageArea.add(pages.get(i), "p" + i);
        }
        pageArea.setOpaque(false);

        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(Theme.BG);
        root.add(buildSidebar(), BorderLayout.WEST);
        JPanel main = new JPanel(new BorderLayout());
        main.setOpaque(false);
        main.add(buildHeader(), BorderLayout.NORTH);
        main.add(pageArea, BorderLayout.CENTER);
        root.add(main, BorderLayout.CENTER);
        setContentPane(root);
        installShortcuts(root);

        setMinimumSize(new Dimension(1120, 700));
        setSize(1320, 820);
        setLocationRelativeTo(null);

        // status and clock follow what happens in the background
        ctx.bus.subscribe(EventBus.Topic.PLAYBACK, this::tick);
        ctx.bus.subscribe(EventBus.Topic.DEVICES, this::tick);
        ctx.bus.subscribe(EventBus.Topic.SETTINGS, this::tick);
        ticker.start();
        navigate(0);
        tick();
    }

    // ---- structure ------------------------------------------------------------------------------

    private JComponent buildSidebar() {
        JPanel side = new JPanel();
        side.setBackground(Theme.SIDEBAR);
        side.setLayout(new BoxLayout(side, BoxLayout.Y_AXIS));
        side.setBorder(javax.swing.BorderFactory.createCompoundBorder(
                javax.swing.BorderFactory.createMatteBorder(0, 0, 0, 1, Theme.BORDER_SOFT), Ui.pad(18, 14, 14, 14)));
        side.setPreferredSize(new Dimension(248, 100));

        JPanel brand = Ui.transparent(new BorderLayout(12, 0));
        JLabel logo = new JLabel(new ImageIcon(AppIcon.image(40)));
        JPanel names = Ui.transparent(new java.awt.GridLayout(2, 1, 0, 0));
        names.add(Ui.label(AppInfo.SHORT_NAME, 17f, Theme.TEXT, Font.BOLD));
        names.add(Ui.label(AppInfo.NAME, 11f, Theme.TEXT_MUTED, Font.PLAIN));
        brand.add(logo, BorderLayout.WEST);
        brand.add(names, BorderLayout.CENTER);
        brand.setAlignmentX(0f);
        brand.setMaximumSize(new Dimension(Integer.MAX_VALUE, 44));
        brand.setToolTipText(AppInfo.SUBTITLE);
        side.add(brand);
        side.add(Ui.vgap(26));

        String[] labels = {"Dashboard", "Announcement Schedules", "Audio Library", "Audio Devices",
            "Announcement History", "Settings"};
        Glyph[] glyphs = {Glyph.DASHBOARD, Glyph.CALENDAR, Glyph.MUSIC, Glyph.SPEAKER, Glyph.HISTORY, Glyph.SLIDERS};
        for (int i = 0; i < labels.length; i++) {
            final int index = i;
            NavItem item = new NavItem(glyphs[i], labels[i], () -> navigate(index));
            item.setAlignmentX(0f);
            navItems.add(item);
            side.add(item);
            side.add(Ui.vgap(4));
        }
        side.add(javax.swing.Box.createVerticalGlue());

        JPanel foot = Ui.transparent(new BorderLayout(0, 6));
        foot.setAlignmentX(0f);
        foot.setMaximumSize(new Dimension(Integer.MAX_VALUE, 56));
        foot.add(sidebarStatus, BorderLayout.NORTH);
        foot.add(Ui.label("Version " + AppInfo.VERSION, 11.5f, Theme.TEXT_FAINT, Font.PLAIN), BorderLayout.SOUTH);
        foot.setBorder(Ui.pad(0, 6, 0, 0));
        side.add(foot);
        return side;
    }

    private JComponent buildHeader() {
        JPanel header = new JPanel(new BorderLayout(20, 0));
        header.setBackground(Theme.BG);
        header.setBorder(javax.swing.BorderFactory.createCompoundBorder(
                javax.swing.BorderFactory.createMatteBorder(0, 0, 1, 0, Theme.BORDER_SOFT), Ui.pad(14, 24, 14, 24)));

        JPanel titles = Ui.transparent(new java.awt.GridLayout(2, 1, 0, 1));
        titles.add(pageTitle);
        titles.add(pageSubtitle);
        header.add(titles, BorderLayout.WEST);

        JPanel right = Ui.transparent(new java.awt.FlowLayout(java.awt.FlowLayout.RIGHT, 18, 4));

        JPanel when = Ui.transparent(new java.awt.GridLayout(2, 1, 0, 0));
        clock.setHorizontalAlignment(javax.swing.SwingConstants.RIGHT);
        date.setHorizontalAlignment(javax.swing.SwingConstants.RIGHT);
        when.add(clock);
        when.add(date);

        JButton test = Ui.button("Test", Glyph.PLAY, Ui.Kind.GHOST);
        test.setToolTipText("Play a test announcement through the announcement output");
        test.addActionListener(e -> {
            String out = ctx.devices.announcementOutputName();
            if (Dialogs.confirm(this, "Test announcement",
                    "FAM will play a short test chime through\n" + out + "\n\nOther applications are lowered while it plays.",
                    "Play test", false)) {
                Async.run(() -> ctx.announcements.enqueueTest(null), () -> Toast.info(this, "Test announcement sent"),
                        err -> Dialogs.error(this, "Test failed", String.valueOf(err.getMessage())));
            }
        });

        JLabel admin = Ui.label("Administrator", 12.5f, Theme.TEXT_MUTED, Font.PLAIN);
        admin.setIcon(Icons.of(Glyph.USER, 18, Theme.TEXT_MUTED));
        admin.setIconTextGap(8);

        right.add(headerStatus);
        right.add(test);
        right.add(when);
        right.add(admin);
        header.add(right, BorderLayout.EAST);
        return header;
    }

    private void installShortcuts(JComponent root) {
        for (int i = 0; i < 6; i++) {
            final int index = i;
            String key = "nav" + i;
            root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
                    .put(KeyStroke.getKeyStroke(KeyEvent.VK_1 + i, KeyEvent.CTRL_DOWN_MASK), key);
            root.getActionMap().put(key, new AbstractAction() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    navigate(index);
                }
            });
        }
    }

    // ---- navigation -----------------------------------------------------------------------------

    public void navigate(int index) {
        if (index == current) {
            return;
        }
        if (current >= 0) {
            pages.get(current).pageHidden();
            navItems.get(current).setSelected(false);
        }
        current = index;
        Page page = pages.get(index);
        navItems.get(index).setSelected(true);
        pageTitle.setText(page.title());
        pageSubtitle.setText(page.subtitle());
        cards.show(pageArea, "p" + index);
        page.pageShown();
    }

    /** Releases listeners; called when the application exits. */
    public void shutdown() {
        ticker.stop();
        pages.forEach(Page::dispose);
    }

    // ---- once a second ------------------------------------------------------------------------------

    private void tick() {
        LocalDateTime now = LocalDateTime.now();
        clock.setText(Fmt.timeSeconds(now.toLocalTime()));
        date.setText(Fmt.date(now.toLocalDate()));

        Health h = health();
        pulse = !pulse;
        boolean playing = ctx.announcements.isPlaying();
        Color dot = playing && pulse ? Theme.alpha(h.color(), 120) : h.color();
        headerStatus.setIcon(playing ? Icons.of(Glyph.WAVE, 18, dot) : Ui.dot(dot, 18));
        headerStatus.setIconTextGap(8);
        headerStatus.setText(h.text());
        headerStatus.setToolTipText(h.detail());
        sidebarStatus.setIcon(Ui.dot(h.color(), 16));
        sidebarStatus.setIconTextGap(8);
        sidebarStatus.setText(h.text());
        sidebarStatus.setToolTipText(h.detail());
    }

    private Health health() {
        var playing = ctx.announcements.currentJob();
        if (playing.isPresent()) {
            return new Health(Theme.ACCENT, "Playing: " + playing.get().scheduleName(),
                    "An announcement is playing now.");
        }
        if (!ctx.scheduler.isRunning()) {
            return new Health(Theme.DANGER, "Scheduler not running",
                    ctx.scheduler.lastError() == null ? "Announcements will not play on time." : ctx.scheduler.lastError());
        }
        DeviceService.SelectionStatus st = ctx.devices.selectionStatus();
        if (st.health() == DeviceService.Health.ERROR) {
            return new Health(Theme.DANGER, "Output unavailable", st.text());
        }
        if (st.health() == DeviceService.Health.WARNING) {
            return new Health(Theme.WARNING, "Check audio output", st.text());
        }
        return new Health(Theme.SUCCESS, "Audio ready", "Announcements play on: " + st.text());
    }
}
