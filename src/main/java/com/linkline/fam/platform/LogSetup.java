package com.linkline.fam.platform;

import com.linkline.fam.util.AppPaths;
import java.io.IOException;
import java.util.logging.FileHandler;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

/** Rotating log files in the data folder; useful when a factory PC misbehaves and nobody is watching. */
public final class LogSetup {

    private LogSetup() {
    }

    public static void init() {
        Logger root = Logger.getLogger("");
        for (Handler h : root.getHandlers()) {
            root.removeHandler(h);
        }
        System.setProperty("java.util.logging.SimpleFormatter.format",
                "%1$tF %1$tT %4$-7s %3$s - %5$s%6$s%n");
        try {
            FileHandler fh = new FileHandler(AppPaths.logsDir().resolve("fam-%g.log").toString(),
                    1_000_000, 5, true);
            fh.setFormatter(new SimpleFormatter());
            fh.setLevel(Level.INFO);
            root.addHandler(fh);
        } catch (IOException | SecurityException e) {
            Handler console = new java.util.logging.ConsoleHandler();
            console.setLevel(Level.INFO);
            root.addHandler(console);
            root.log(new LogRecord(Level.WARNING, "File logging unavailable: " + e.getMessage()));
        }
        root.setLevel(Level.INFO);
    }
}
