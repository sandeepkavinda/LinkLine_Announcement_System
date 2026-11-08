package com.linkline.fam.service;

import com.linkline.fam.db.AudioRepository;
import com.linkline.fam.db.ScheduleRepository;
import com.linkline.fam.model.AudioItem;
import com.linkline.fam.model.RecurrenceType;
import com.linkline.fam.model.Schedule;
import com.linkline.fam.model.TriggerType;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/** Create, edit, delete and enable/disable schedules, with validation. */
public final class ScheduleService {

    private final ScheduleRepository repo;
    private final AudioRepository audio;
    private final EventBus bus;
    private final AnnouncementService announcements;

    public ScheduleService(ScheduleRepository repo, AudioRepository audio, EventBus bus,
                           AnnouncementService announcements) {
        this.repo = repo;
        this.audio = audio;
        this.bus = bus;
        this.announcements = announcements;
    }

    public List<Schedule> list() {
        return repo.findAll();
    }

    public Optional<Schedule> find(long id) {
        return repo.find(id);
    }

    /** @throws IllegalArgumentException with a user-readable message when the schedule is not valid */
    public void validate(Schedule s) {
        if (s.getName() == null || s.getName().isBlank()) {
            throw new IllegalArgumentException("Please enter an announcement name.");
        }
        if (s.getTime() == null) {
            throw new IllegalArgumentException("Please choose the time of the announcement.");
        }
        if (s.getAudioId() == null) {
            if (s.isEnabled()) {
                throw new IllegalArgumentException("Please assign an audio file, or switch the schedule to inactive.");
            }
        } else if (audio.find(s.getAudioId()).isEmpty()) {
            throw new IllegalArgumentException("The selected audio file no longer exists in the library.");
        }
        if (s.getStartDate() == null) {
            throw new IllegalArgumentException("Please choose a start date.");
        }
        if (s.getEndDate() != null && s.getEndDate().isBefore(s.getStartDate())) {
            throw new IllegalArgumentException("The end date cannot be before the start date.");
        }
        if (s.getRecurrence() == RecurrenceType.SELECTED_DAYS && s.getDaysMask() == 0) {
            throw new IllegalArgumentException("Select at least one day of the week.");
        }
        if (s.getRecurrence() == RecurrenceType.CUSTOM && s.getIntervalN() < 1) {
            throw new IllegalArgumentException("The repeat interval must be at least 1.");
        }
        if (s.getRepeatCount() < 1 || s.getRepeatCount() > 20) {
            throw new IllegalArgumentException("Plays per announcement must be between 1 and 20.");
        }
    }

    public long save(Schedule s) {
        validate(s);
        long id;
        if (s.getId() == 0) {
            id = repo.insert(s);
        } else {
            repo.update(s);
            id = s.getId();
        }
        bus.publish(EventBus.Topic.SCHEDULES);
        return id;
    }

    public void delete(long id) {
        repo.delete(id);
        bus.publish(EventBus.Topic.SCHEDULES);
        bus.publish(EventBus.Topic.HISTORY);
    }

    public void setEnabled(Schedule s, boolean enabled) {
        if (enabled && s.getAudioId() == null) {
            throw new IllegalArgumentException("This schedule has no audio file. Assign one before enabling it.");
        }
        repo.setEnabled(s.getId(), enabled);
        bus.publish(EventBus.Topic.SCHEDULES);
    }

    /** Plays the schedule right now with its own audio and device ("Play now"). */
    public void playNow(Schedule s) {
        announcements.enqueueSchedule(s, TriggerType.MANUAL, LocalDateTime.now().withNano(0));
    }

    public Optional<AudioItem> audioOf(Schedule s) {
        return s.getAudioId() == null ? Optional.empty() : audio.find(s.getAudioId());
    }
}
