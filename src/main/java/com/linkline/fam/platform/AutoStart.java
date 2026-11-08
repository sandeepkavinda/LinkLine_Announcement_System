package com.linkline.fam.platform;

import com.sun.jna.platform.win32.Advapi32Util;
import com.sun.jna.platform.win32.WinReg;
import java.util.Locale;
import java.util.Optional;

/**
 * "Start FAM when I sign in to Windows", using the per-user Run registry key (no administrator rights).
 * Only available when FAM runs from its installed launcher (FAM.exe), not from a plain {@code java} run.
 */
public final class AutoStart {

    private static final String RUN_KEY = "Software\\Microsoft\\Windows\\CurrentVersion\\Run";
    private static final String VALUE_NAME = "FactoryAnnouncementManager";

    private AutoStart() {
    }

    /** Path of the running launcher, if FAM was started from an installed executable. */
    public static Optional<String> launcher() {
        if (!Os.isWindows()) {
            return Optional.empty();
        }
        return ProcessHandle.current().info().command()
                .filter(cmd -> {
                    String lower = cmd.toLowerCase(Locale.ROOT);
                    return lower.endsWith(".exe") && !lower.endsWith("\\java.exe") && !lower.endsWith("\\javaw.exe");
                });
    }

    public static boolean isSupported() {
        return launcher().isPresent();
    }

    public static boolean isEnabled() {
        if (!Os.isWindows()) {
            return false;
        }
        return Advapi32Util.registryValueExists(WinReg.HKEY_CURRENT_USER, RUN_KEY, VALUE_NAME);
    }

    public static void set(boolean enabled) {
        if (!Os.isWindows()) {
            throw new IllegalStateException("Start with Windows is only available on Windows.");
        }
        if (enabled) {
            String exe = launcher().orElseThrow(() -> new IllegalStateException(
                    "Start with Windows needs the installed FAM program (FAM.exe); it is not available when "
                            + "FAM is started with java directly."));
            Advapi32Util.registrySetStringValue(WinReg.HKEY_CURRENT_USER, RUN_KEY, VALUE_NAME,
                    "\"" + exe + "\" --minimized");
        } else if (Advapi32Util.registryValueExists(WinReg.HKEY_CURRENT_USER, RUN_KEY, VALUE_NAME)) {
            Advapi32Util.registryDeleteValue(WinReg.HKEY_CURRENT_USER, RUN_KEY, VALUE_NAME);
        }
    }
}
