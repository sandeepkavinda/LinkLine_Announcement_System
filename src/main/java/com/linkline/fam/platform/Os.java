package com.linkline.fam.platform;

import java.util.Locale;

public final class Os {

    private static final boolean WINDOWS =
            System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");

    private Os() {
    }

    public static boolean isWindows() {
        return WINDOWS;
    }
}
