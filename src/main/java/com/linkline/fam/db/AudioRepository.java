package com.linkline.fam.db;

import com.linkline.fam.model.AudioItem;
import com.linkline.fam.util.Fmt;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class AudioRepository {

    private static final String SELECT =
            "SELECT a.id, a.display_name, a.original_name, a.stored_name, a.format, a.duration_ms, "
                    + "a.size_bytes, a.imported_at, "
                    + "(SELECT COUNT(*) FROM schedules s WHERE s.audio_id = a.id) AS assigned "
                    + "FROM audio_files a ";

    private final Database db;

    public AudioRepository(Database db) {
        this.db = db;
    }

    private static AudioItem map(ResultSet rs) throws SQLException {
        return new AudioItem(
                rs.getLong("id"),
                rs.getString("display_name"),
                rs.getString("original_name"),
                rs.getString("stored_name"),
                rs.getString("format"),
                rs.getLong("duration_ms"),
                rs.getLong("size_bytes"),
                LocalDateTime.parse(rs.getString("imported_at"), Fmt.DB_DT),
                rs.getInt("assigned"));
    }

    public List<AudioItem> findAll() {
        return db.query(c -> {
            List<AudioItem> list = new ArrayList<>();
            try (Statement st = c.createStatement();
                 ResultSet rs = st.executeQuery(SELECT + "ORDER BY a.imported_at DESC, a.id DESC")) {
                while (rs.next()) {
                    list.add(map(rs));
                }
            }
            return list;
        });
    }

    public Optional<AudioItem> find(long id) {
        return db.query(c -> {
            try (PreparedStatement ps = c.prepareStatement(SELECT + "WHERE a.id = ?")) {
                ps.setLong(1, id);
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next() ? Optional.of(map(rs)) : Optional.empty();
                }
            }
        });
    }

    public long insert(AudioItem item) {
        return db.query(c -> {
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO audio_files(display_name, original_name, stored_name, format, duration_ms, "
                            + "size_bytes, imported_at) VALUES(?,?,?,?,?,?,?)")) {
                ps.setString(1, item.displayName());
                ps.setString(2, item.originalName());
                ps.setString(3, item.storedName());
                ps.setString(4, item.format());
                ps.setLong(5, item.durationMs());
                ps.setLong(6, item.sizeBytes());
                ps.setString(7, item.importedAt().format(Fmt.DB_DT));
                ps.executeUpdate();
            }
            try (Statement st = c.createStatement(); ResultSet rs = st.executeQuery("SELECT last_insert_rowid()")) {
                rs.next();
                return rs.getLong(1);
            }
        });
    }

    public void rename(long id, String displayName) {
        db.exec(c -> {
            try (PreparedStatement ps = c.prepareStatement("UPDATE audio_files SET display_name = ? WHERE id = ?")) {
                ps.setString(1, displayName);
                ps.setLong(2, id);
                ps.executeUpdate();
            }
        });
    }

    public void delete(long id) {
        db.exec(c -> {
            try (PreparedStatement ps = c.prepareStatement("DELETE FROM audio_files WHERE id = ?")) {
                ps.setLong(1, id);
                ps.executeUpdate();
            }
        });
    }
}
