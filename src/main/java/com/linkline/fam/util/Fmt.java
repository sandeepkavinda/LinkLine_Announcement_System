package com.linkline.fam.util;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** Display and storage formatting helpers. */
public final class Fmt {

    /** Storage format for date-times (local time, always with seconds). */
    public static final DateTimeFormatter DB_DT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss", Locale.ROOT);
    public static final DateTimeFormatter DB_D = DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.ROOT);
    public static final DateTimeFormatter DB_T = DateTimeFormatter.ofPattern("HH:mm", Locale.ROOT);

    private static final DateTimeFormatter T12 = DateTimeFormatter.ofPattern("hh:mm a", Locale.ENGLISH);
    private static final DateTimeFormatter T24 = DateTimeFormatter.ofPattern("HH:mm", Locale.ENGLISH);
    private static final DateTimeFormatter T12S = DateTimeFormatter.ofPattern("hh:mm:ss a", Locale.ENGLISH);
    private static final DateTimeFormatter T24S = DateTimeFormatter.ofPattern("HH:mm:ss", Locale.ENGLISH);
    private static final DateTimeFormatter DATE_LONG = DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy", Locale.ENGLISH);
    private static final DateTimeFormatter DATE_SHORT = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH);

    private static volatile boolean clock24;

    private Fmt() {
    }

    public static void setClock24(boolean value) {
        clock24 = value;
    }

    public static boolean isClock24() {
        return clock24;
    }

    public static String time(LocalTime t) {
        return t == null ? "" : t.format(clock24 ? T24 : T12);
    }

    public static String timeSeconds(LocalTime t) {
        return t == null ? "" : t.format(clock24 ? T24S : T12S);
    }

    public static String dateLong(LocalDate d) {
        return d == null ? "" : d.format(DATE_LONG);
    }

    public static String date(LocalDate d) {
        return d == null ? "" : d.format(DATE_SHORT);
    }

    public static String dateTime(LocalDateTime dt) {
        return dt == null ? "" : date(dt.toLocalDate()) + ", " + time(dt.toLocalTime());
    }

    public static String dateTimeSeconds(LocalDateTime dt) {
        return dt == null ? "" : date(dt.toLocalDate()) + ", " + timeSeconds(dt.toLocalTime());
    }

    public static String duration(long ms) {
        if (ms <= 0) {
            return "0:00";
        }
        long total = Math.round(ms / 1000.0);
        long h = total / 3600;
        long m = (total % 3600) / 60;
        long s = total % 60;
        return h > 0 ? String.format(Locale.ROOT, "%d:%02d:%02d", h, m, s) : String.format(Locale.ROOT, "%d:%02d", m, s);
    }

    public static String size(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        }
        double kb = bytes / 1024.0;
        if (kb < 1024) {
            return String.format(Locale.ROOT, "%.0f KB", kb);
        }
        double mb = kb / 1024.0;
        if (mb < 1024) {
            return String.format(Locale.ROOT, "%.1f MB", mb);
        }
        return String.format(Locale.ROOT, "%.2f GB", mb / 1024.0);
    }

    /** Countdown such as {@code 02:14:05} or {@code 1d 03:20:00}. */
    public static String countdown(Duration d) {
        long s = Math.max(0, d.getSeconds());
        long days = s / 86400;
        long h = (s % 86400) / 3600;
        long m = (s % 3600) / 60;
        long sec = s % 60;
        String hms = String.format(Locale.ROOT, "%02d:%02d:%02d", h, m, sec);
        return days > 0 ? days + "d " + hms : hms;
    }

    public static String stripExtension(String name) {
        int i = name.lastIndexOf('.');
        return i > 0 ? name.substring(0, i) : name;
    }

    public static String extension(String name) {
        int i = name.lastIndexOf('.');
        return i >= 0 && i < name.length() - 1 ? name.substring(i + 1).toLowerCase(Locale.ROOT) : "";
    }
}
