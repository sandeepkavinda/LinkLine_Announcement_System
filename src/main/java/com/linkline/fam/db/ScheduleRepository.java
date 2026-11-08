package com.linkline.fam.db;

import com.linkline.fam.model.IntervalUnit;
import com.linkline.fam.model.RecurrenceType;
import com.linkline.fam.model.Schedule;
import com.linkline.fam.util.Fmt;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class ScheduleRepository {

    private static final String SELECT =
            "SELECT s.*, a.display_name AS audio_name FROM schedules s "
                    + "LEFT JOIN audio_files a ON a.id = s.audio_id ";

    private final Database db;

    public ScheduleRepository(Database db) {
        this.db = db;
    }

    private static Schedule map(ResultSet rs) throws SQLException {
        Schedule s = new Schedule();
        s.setId(rs.getLong("id"));
        s.setName(rs.getString("name"));
        s.setDescription(rs.getString("description"));
        s.setNotes(rs.getString("notes"));
        s.setTime(LocalTime.parse(rs.getString("time_of_day"), Fmt.DB_T));
        long audio = rs.getLong("audio_id");
        s.setAudioId(rs.wasNull() ? null : audio);
        s.setAudioName(rs.getString("audio_name"));
        s.setDeviceId(rs.getString("device_id"));
        s.setDeviceName(rs.getString("device_name"));
        s.setEnabled(rs.getInt("enabled") != 0);
        s.setRecurrence(RecurrenceType.valueOf(rs.getString("recurrence")));
        s.setDaysMask(rs.getInt("days_mask"));
        s.setIntervalN(rs.getInt("interval_n"));
        s.setIntervalUnit(IntervalUnit.valueOf(rs.getString("interval_unit")));
        s.setStartDate(LocalDate.parse(rs.getString("start_date"), Fmt.DB_D));
        String end = rs.getString("end_date");
        s.setEndDate(end == null ? null : LocalDate.parse(end, Fmt.DB_D));
        s.setRepeatCount(rs.getInt("repeat_count"));
        s.setRepeatGapSec(rs.getInt("repeat_gap_sec"));
        s.setCreatedAt(LocalDateTime.parse(rs.getString("created_at"), Fmt.DB_DT));
        s.setUpdatedAt(LocalDateTime.parse(rs.getString("updated_at"), Fmt.DB_DT));
        return s;
    }

    public List<Schedule> findAll() {
        return db.query(c -> {
            List<Schedule> list = new ArrayList<>();
            try (Statement st = c.createStatement();
                 ResultSet rs = st.executeQuery(SELECT + "ORDER BY s.time_of_day, s.name COLLATE NOCASE")) {
                while (rs.next()) {
                    list.add(map(rs));
                }
            }
            return list;
        });
    }

    public Optional<Schedule> find(long id) {
        return db.query(c -> {
            try (PreparedStatement ps = c.prepareStatement(SELECT + "WHERE s.id = ?")) {
                ps.setLong(1, id);
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next() ? Optional.of(map(rs)) : Optional.empty();
                }
            }
        });
    }

    public List<Schedule> findByAudio(long audioId) {
        return db.query(c -> {
            List<Schedule> list = new ArrayList<>();
            try (PreparedStatement ps = c.prepareStatement(SELECT + "WHERE s.audio_id = ? ORDER BY s.time_of_day")) {
                ps.setLong(1, audioId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        list.add(map(rs));
                    }
                }
            }
            return list;
        });
    }

    /** Inserts the schedule and returns its new id. */
    public long insert(Schedule s) {
        return db.query(c -> {
            LocalDateTime now = LocalDateTime.now().withNano(0);
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO schedules(name, description, notes, time_of_day, audio_id, device_id, device_name, "
                            + "enabled, recurrence, days_mask, interval_n, interval_unit, start_date, end_date, "
                            + "repeat_count, repeat_gap_sec, created_at, updated_at) "
                            + "VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)")) {
                bind(ps, s);
                ps.setString(17, now.format(Fmt.DB_DT));
                ps.setString(18, now.format(Fmt.DB_DT));
                ps.executeUpdate();
            }
            try (Statement st = c.createStatement(); ResultSet rs = st.executeQuery("SELECT last_insert_rowid()")) {
                rs.next();
                return rs.getLong(1);
            }
        });
    }

    public void update(Schedule s) {
        db.exec(c -> {
            try (PreparedStatement ps = c.prepareStatement(
                    "UPDATE schedules SET name=?, description=?, notes=?, time_of_day=?, audio_id=?, device_id=?, "
                            + "device_name=?, enabled=?, recurrence=?, days_mask=?, interval_n=?, interval_unit=?, "
                            + "start_date=?, end_date=?, repeat_count=?, repeat_gap_sec=?, updated_at=? WHERE id=?")) {
                bind(ps, s);
                ps.setString(17, LocalDateTime.now().withNano(0).format(Fmt.DB_DT));
                ps.setLong(18, s.getId());
                ps.executeUpdate();
            }
        });
    }

    private static void bind(PreparedStatement ps, Schedule s) throws SQLException {
        ps.setString(1, s.getName());
        ps.setString(2, s.getDescription() == null ? "" : s.getDescription());
        ps.setString(3, s.getNotes() == null ? "" : s.getNotes());
        ps.setString(4, s.getTime().format(Fmt.DB_T));
        if (s.getAudioId() == null) {
            ps.setNull(5, Types.INTEGER);
        } else {
            ps.setLong(5, s.getAudioId());
        }
        ps.setString(6, s.getDeviceId());
        ps.setString(7, s.getDeviceName());
        ps.setInt(8, s.isEnabled() ? 1 : 0);
        ps.setString(9, s.getRecurrence().name());
        ps.setInt(10, s.getDaysMask());
        ps.setInt(11, Math.max(1, s.getIntervalN()));
        ps.setString(12, s.getIntervalUnit().name());
        ps.setString(13, s.getStartDate().format(Fmt.DB_D));
        ps.setString(14, s.getEndDate() == null ? null : s.getEndDate().format(Fmt.DB_D));
        ps.setInt(15, Math.max(1, s.getRepeatCount()));
        ps.setInt(16, Math.max(0, s.getRepeatGapSec()));
    }

    public void setEnabled(long id, boolean enabled) {
        db.exec(c -> {
            try (PreparedStatement ps = c.prepareStatement("UPDATE schedules SET enabled=?, updated_at=? WHERE id=?")) {
                ps.setInt(1, enabled ? 1 : 0);
                ps.setString(2, LocalDateTime.now().withNano(0).format(Fmt.DB_DT));
                ps.setLong(3, id);
                ps.executeUpdate();
            }
        });
    }

    public void delete(long id) {
        db.exec(c -> {
            try (PreparedStatement ps = c.prepareStatement("DELETE FROM schedules WHERE id = ?")) {
                ps.setLong(1, id);
                ps.executeUpdate();
            }
        });
    }

    /**
     * Moves every schedule that uses {@code oldAudioId} to {@code newAudioId}. When the new id is null
     * the audio reference is removed and the affected schedules are disabled (they cannot play).
     */
    public int reassignAudio(long oldAudioId, Long newAudioId) {
        return db.transaction(c -> {
            try (PreparedStatement ps = c.prepareStatement(
                    "UPDATE schedules SET audio_id = ?, enabled = CASE WHEN ? IS NULL THEN 0 ELSE enabled END, "
                            + "updated_at = ? WHERE audio_id = ?")) {
                if (newAudioId == null) {
                    ps.setNull(1, Types.INTEGER);
                    ps.setNull(2, Types.INTEGER);
                } else {
                    ps.setLong(1, newAudioId);
                    ps.setLong(2, newAudioId);
                }
                ps.setString(3, LocalDateTime.now().withNano(0).format(Fmt.DB_DT));
                ps.setLong(4, oldAudioId);
                return ps.executeUpdate();
            }
        });
    }
}
