-- FAM database schema, version 1. Statements are separated by semicolons at line ends.

CREATE TABLE audio_files (
    id            INTEGER PRIMARY KEY AUTOINCREMENT,
    display_name  TEXT    NOT NULL,
    original_name TEXT    NOT NULL,
    stored_name   TEXT    NOT NULL UNIQUE,
    format        TEXT    NOT NULL,
    duration_ms   INTEGER NOT NULL DEFAULT 0,
    size_bytes    INTEGER NOT NULL DEFAULT 0,
    imported_at   TEXT    NOT NULL
);

CREATE TABLE schedules (
    id             INTEGER PRIMARY KEY AUTOINCREMENT,
    name           TEXT    NOT NULL,
    description    TEXT    NOT NULL DEFAULT '',
    notes          TEXT    NOT NULL DEFAULT '',
    time_of_day    TEXT    NOT NULL,
    audio_id       INTEGER REFERENCES audio_files(id) ON DELETE RESTRICT,
    device_id      TEXT,
    device_name    TEXT,
    enabled        INTEGER NOT NULL DEFAULT 1,
    recurrence     TEXT    NOT NULL DEFAULT 'DAILY',
    days_mask      INTEGER NOT NULL DEFAULT 0,
    interval_n     INTEGER NOT NULL DEFAULT 1,
    interval_unit  TEXT    NOT NULL DEFAULT 'DAYS',
    start_date     TEXT    NOT NULL,
    end_date       TEXT,
    repeat_count   INTEGER NOT NULL DEFAULT 1,
    repeat_gap_sec INTEGER NOT NULL DEFAULT 5,
    created_at     TEXT    NOT NULL,
    updated_at     TEXT    NOT NULL
);

CREATE INDEX idx_schedules_audio ON schedules(audio_id);

CREATE TABLE history (
    id            INTEGER PRIMARY KEY AUTOINCREMENT,
    schedule_id   INTEGER REFERENCES schedules(id) ON DELETE SET NULL,
    schedule_name TEXT    NOT NULL,
    audio_name    TEXT    NOT NULL DEFAULT '',
    device_name   TEXT    NOT NULL DEFAULT '',
    planned_at    TEXT    NOT NULL,
    started_at    TEXT,
    finished_at   TEXT,
    status        TEXT    NOT NULL,
    trigger_type  TEXT    NOT NULL,
    message       TEXT    NOT NULL DEFAULT ''
);

CREATE INDEX idx_history_planned ON history(planned_at);

-- One row per scheduled occurrence: prevents accidental duplicate execution,
-- including after restarts and recovery runs.
CREATE UNIQUE INDEX ux_history_occurrence
    ON history(schedule_id, planned_at)
    WHERE schedule_id IS NOT NULL AND trigger_type IN ('SCHEDULED', 'RECOVERED');

CREATE TABLE settings (
    key   TEXT PRIMARY KEY,
    value TEXT NOT NULL
);

-- Original volumes of audio sessions that FAM has ducked. Lets FAM restore them
-- after a crash or forced shutdown in the middle of an announcement.
CREATE TABLE duck_journal (
    session_key TEXT PRIMARY KEY,
    device_id   TEXT NOT NULL,
    instance_id TEXT NOT NULL,
    original    REAL NOT NULL,
    ducked      REAL NOT NULL
);
