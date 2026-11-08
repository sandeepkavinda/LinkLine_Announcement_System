package com.linkline.fam.service;

import com.linkline.fam.audio.AudioFiles;
import com.linkline.fam.audio.AudioPlayer;
import com.linkline.fam.audio.Chime;
import com.linkline.fam.audio.SessionDucker;
import com.linkline.fam.db.AudioRepository;
import com.linkline.fam.db.HistoryRepository;
import com.linkline.fam.model.AudioItem;
import com.linkline.fam.model.PlayStatus;
import com.linkline.fam.model.Schedule;
import com.linkline.fam.model.TriggerType;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.sound.sampled.LineUnavailableException;

/**
 * Plays announcements, one at a time, in the order they were queued. For each announcement it:
 * picks the output device, lowers other applications' volume, plays the schedule's own audio,
 * restores every volume, and records the outcome in the history.
 */
public final class AnnouncementService {

    private static final Logger LOG = Logger.getLogger(AnnouncementService.class.getName());
    private static final int DEVICE_BUSY_RETRIES = 3;

    /** One queued announcement. {@code audio == null} means the built-in test chime. */
    public record Job(long historyId, Long scheduleId, String scheduleName, AudioItem audio,
                      String deviceId, String deviceName, TriggerType trigger,
                      int repeatCount, int repeatGapSec) {
    }

    private final HistoryRepository history;
    private final AudioRepository audioRepo;
    private final AudioLibraryService library;
    private final DeviceService devices;
    private final SettingsService settings;
    private final SessionDucker ducker;
    private final EventBus bus;

