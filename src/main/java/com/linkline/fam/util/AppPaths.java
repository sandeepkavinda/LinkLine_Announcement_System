package com.linkline.fam.util;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Locations of FAM's local data. Everything lives under one folder, nothing is online. */
public final class AppPaths {

    private static volatile Path base;

    private AppPaths() {
    }

    public static Path dataDir() {
        Path b = base;
        if (b == null) {
            synchronized (AppPaths.class) {
                if (base == null) {
                    String override = System.getProperty("fam.data");
                    String appData = System.getenv("APPDATA");
                    if (override != null && !override.isBlank()) {
                        base = Path.of(override);
                    } else if (appData != null && !appData.isBlank()) {
                        base = Path.of(appData, "FAM");
                    } else {
                        base = Path.of(System.getProperty("user.home"), ".fam");
                    }
                }
                b = base;
            }
        }
        return b;
    }

    public static Path audioDir() {
        return dataDir().resolve("audio");
    }

    public static Path logsDir() {
        return dataDir().resolve("logs");
    }

    public static Path databaseFile() {
        return dataDir().resolve("fam.db");
    }

    public static Path lockFile() {
        return dataDir().resolve("fam.lock");
    }

    public static void ensureDirectories() throws IOException {
        Files.createDirectories(dataDir());
        Files.createDirectories(audioDir());
        Files.createDirectories(logsDir());
    }
}
