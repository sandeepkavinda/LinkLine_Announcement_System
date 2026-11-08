package com.linkline.fam.service;

import com.linkline.fam.db.SettingsRepository;
import com.linkline.fam.util.Fmt;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Typed access to the settings table with an in-memory cache. */
public final class SettingsService {

    /** What to do with announcements that were due while FAM was not running. */
    public enum MissedPolicy {
        SKIP("Skip missed announcements"),
        GRACE("Play if the delay is within the grace period"),
        TODAY("Play every announcement missed today");

        private final String label;

        MissedPolicy(String label) {
            this.label = label;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    /** What to do when the chosen output device is disconnected at announcement time. */
    public enum FallbackMode {
        FAIL("Do not play (record a failure)"),
        DEFAULT_DEVICE("Play through the Windows default device");

        private final String label;

        FallbackMode(String label) {
            this.label = label;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    public static final String DEVICE_ID = "device.id";
    public static final String DEVICE_NAME = "device.name";
    public static final String DEVICE_FALLBACK = "device.fallback";
    public static final String DUCK_ENABLED = "duck.enabled";
    public static final String DUCK_PERCENT = "duck.percent";
    public static final String MISSED_POLICY = "missed.policy";
    public static final String MISSED_GRACE_MIN = "missed.graceMinutes";
    public static final String CLOCK_24H = "ui.clock24";
    public static final String MINIMIZE_TO_TRAY = "app.minimizeToTray";
    public static final String AUTOSTART = "app.autostart";
    public static final String RETENTION_DAYS = "history.retentionDays";
    public static final String LAST_CHECK = "scheduler.lastCheck";
    public static final String TRAY_HINT_SHOWN = "app.trayHintShown";

    private final SettingsRepository repo;
    private final EventBus bus;
    private final Map<String, String> cache = new ConcurrentHashMap<>();

    public SettingsService(SettingsRepository repo, EventBus bus) {
        this.repo = repo;
        this.bus = bus;
        cache.putAll(repo.loadAll());
        Fmt.setClock24(clock24());
    }

    public String get(String key, String def) {
        return cache.getOrDefault(key, def);
    }

    public boolean getBool(String key, boolean def) {
        String v = cache.get(key);
        return v == null ? def : Boolean.parseBoolean(v);
    }

    public int getInt(String key, int def) {
        String v = cache.get(key);
        if (v == null) {
            return def;
        }
        try {
            return Integer.parseInt(v.strip());
        } catch (NumberFormatException e) {
            return def;
        }
    }

    public void set(String key, String value) {
        setQuietly(key, value);
        if (CLOCK_24H.equals(key)) {
            Fmt.setClock24(clock24());
        }
        bus.publish(EventBus.Topic.SETTINGS);
    }

    /** Stores a value without notifying the UI (used for internal bookkeeping). */
    public void setQuietly(String key, String value) {
        if (value == null) {
            cache.remove(key);
            repo.remove(key);
        } else {
            cache.put(key, value);
            repo.put(key, value);
        }
    }

    public void setBool(String key, boolean value) {
        set(key, Boolean.toString(value));
    }

    public void setInt(String key, int value) {
        set(key, Integer.toString(value));
    }

    // ---- typed accessors --------------------------------------------------------------

    public String deviceId() {
        String v = get(DEVICE_ID, "");
        return v.isBlank() ? null : v;
    }

    public String deviceName() {
        String v = get(DEVICE_NAME, "");
        return v.isBlank() ? null : v;
    }

    public void setDevice(String id, String name) {
        setQuietly(DEVICE_ID, id);
        setQuietly(DEVICE_NAME, name);
        bus.publish(EventBus.Topic.SETTINGS);
        bus.publish(EventBus.Topic.DEVICES);
    }

    public FallbackMode fallbackMode() {
        try {
            return FallbackMode.valueOf(get(DEVICE_FALLBACK, FallbackMode.FAIL.name()));
        } catch (IllegalArgumentException e) {
            return FallbackMode.FAIL;
        }
    }

    public boolean duckEnabled() {
        return getBool(DUCK_ENABLED, true);
    }

    /** Percentage of the original volume that other applications are lowered to (default 50). */
    public int duckPercent() {
        return Math.max(5, Math.min(95, getInt(DUCK_PERCENT, 50)));
    }

    public MissedPolicy missedPolicy() {
        try {
            return MissedPolicy.valueOf(get(MISSED_POLICY, MissedPolicy.GRACE.name()));
        } catch (IllegalArgumentException e) {
            return MissedPolicy.GRACE;
        }
    }

    public int graceMinutes() {
        return Math.max(1, Math.min(720, getInt(MISSED_GRACE_MIN, 10)));
    }

    public boolean clock24() {
        return getBool(CLOCK_24H, false);
    }

    public boolean minimizeToTray() {
        return getBool(MINIMIZE_TO_TRAY, true);
    }

    public int retentionDays() {
        return Math.max(7, getInt(RETENTION_DAYS, 365));
    }
}