    private final ExecutorService worker = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "fam-announcer");
        t.setDaemon(false); // lets a running announcement finish its volume restore on exit
        return t;
    });

    private volatile Job current;
    private volatile AudioPlayer currentPlayer;
    private volatile boolean stopRequested;
    private volatile boolean shuttingDown;

    public AnnouncementService(HistoryRepository history, AudioRepository audioRepo, AudioLibraryService library,
                               DeviceService devices, SettingsService settings, SessionDucker ducker, EventBus bus) {
        this.history = history;
        this.audioRepo = audioRepo;
        this.library = library;
        this.devices = devices;
        this.settings = settings;
        this.ducker = ducker;
        this.bus = bus;
    }

    public Optional<Job> currentJob() {
        return Optional.ofNullable(current);
    }

    public boolean isPlaying() {
        return current != null;
    }

    // ---- queueing -----------------------------------------------------------------------

    /**
     * Queues a schedule's announcement with the schedule's own audio file.
     *
     * @param planned the planned run time (for scheduled runs this is the occurrence being fired)
     * @return the history id, or -1 if this occurrence was already handled (duplicate protection)
     */
    public long enqueueSchedule(Schedule s, TriggerType trigger, LocalDateTime planned) {
        AudioItem audio = s.getAudioId() == null ? null : audioRepo.find(s.getAudioId()).orElse(null);
        String audioName = audio == null ? "" : audio.displayName();
        String deviceLabel = s.getDeviceName() == null ? "" : s.getDeviceName();

        String problem = null;
        if (audio == null) {
            problem = "No audio file is assigned to this schedule";
        } else if (!library.fileExists(audio)) {
            problem = "The audio file '" + audio.displayName() + "' is missing from FAM's storage";
        }
        if (problem != null) {
            long id = history.claim(s.getId(), s.getName(), audioName, deviceLabel, planned,
                    PlayStatus.FAILED, trigger, problem);
            bus.publish(EventBus.Topic.HISTORY);
            return id;
        }

        long historyId = history.claim(s.getId(), s.getName(), audioName, deviceLabel, planned,
                PlayStatus.QUEUED, trigger, "");
        if (historyId < 0) {
            LOG.info("Occurrence of '" + s.getName() + "' at " + planned + " was already handled; not playing again");
            return -1;
        }
        bus.publish(EventBus.Topic.HISTORY);
        submit(new Job(historyId, s.getId(), s.getName(), audio, s.getDeviceId(), s.getDeviceName(), trigger,
                Math.max(1, s.getRepeatCount()), Math.max(0, s.getRepeatGapSec())));
        return historyId;
    }

    /** Test announcement through the selected output device, with the full ducking path. */
    public long enqueueTest(AudioItem audioOrNull) {
        String name = audioOrNull == null ? "Built-in test chime" : audioOrNull.displayName();
        long historyId = history.claim(null, "Test announcement", name, "", LocalDateTime.now().withNano(0),
                PlayStatus.QUEUED, TriggerType.TEST, "");
        bus.publish(EventBus.Topic.HISTORY);
        submit(new Job(historyId, null, "Test announcement", audioOrNull, null, null, TriggerType.TEST, 1, 0));
        return historyId;
    }

    private void submit(Job job) {
        if (shuttingDown) {
            history.finish(job.historyId(), PlayStatus.FAILED, "FAM is shutting down");
            return;
        }
        worker.execute(() -> run(job));
    }

    // ---- running one announcement ---------------------------------------------------

    private void run(Job job) {
        if (shuttingDown) {
            finish(job, PlayStatus.FAILED, "FAM was closed before this announcement could play");
            return;
        }
        current = job;
        stopRequested = false;
        bus.publish(EventBus.Topic.PLAYBACK);
        SessionDucker.Handle ducking = null;
        try {
            DeviceService.Target target;
            try {
                target = devices.resolve(job.deviceId(), job.deviceName());
            } catch (DeviceUnavailableException e) {
                finish(job, PlayStatus.FAILED, e.getMessage());
                return;
            }
            String audioName = job.audio() == null ? "Built-in test chime" : job.audio().displayName();
            history.markPlaying(job.historyId(), audioName, target.label());
            bus.publish(EventBus.Topic.HISTORY);

            StringBuilder notes = new StringBuilder();
            if (target.warning() != null) {
                notes.append(target.warning()).append(". ");
            }

            if (settings.duckEnabled()) {
                if (ducker.isSupported()) {
                    try {
                        ducking = ducker.begin(settings.duckPercent() / 100.0);
                        notes.append("Lowered ").append(ducking.sessionCount())
                                .append(" application(s) to ").append(settings.duckPercent()).append("%. ");
                        sleep(120); // let the new volumes take effect before the announcement starts
                    } catch (Exception e) {
                        LOG.log(Level.WARNING, "Volume ducking failed; playing without it", e);
                        notes.append("Could not lower other applications' volume (").append(e.getMessage()).append("). ");
                    }
                }
            }

            try {
                playAll(job, target);
                if (stopRequested) {
                    notes.append("Stopped manually before the end.");
                }
                finish(job, PlayStatus.COMPLETED, notes.toString().strip());
            } catch (Exception e) {
                LOG.log(Level.WARNING, "Announcement '" + job.scheduleName() + "' failed", e);
                finish(job, PlayStatus.FAILED, (notes + friendly(e)).strip());
            }
        } catch (RuntimeException e) {
            LOG.log(Level.SEVERE, "Unexpected failure in announcement", e);
            finish(job, PlayStatus.FAILED, "Unexpected error: " + e.getMessage());
        } finally {
            if (ducking != null) {
                ducking.restore(); // always put the other applications' volumes back
            }
            current = null;
            currentPlayer = null;
            bus.publish(EventBus.Topic.PLAYBACK);
        }
    }

    private void playAll(Job job, DeviceService.Target target) throws Exception {
        AudioPlayer.Source source;
        if (job.audio() == null) {
            source = Chime::open;
        } else {
            Path file = library.fileOf(job.audio());
            if (!Files.isRegularFile(file)) {
                throw new java.io.FileNotFoundException("Audio file is missing: " + job.audio().displayName());
            }
            source = () -> AudioFiles.open(file);
        }
        for (int i = 0; i < job.repeatCount() && !stopRequested; i++) {
            playOnce(source, target);
            if (i < job.repeatCount() - 1 && !stopRequested) {
                sleep(job.repeatGapSec() * 1000L);
            }
        }
    }

    /** A device can be briefly busy (for example while Windows switches it); retry a few times. */
    private void playOnce(AudioPlayer.Source source, DeviceService.Target target) throws Exception {
        for (int attempt = 1; ; attempt++) {
            AudioPlayer player = new AudioPlayer();
            currentPlayer = player;
            if (stopRequested) {
                return;
            }
            try {
                player.play(source, target.mixer());
                return;
            } catch (LineUnavailableException e) {
                if (attempt >= DEVICE_BUSY_RETRIES || stopRequested) {
                    throw e;
                }
                LOG.info("Output device busy, retrying (" + attempt + ")");
                sleep(2000);
            }
        }
    }

    private void finish(Job job, PlayStatus status, String message) {
        history.finish(job.historyId(), status, message);
        bus.publish(EventBus.Topic.HISTORY);
    }

    private static String friendly(Exception e) {
        if (e instanceof LineUnavailableException) {
            return "The output device is busy or unavailable: " + e.getMessage();
        }
        return e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
    }

    private void sleep(long ms) {
        long end = System.currentTimeMillis() + ms;
        while (!stopRequested) {
            long left = end - System.currentTimeMillis();
            if (left <= 0) {
                return;
            }
            try {
                Thread.sleep(Math.min(left, 100));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    // ---- control ------------------------------------------------------------------------

    /** Stops the announcement that is playing now. Queued ones still run. */
    public void stopCurrent() {
        stopRequested = true;
        AudioPlayer p = currentPlayer;
        if (p != null) {
            p.stop();
        }
    }

    /** Stops playback, lets the volume restore finish and refuses new work. */
    public void shutdown() {
        shuttingDown = true;
        stopCurrent();
        worker.shutdown();
        try {
            if (!worker.awaitTermination(8, java.util.concurrent.TimeUnit.SECONDS)) {
                LOG.warning("Announcement worker did not stop in time");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
