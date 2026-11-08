package com.linkline.fam.db;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Single shared SQLite connection. Access is serialized, which is plenty for a local
 * desktop application and keeps SQLite's locking behaviour simple.
 */
public final class Database implements AutoCloseable {

    private static final Logger LOG = Logger.getLogger(Database.class.getName());
    private static final int SCHEMA_VERSION = 1;

    @FunctionalInterface
    public interface SqlFunction<T> {
        T apply(Connection c) throws SQLException;
    }

    @FunctionalInterface
    public interface SqlAction {
        void run(Connection c) throws SQLException;
    }

    private final Connection conn;
    private final Path file;

    public Database(Path file) {
        this.file = file;
        try {
            conn = DriverManager.getConnection("jdbc:sqlite:" + file.toAbsolutePath());
            try (Statement st = conn.createStatement()) {
                st.execute("PRAGMA foreign_keys = ON");
                st.execute("PRAGMA busy_timeout = 5000");
                st.execute("PRAGMA journal_mode = WAL");
                st.execute("PRAGMA synchronous = NORMAL");
            }
            migrate();
        } catch (SQLException e) {
            throw new DataAccessException("Cannot open the FAM database at " + file, e);
        }
    }

    public Path file() {
        return file;
    }

    private void migrate() throws SQLException {
        int version;
        try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery("PRAGMA user_version")) {
            version = rs.next() ? rs.getInt(1) : 0;
        }
        if (version < 1) {
            LOG.info("Creating database schema");
            conn.setAutoCommit(false);
            try (Statement st = conn.createStatement()) {
                for (String sql : loadStatements("/db/schema.sql")) {
                    st.execute(sql);
                }
                st.execute("PRAGMA user_version = " + SCHEMA_VERSION);
                conn.commit();
            } catch (SQLException | RuntimeException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        }
    }

    private static List<String> loadStatements(String resource) {
        try (InputStream in = Database.class.getResourceAsStream(resource)) {
            if (in == null) {
                throw new IllegalStateException("Missing resource " + resource);
            }
            StringBuilder sb = new StringBuilder();
            try (BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                String line;
                while ((line = r.readLine()) != null) {
                    if (!line.strip().startsWith("--")) {
                        sb.append(line).append('\n');
                    }
                }
            }
            List<String> out = new ArrayList<>();
            for (String part : sb.toString().split(";\\s*\\n")) {
                String s = part.strip();
                if (s.endsWith(";")) {
                    s = s.substring(0, s.length() - 1);
                }
                if (!s.isEmpty()) {
                    out.add(s);
                }
            }
            return out;
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read " + resource, e);
        }
    }

    public synchronized <T> T query(SqlFunction<T> fn) {
        try {
            return fn.apply(conn);
        } catch (SQLException e) {
            throw new DataAccessException("Database error: " + e.getMessage(), e);
        }
    }

    public synchronized void exec(SqlAction action) {
        query(c -> {
            action.run(c);
            return null;
        });
    }

    /** Runs the work in one transaction; rolls back on any failure. */
    public synchronized <T> T transaction(SqlFunction<T> fn) {
        try {
            conn.setAutoCommit(false);
            try {
                T result = fn.apply(conn);
                conn.commit();
                return result;
            } catch (SQLException | RuntimeException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (SQLException e) {
            throw new DataAccessException("Database error: " + e.getMessage(), e);
        }
    }

    /** Quick health probe for the dashboard. */
    public boolean isHealthy() {
        try {
            return query(c -> {
                try (Statement st = c.createStatement(); ResultSet rs = st.executeQuery("SELECT 1")) {
                    return rs.next();
                }
            });
        } catch (RuntimeException e) {
            LOG.log(Level.WARNING, "Database health check failed", e);
            return false;
        }
    }

    @Override
    public synchronized void close() {
        try {
            conn.close();
        } catch (SQLException e) {
            LOG.log(Level.WARNING, "Closing database failed", e);
        }
    }
}
