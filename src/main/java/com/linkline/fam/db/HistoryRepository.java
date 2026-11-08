package com.linkline.fam.db;

import com.linkline.fam.model.HistoryEntry;
import com.linkline.fam.model.PlayStatus;
import com.linkline.fam.model.TriggerType;
import com.linkline.fam.util.Fmt;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class HistoryRepository {

    private final Database db;

    public HistoryRepository(Database db) {
        this.db = db;
    }

    private static String str(LocalDateTime dt) {
        return dt == null ? null : dt.format(Fmt.DB_DT);
    }

    private static LocalDateTime dt(String s) {
        return s == null ? null : LocalDateTime.parse(s, Fmt.DB_DT);
    }

    private static HistoryEntry map(ResultSet rs) throws SQLException {
        long sid = rs.getLong("schedule_id");
        Long scheduleId = rs.wasNull() ? null : sid;
        return new HistoryEntry(
                rs.getLong("id"),
                scheduleId,
                rs.getString("schedule_name"),
                rs.getString("audio_name"),
                rs.getString("device_name"),
                dt(rs.getString("planned_at")),
                dt(rs.getString("started_at")),
                dt(rs.getString("finished_at")),
                PlayStatus.valueOf(rs.getString("status")),
                TriggerType.valueOf(rs.getString("trigger_type")),
                rs.getString("message"));
    }

    /**
     * Records a run. For scheduled runs the unique index guarantees that one occurrence is only ever
     * recorded once; in that case -1 is returned and the caller must not play it again.
     */
    public long claim(Long scheduleId, String scheduleName, String audioName, String deviceName,
                      LocalDateTime plannedAt, PlayStatus status, TriggerType trigger, String message) {
        return db.query(c -> {
            int changed;
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT OR IGNORE INTO history(schedule_id, schedule_name, audio_name, device_name, planned_at, "
                            + "status, trigger_type, message, started_at, finished_at) VALUES(?,?,?,?,?,?,?,?,?,?)")) {
                if (scheduleId == null) {
                    ps.setNull(1, Types.INTEGER);
                } else {
                    ps.setLong(1, scheduleId);
                }
                ps.setString(2, scheduleName);
                ps.setString(3, audioName == null ? "" : audioName);
                ps.setString(4, deviceName == null ? "" : deviceName);
                ps.setString(5, str(plannedAt));
                ps.setString(6, status.name());
                ps.setString(7, trigger.name());
                ps.setString(8, message == null ? "" : message);
                boolean finalState = status == PlayStatus.SKIPPED || status == PlayStatus.FAILED;
                ps.setString(9, null);
                ps.setString(10, finalState ? str(LocalDateTime.now()) : null);
                changed = ps.executeUpdate();
            }
            if (changed == 0) {
                return -1L;
            }
            try (Statement st = c.createStatement(); ResultSet rs = st.executeQuery("SELECT last_insert_rowid()")) {
                rs.next();
                return rs.getLong(1);
            }
        });
    }

    public void markPlaying(long id, String audioName, String deviceName) {
        db.exec(c -> {
            try (PreparedStatement ps = c.prepareStatement(
                    "UPDATE history SET status = ?, started_at = ?, audio_name = ?, device_name = ? WHERE id = ?")) {
                ps.setString(1, PlayStatus.PLAYING.name());
                ps.setString(2, str(LocalDateTime.now()));
                ps.setString(3, audioName == null ? "" : audioName);
                ps.setString(4, deviceName == null ? "" : deviceName);
                ps.setLong(5, id);
                ps.executeUpdate();
            }
        });
    }

    public void finish(long id, PlayStatus status, String message) {
        db.exec(c -> {
            try (PreparedStatement ps = c.prepareStatement(
                    "UPDATE history SET status = ?, finished_at = ?, message = ? WHERE id = ?")) {
                ps.setString(1, status.name());
                ps.setString(2, str(LocalDateTime.now()));
                ps.setString(3, message == null ? "" : message);
                ps.setLong(4, id);
                ps.executeUpdate();
            }
        });
    }

    public Optional<HistoryEntry> find(long id) {
        return db.query(c -> {
            try (PreparedStatement ps = c.prepareStatement("SELECT * FROM history WHERE id = ?")) {
                ps.setLong(1, id);
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next() ? Optional.of(map(rs)) : Optional.empty();
                }
            }
        });
    }

    /** Entries whose planned time falls on the given day, oldest first. */
    public List<HistoryEntry> findForDay(LocalDate day) {
        LocalDateTime from = day.atStartOfDay();
        return query("SELECT * FROM history WHERE planned_at >= ? AND planned_at < ? ORDER BY planned_at, id",
                from, from.plusDays(1));
    }

    /** Most recent first. Pass null for no lower or upper bound, and null status for all. */
    public List<HistoryEntry> search(LocalDateTime from, LocalDateTime to, PlayStatus status, String text, int limit) {
        return db.query(c -> {
            StringBuilder sql = new StringBuilder("SELECT * FROM history WHERE 1=1");
            List<String> args = new ArrayList<>();
            if (from != null) {
                sql.append(" AND planned_at >= ?");
                args.add(str(from));
            }
            if (to != null) {
                sql.append(" AND planned_at < ?");
                args.add(str(to));
            }
            if (status != null) {
                sql.append(" AND status = ?");
                args.add(status.name());
            }
            if (text != null && !text.isBlank()) {
                sql.append(" AND (schedule_name LIKE ? OR audio_name LIKE ? OR message LIKE ?)");
                String like = "%" + text.strip() + "%";
                args.add(like);
                args.add(like);
                args.add(like);
            }
            sql.append(" ORDER BY planned_at DESC, id DESC LIMIT ").append(Math.max(1, limit));
            List<HistoryEntry> list = new ArrayList<>();
            try (PreparedStatement ps = c.prepareStatement(sql.toString())) {
                for (int i = 0; i < args.size(); i++) {
                    ps.setString(i + 1, args.get(i));
                }
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        list.add(map(rs));
                    }
                }
            }
            return list;
        });
    }

    private List<HistoryEntry> query(String sql, LocalDateTime a, LocalDateTime b) {
        return db.query(c -> {
            List<HistoryEntry> list = new ArrayList<>();
            try (PreparedStatement ps = c.prepareStatement(sql)) {
                ps.setString(1, str(a));
                ps.setString(2, str(b));
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        list.add(map(rs));
                    }
                }
            }
            return list;
        });
    }

    /** After a crash or forced exit some runs are left as Queued/Playing; close them as failed. */
    public int failInterrupted() {
        return db.query(c -> {
            try (PreparedStatement ps = c.prepareStatement(
                    "UPDATE history SET status = 'FAILED', finished_at = ?, "
                            + "message = 'Interrupted: FAM was closed while this announcement was pending' "
                            + "WHERE status IN ('QUEUED', 'PLAYING')")) {
                ps.setString(1, str(LocalDateTime.now()));
                return ps.executeUpdate();
            }
        });
    }

    public int purgeOlderThan(LocalDateTime cutoff) {
        return db.query(c -> {
            try (PreparedStatement ps = c.prepareStatement("DELETE FROM history WHERE planned_at < ?")) {
                ps.setString(1, str(cutoff));
                return ps.executeUpdate();
            }
        });
    }

    public int clearAll() {
        return db.query(c -> {
            try (Statement st = c.createStatement()) {
                return st.executeUpdate("DELETE FROM history WHERE status NOT IN ('QUEUED', 'PLAYING')");
            }
        });
    }
}
