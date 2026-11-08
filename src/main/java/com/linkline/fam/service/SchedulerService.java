package com.linkline.fam.service;

import com.linkline.fam.db.HistoryRepository;
import com.linkline.fam.db.ScheduleRepository;
import com.linkline.fam.model.HistoryEntry;
import com.linkline.fam.model.PlayStatus;
import com.linkline.fam.model.Schedule;
import com.linkline.fam.model.TriggerType;
import com.linkline.fam.util.Fmt;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The background scheduler. A single daemon thread wakes every second and fires every announcement
 * whose planned time fell between the previous check and now. Nothing here runs on the Swing thread.
 *
 * <p>Restarts, sleep and clock changes are handled by remembering the time of the last check and
 * treating anything planned since then as due; how late a run may still play is set by the
 * missed-schedule rules. Every occurrence is claimed in the history table first, and a unique index
 * there makes a second execution of the same occurrence impossible.
 */
public final class SchedulerService {

    private static final Logger LOG = Logger.getLogger(SchedulerService.class.getName());

    /** A run that is at most this late counts as on time (covers scheduler jitter and short pauses). */
    private static final Duration ON_TIME = Duration.ofSeconds(10);
    /** Occurrences older than this are not reconsidered after a restart. */
    private static final Duration MAX_RECOVERY_WINDOW = Duration.ofHours(24);
    private static final int PERSIST_EVERY_TICKS = 5;

    public enum TimelineStatus {
        COMPLETED("Completed"), UPCOMING("Upcoming"), QUEUED("Queued"), PLAYING("Playing"),
        FAILED("Failed"), SKIPPED("Skipped");

        private final String label;

