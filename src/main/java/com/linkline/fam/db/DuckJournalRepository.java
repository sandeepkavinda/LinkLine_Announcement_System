package com.linkline.fam.db;

import com.linkline.fam.audio.DuckJournal;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/** SQLite-backed {@link DuckJournal}. */
public final class DuckJournalRepository implements DuckJournal {

    private final Database db;

    public DuckJournalRepository(Database db) {
        this.db = db;
    }

    @Override
    public void save(Entry e) {
        db.exec(c -> {
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT OR REPLACE INTO duck_journal(session_key, device_id, instance_id, original, ducked) "
                            + "VALUES(?,?,?,?,?)")) {
                ps.setString(1, e.key());
                ps.setString(2, e.deviceId());
                ps.setString(3, e.instanceId());
                ps.setDouble(4, e.original());
                ps.setDouble(5, e.ducked());
                ps.executeUpdate();
            }
        });
    }

    @Override
    public List<Entry> load() {
        return db.query(c -> {
            List<Entry> list = new ArrayList<>();
            try (Statement st = c.createStatement();
                 ResultSet rs = st.executeQuery(
                         "SELECT session_key, device_id, instance_id, original, ducked FROM duck_journal")) {
                while (rs.next()) {
                    list.add(new Entry(rs.getString(1), rs.getString(2), rs.getString(3),
                            (float) rs.getDouble(4), (float) rs.getDouble(5)));
                }
            }
            return list;
        });
    }

    @Override
    public void clear() {
        db.exec(c -> {
            try (Statement st = c.createStatement()) {
                st.executeUpdate("DELETE FROM duck_journal");
            }
        });
    }
}
