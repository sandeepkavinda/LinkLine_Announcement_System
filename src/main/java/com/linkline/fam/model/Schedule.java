package com.linkline.fam.model;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** An announcement schedule together with its recurrence rules. */
public class Schedule {

    /** How far ahead {@link #nextOccurrence} looks (covers any realistic custom interval). */
    private static final int MAX_LOOKAHEAD_DAYS = 3700;

    private long id;
    private String name = "";
    private String description = "";
    private String notes = "";
    private LocalTime time = LocalTime.of(9, 0);
    private Long audioId;
    private String audioName;
    private String deviceId;
    private String deviceName;
    private boolean enabled = true;
    private RecurrenceType recurrence = RecurrenceType.DAILY;
    /** Bit 0 = Monday ... bit 6 = Sunday. */
    private int daysMask;
    private int intervalN = 1;
    private IntervalUnit intervalUnit = IntervalUnit.DAYS;
    private LocalDate startDate = LocalDate.now();
    private LocalDate endDate;
    private int repeatCount = 1;
    private int repeatGapSec = 5;
    private LocalDateTime createdAt = LocalDateTime.now();
    private LocalDateTime updatedAt = LocalDateTime.now();

    // ---- recurrence logic -------------------------------------------------------------

    public static int bit(DayOfWeek d) {
        return 1 << (d.getValue() - 1);
    }

    public boolean hasDay(DayOfWeek d) {
        return (daysMask & bit(d)) != 0;
    }

    /** True if this schedule fires on the given calendar day (ignoring the time of day). */
    public boolean occursOn(LocalDate d) {
        if (d.isBefore(startDate) || (endDate != null && d.isAfter(endDate))) {
            return false;
        }
        DayOfWeek dow = d.getDayOfWeek();
        switch (recurrence) {
            case ONCE:
                return d.equals(startDate);
            case DAILY:
                return true;
            case WEEKDAYS:
                return dow != DayOfWeek.SATURDAY && dow != DayOfWeek.SUNDAY;
            case WEEKENDS:
                return dow == DayOfWeek.SATURDAY || dow == DayOfWeek.SUNDAY;
            case SELECTED_DAYS:
                return hasDay(dow);
            case CUSTOM:
                int n = Math.max(1, intervalN);
                if (intervalUnit == IntervalUnit.DAYS) {
                    return ChronoUnit.DAYS.between(startDate, d) % n == 0;
                }
                LocalDate weekOfStart = startDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
                LocalDate weekOfD = d.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
                long weeks = ChronoUnit.WEEKS.between(weekOfStart, weekOfD);
                boolean dayMatches = daysMask == 0 ? dow == startDate.getDayOfWeek() : hasDay(dow);
                return weeks % n == 0 && dayMatches;
            default:
                return false;
        }
    }

    /** The first planned run strictly after {@code after}, or null if there is none. */
    public LocalDateTime nextOccurrence(LocalDateTime after) {
        LocalDate day = after.toLocalDate();
        if (day.isBefore(startDate)) {
            day = startDate;
        }
        for (int i = 0; i < MAX_LOOKAHEAD_DAYS; i++) {
            LocalDate d = day.plusDays(i);
            if (endDate != null && d.isAfter(endDate)) {
                return null;
            }
            if (occursOn(d)) {
                LocalDateTime candidate = LocalDateTime.of(d, time);
                if (candidate.isAfter(after)) {
                    return candidate;
                }
            }
        }
        return null;
    }

    /** All planned runs in the window ({@code from} exclusive, {@code to} inclusive), oldest first. */
    public List<LocalDateTime> occurrencesBetween(LocalDateTime from, LocalDateTime to) {
        List<LocalDateTime> result = new ArrayList<>();
        for (LocalDate d = from.toLocalDate(); !d.isAfter(to.toLocalDate()); d = d.plusDays(1)) {
            if (occursOn(d)) {
                LocalDateTime dt = LocalDateTime.of(d, time);
                if (dt.isAfter(from) && !dt.isAfter(to)) {
                    result.add(dt);
                }
            }
        }
        return result;
    }

