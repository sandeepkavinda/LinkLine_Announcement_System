package com.linkline.fam;

import com.linkline.fam.audio.DeviceCatalog;
import com.linkline.fam.audio.JavaSoundDeviceCatalog;
import com.linkline.fam.audio.SessionDucker;
import com.linkline.fam.audio.UnsupportedDucker;
import com.linkline.fam.audio.win.WindowsDeviceCatalog;
import com.linkline.fam.audio.win.WindowsSessionDucker;
import com.linkline.fam.db.AudioRepository;
import com.linkline.fam.db.Database;
import com.linkline.fam.db.DuckJournalRepository;
import com.linkline.fam.db.HistoryRepository;
import com.linkline.fam.db.ScheduleRepository;
import com.linkline.fam.db.SettingsRepository;
import com.linkline.fam.platform.Os;
import com.linkline.fam.service.AnnouncementService;
import com.linkline.fam.service.AudioLibraryService;
import com.linkline.fam.service.DeviceService;
import com.linkline.fam.service.EventBus;
import com.linkline.fam.service.PreviewService;
import com.linkline.fam.service.ScheduleService;
import com.linkline.fam.service.SchedulerService;
import com.linkline.fam.service.SettingsService;
import com.linkline.fam.util.AppPaths;
import java.time.LocalDateTime;
import java.time.Instant;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Wires the application together and owns the lifecycle of its services. */
public final class AppContext implements AutoCloseable {

    private static final Logger LOG = Logger.getLogger(AppContext.class.getName());

    public final Instant startedAt = Instant.now();
    public final EventBus bus = new EventBus();
    public final Database db;
    public final SettingsService settings;
    public final HistoryRepository history;
    public final ScheduleRepository scheduleRepo;
    public final AudioRepository audioRepo;
    public final AudioLibraryService library;
    public final DeviceService devices;
    public final SessionDucker ducker;
    public final AnnouncementService announcements;
    public final ScheduleService schedules;
    public final SchedulerService scheduler;
    public final PreviewService preview;

    private boolean closed;

    public AppContext() {
        db = new Database(AppPaths.databaseFile());
        settings = new SettingsService(new SettingsRepository(db), bus);
        history = new HistoryRepository(db);
        scheduleRepo = new ScheduleRepository(db);
        audioRepo = new AudioRepository(db);

        DeviceCatalog catalog;
        if (Os.isWindows()) {
            catalog = new WindowsDeviceCatalog();
            ducker = new WindowsSessionDucker(new DuckJournalRepository(db));
        } else {
            catalog = new JavaSoundDeviceCatalog();
            ducker = new UnsupportedDucker();
        }

        library = new AudioLibraryService(audioRepo, scheduleRepo, bus);
        devices = new DeviceService(catalog, settings, bus);
        announcements = new AnnouncementService(history, audioRepo, library, devices, settings, ducker, bus);
        schedules = new ScheduleService(scheduleRepo, audioRepo, bus, announcements);
        scheduler = new SchedulerService(scheduleRepo, history, settings, announcements, bus);
        preview = new PreviewService(library, bus);
    }

    /** Starts the background services. Called once, after the window exists. */
    public void start() {
        try {
            ducker.recoverFromJournal(); // undo a lowered volume left by a crash
        } catch (RuntimeException e) {
            LOG.log(Level.WARNING, "Volume recovery failed", e);
        }
        int interrupted = history.failInterrupted();
        if (interrupted > 0) {
            LOG.warning(interrupted + " announcement(s) were interrupted by the last shutdown");
        }
        history.purgeOlderThan(LocalDateTime.now().minusDays(settings.retentionDays()));
        devices.start();
        scheduler.start();
    }

    @Override
    public synchronized void close() {
        if (closed) {
            return;
        }
        closed = true;
        LOG.info("Shutting down");
        scheduler.stop();
        preview.stop();
        announcements.shutdown(); // waits for a running announcement to restore volumes
        devices.shutdown();
        db.close();
    }
}