        TimelineStatus(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    /** One line of today's schedule. */
    public record TimelineItem(Long scheduleId, String name, LocalDateTime time, String audioName,
                               TimelineStatus status, String message) {
    }

    public record Upcoming(Schedule schedule, LocalDateTime at) {
    }

    private record Due(Schedule schedule, LocalDateTime planned) {
    }

    private final ScheduleRepository scheduleRepo;
    private final HistoryRepository history;
    private final SettingsService settings;
    private final AnnouncementService announcements;
    private final EventBus bus;

    private volatile List<Schedule> cache = List.of();
    private volatile boolean cacheDirty = true;
    private volatile Instant cacheLoadedAt = Instant.EPOCH;

    private ScheduledExecutorService executor;
    private volatile LocalDateTime lastCheck;
    private volatile Instant heartbeat = Instant.EPOCH;
    private volatile boolean started;
    private volatile String lastError;
    private int ticks;

    public SchedulerService(ScheduleRepository scheduleRepo, HistoryRepository history, SettingsService settings,
                            AnnouncementService announcements, EventBus bus) {
        this.scheduleRepo = scheduleRepo;
        this.history = history;
        this.settings = settings;
        this.announcements = announcements;
        this.bus = bus;
    }

    // ---- lifecycle ----------------------------------------------------------------------

    public synchronized void start() {
        if (started) {
            return;
        }
        LocalDateTime now = LocalDateTime.now().withNano(0);
        lastCheck = restoreLastCheck(now);
        bus.subscribe(EventBus.Topic.SCHEDULES, () -> cacheDirty = true);
        executor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "fam-scheduler");
            t.setDaemon(true);
            return t;
        });
        started = true;
        executor.scheduleWithFixedDelay(this::tick, 0, 1, TimeUnit.SECONDS);
        LOG.info("Scheduler started; checking from " + lastCheck);
    }

    public synchronized void stop() {
        if (!started) {
            return;
        }
        started = false;
        executor.shutdownNow();
        persistLastCheck();
    }

    private LocalDateTime restoreLastCheck(LocalDateTime now) {
        String stored = settings.get(SettingsService.LAST_CHECK, "");
        if (!stored.isBlank()) {
            try {
                LocalDateTime t = LocalDateTime.parse(stored, Fmt.DB_DT);
                if (!t.isAfter(now)) {
                    return t;
                }
            } catch (RuntimeException ignored) {
                // fall through to "now"
            }
        }
        return now;
    }

    private void persistLastCheck() {
        LocalDateTime t = lastCheck;
        if (t != null) {
            settings.setQuietly(SettingsService.LAST_CHECK, t.format(Fmt.DB_DT));
        }
    }

    // ---- status for the UI --------------------------------------------------------------

    public boolean isRunning() {
        return started && Duration.between(heartbeat, Instant.now()).getSeconds() < 5;
    }

    public Instant lastHeartbeat() {
        return heartbeat;
    }

    public String lastError() {
        return lastError;
    }

    // ---- the tick -----------------------------------------------------------------------

    private void tick() {
        try {
            LocalDateTime now = LocalDateTime.now().withNano(0);
            LocalDateTime from = lastCheck;
            if (now.isBefore(from)) {
                LOG.warning("System clock moved backwards; resuming from " + now);
                lastCheck = now;
            } else if (now.isAfter(from)) {
                LocalDateTime windowStart = from.isBefore(now.minus(MAX_RECOVERY_WINDOW)) ? now.minus(MAX_RECOVERY_WINDOW) : from;
                process(windowStart, now);
                lastCheck = now;
            }
            heartbeat = Instant.now();
            lastError = null;
            if (++ticks % PERSIST_EVERY_TICKS == 0) {
                persistLastCheck();
            }
        } catch (Throwable t) { // the scheduler thread must never die
            lastError = t.getMessage() == null ? t.toString() : t.getMessage();
            LOG.log(Level.SEVERE, "Scheduler tick failed", t);
        }
    }

    /** Fires or records every occurrence planned in (from, to]. Visible for tests. */
    void process(LocalDateTime from, LocalDateTime to) {
        List<Due> due = new ArrayList<>();
        for (Schedule s : schedules()) {
            if (!s.isEnabled()) {
                continue;
            }
            for (LocalDateTime planned : s.occurrencesBetween(from, to)) {
                // A schedule created or changed after a slot had already passed does not owe that slot.
                if (!planned.isBefore(s.getUpdatedAt())) {
                    due.add(new Due(s, planned));
                }
            }
        }
        due.sort(Comparator.comparing(Due::planned).thenComparing(d -> d.schedule().getId()));
        for (Due d : due) {
            handle(d, to);
        }
    }

    private void handle(Due d, LocalDateTime now) {
        Duration late = Duration.between(d.planned(), now);
        if (late.compareTo(ON_TIME) <= 0) {
            fire(d, TriggerType.SCHEDULED);
            return;
        }
        boolean play;
        switch (settings.missedPolicy()) {
            case GRACE:
                play = late.compareTo(Duration.ofMinutes(settings.graceMinutes())) <= 0;
                break;
            case TODAY:
                play = d.planned().toLocalDate().equals(now.toLocalDate());
                break;
            case SKIP:
            default:
                play = false;
        }
        if (play) {
            fire(d, TriggerType.RECOVERED);
        } else {
            long id = history.claim(d.schedule().getId(), d.schedule().getName(),
                    d.schedule().getAudioName(), "", d.planned(), PlayStatus.SKIPPED, TriggerType.SCHEDULED,
                    "Missed: FAM was not running (or the computer was asleep) at " + Fmt.time(d.planned().toLocalTime())
                            + ", and the missed-schedule rules say not to play it late.");
            if (id >= 0) {
                bus.publish(EventBus.Topic.HISTORY);
            }
        }
    }

    private void fire(Due d, TriggerType trigger) {
        try {
            announcements.enqueueSchedule(d.schedule(), trigger, d.planned());
        } catch (RuntimeException e) {
            LOG.log(Level.SEVERE, "Could not start announcement '" + d.schedule().getName() + "'", e);
        }
    }

    // ---- schedule cache -------------------------------------------------------------------

    /** Current schedules (cached; refreshed when schedules change and at least every 30 seconds). */
    public List<Schedule> schedules() {
        if (cacheDirty || Duration.between(cacheLoadedAt, Instant.now()).getSeconds() > 30) {
            cacheDirty = false;
            cache = scheduleRepo.findAll();
            cacheLoadedAt = Instant.now();
        }
        return cache;
    }

    // ---- queries for the dashboard ----------------------------------------------------------

    /** The soonest upcoming announcement among enabled schedules that have audio. */
    public Optional<Upcoming> nextAnnouncement() {
        LocalDateTime now = LocalDateTime.now();
        Upcoming best = null;
        for (Schedule s : schedules()) {
            if (!s.isEnabled() || s.getAudioId() == null) {
                continue;
            }
            LocalDateTime at = s.nextOccurrence(now);
            if (at != null && (best == null || at.isBefore(best.at()))) {
                best = new Upcoming(s, at);
            }
        }
        return Optional.ofNullable(best);
    }

    /** Everything scheduled for today: what happened already, what is playing, and what is still to come. */
    public List<TimelineItem> today() {
        LocalDateTime now = LocalDateTime.now();
        LocalDate today = now.toLocalDate();
        List<TimelineItem> items = new ArrayList<>();
        Set<String> seen = new HashSet<>();

        for (HistoryEntry h : history.findForDay(today)) {
            if (!h.trigger().isScheduledKind()) {
                continue;
            }
            seen.add(h.scheduleId() + "@" + h.plannedAt());
            items.add(new TimelineItem(h.scheduleId(), h.scheduleName(), h.plannedAt(), h.audioName(),
                    toTimeline(h.status()), h.message()));
        }
        for (Schedule s : schedules()) {
            if (!s.isEnabled() || !s.occursOn(today)) {
                continue;
            }
            LocalDateTime planned = LocalDateTime.of(today, s.getTime());
            if (planned.isAfter(now) && !planned.isBefore(s.getUpdatedAt()) && !seen.contains(s.getId() + "@" + planned)) {
                items.add(new TimelineItem(s.getId(), s.getName(), planned,
                        s.getAudioName() == null ? "No audio assigned" : s.getAudioName(), TimelineStatus.UPCOMING, ""));
            }
        }
        items.sort(Comparator.comparing(TimelineItem::time).thenComparing(i -> i.name().toLowerCase()));
        return items;
    }

    private static TimelineStatus toTimeline(PlayStatus s) {
        switch (s) {
            case COMPLETED:
                return TimelineStatus.COMPLETED;
            case PLAYING:
                return TimelineStatus.PLAYING;
            case QUEUED:
                return TimelineStatus.QUEUED;
            case FAILED:
                return TimelineStatus.FAILED;
            case SKIPPED:
            default:
                return TimelineStatus.SKIPPED;
        }
    }
}
