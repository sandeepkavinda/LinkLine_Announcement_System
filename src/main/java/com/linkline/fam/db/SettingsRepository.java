package com.linkline.fam.db;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.HashMap;
import java.util.Map;

public final class SettingsRepository {

    private final Database db;

    public SettingsRepository(Database db) {
        this.db = db;
    }

    public Map<String, String> loadAll() {
        return db.query(c -> {
            Map<String, String> map = new HashMap<>();
            try (PreparedStatement ps = c.prepareStatement("SELECT key, value FROM settings");
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    map.put(rs.getString(1), rs.getString(2));
                }
            }
            return map;
        });
    }

    public void put(String key, String value) {
        db.exec(c -> {
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO settings(key, value) VALUES(?, ?) "
                            + "ON CONFLICT(key) DO UPDATE SET value = excluded.value")) {
                ps.setString(1, key);
                ps.setString(2, value);
                ps.executeUpdate();
            }
        });
    }

    public void remove(String key) {
        db.exec(c -> {
            try (PreparedStatement ps = c.prepareStatement("DELETE FROM settings WHERE key = ?")) {
                ps.setString(1, key);
                ps.executeUpdate();
            }
        });
    }
}