    public String describeRecurrence() {
        switch (recurrence) {
            case ONCE:
                return "Once";
            case DAILY:
                return "Every day";
            case WEEKDAYS:
                return "Weekdays";
            case WEEKENDS:
                return "Weekends";
            case SELECTED_DAYS:
                return daysText();
            case CUSTOM:
                String unit = intervalUnit == IntervalUnit.DAYS ? "day" : "week";
                String every = intervalN == 1 ? "Every " + unit : "Every " + intervalN + " " + unit + "s";
                return intervalUnit == IntervalUnit.WEEKS && daysMask != 0 ? every + " · " + daysText() : every;
            default:
                return "";
        }
    }

    private String daysText() {
        StringBuilder sb = new StringBuilder();
        for (DayOfWeek d : DayOfWeek.values()) {
            if (hasDay(d)) {
                if (sb.length() > 0) {
                    sb.append(", ");
                }
                String n = d.name();
                sb.append(n.charAt(0)).append(n.substring(1, 3).toLowerCase(Locale.ROOT));
            }
        }
        return sb.length() == 0 ? "No days selected" : sb.toString();
    }

    public Schedule copy() {
        Schedule s = new Schedule();
        s.id = id;
        s.name = name;
        s.description = description;
        s.notes = notes;
        s.time = time;
        s.audioId = audioId;
        s.audioName = audioName;
        s.deviceId = deviceId;
        s.deviceName = deviceName;
        s.enabled = enabled;
        s.recurrence = recurrence;
        s.daysMask = daysMask;
        s.intervalN = intervalN;
        s.intervalUnit = intervalUnit;
        s.startDate = startDate;
        s.endDate = endDate;
        s.repeatCount = repeatCount;
        s.repeatGapSec = repeatGapSec;
        s.createdAt = createdAt;
        s.updatedAt = updatedAt;
        return s;
    }

    // ---- accessors --------------------------------------------------------------------

    public long getId() { return id; }
    public void setId(long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
    public LocalTime getTime() { return time; }
    public void setTime(LocalTime time) { this.time = time.withSecond(0).withNano(0); }
    public Long getAudioId() { return audioId; }
    public void setAudioId(Long audioId) { this.audioId = audioId; }
    public String getAudioName() { return audioName; }
    public void setAudioName(String audioName) { this.audioName = audioName; }
    public String getDeviceId() { return deviceId; }
    public void setDeviceId(String deviceId) { this.deviceId = deviceId; }
    public String getDeviceName() { return deviceName; }
    public void setDeviceName(String deviceName) { this.deviceName = deviceName; }
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public RecurrenceType getRecurrence() { return recurrence; }
    public void setRecurrence(RecurrenceType recurrence) { this.recurrence = recurrence; }
    public int getDaysMask() { return daysMask; }
    public void setDaysMask(int daysMask) { this.daysMask = daysMask; }
    public int getIntervalN() { return intervalN; }
    public void setIntervalN(int intervalN) { this.intervalN = intervalN; }
    public IntervalUnit getIntervalUnit() { return intervalUnit; }
    public void setIntervalUnit(IntervalUnit intervalUnit) { this.intervalUnit = intervalUnit; }
    public LocalDate getStartDate() { return startDate; }
    public void setStartDate(LocalDate startDate) { this.startDate = startDate; }
    public LocalDate getEndDate() { return endDate; }
    public void setEndDate(LocalDate endDate) { this.endDate = endDate; }
    public int getRepeatCount() { return repeatCount; }
    public void setRepeatCount(int repeatCount) { this.repeatCount = repeatCount; }
    public int getRepeatGapSec() { return repeatGapSec; }
    public void setRepeatGapSec(int repeatGapSec) { this.repeatGapSec = repeatGapSec; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
