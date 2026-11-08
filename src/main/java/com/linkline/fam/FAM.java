package com.linkline.fam;

import com.linkline.fam.platform.LogSetup;
import com.linkline.fam.platform.SingleInstance;
import com.linkline.fam.service.SettingsService;
import com.linkline.fam.ui.Dialogs;
import com.linkline.fam.ui.MainFrame;
import com.linkline.fam.ui.Theme;
import com.linkline.fam.ui.Tray;
import com.linkline.fam.util.AppPaths;
import java.awt.Frame;
import java.io.IOException;
import java.util.Arrays;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.SwingUtilities;

/** Factory Announcement Manager: entry point. */
public final class FAM {

    private static final Logger LOG = Logger.getLogger(FAM.class.getName());

    private static AppContext context;
    private static MainFrame frame;
    private static final Tray TRAY = new Tray();
    private static final SingleInstance INSTANCE = new SingleInstance();
    private static boolean exiting;

    private FAM() {
    }

    public static void main(String[] args) {
        boolean minimized = Arrays.asList(args).contains("--minimized");
        try {
            AppPaths.ensureDirectories();
        } catch (IOException e) {
            SwingUtilities.invokeLater(() -> {
                Theme.install();
                Dialogs.error(null, "FAM cannot start",
                        "FAM could not create its data folder:\n" + AppPaths.dataDir() + "\n\n" + e.getMessage());
                System.exit(1);
            });
            return;
        }
        LogSetup.init();
        Thread.setDefaultUncaughtExceptionHandler((t, e) -> LOG.log(Level.SEVERE, "Uncaught error in " + t.getName(), e));

        if (!INSTANCE.acquire(AppPaths.lockFile())) {
            SwingUtilities.invokeLater(() -> {
                Theme.install();
                Dialogs.info(null, AppInfo.NAME, "FAM is already running on this computer.\n"
                        + "Look for its icon in the system tray, next to the clock.");
                System.exit(0);
            });
            return;
        }
        SwingUtilities.invokeLater(() -> start(minimized));
    }

    private static void start(boolean minimized) {
        Theme.install();
        try {
            context = new AppContext();
        } catch (RuntimeException e) {
            LOG.log(Level.SEVERE, "Startup failed", e);
            Dialogs.error(null, "FAM cannot start",
                    "FAM could not open its database.\n\n" + e.getMessage()
                            + "\n\nDetails are in the log folder:\n" + AppPaths.logsDir());
            INSTANCE.release();
            System.exit(1);
            return;
        }
        frame = new MainFrame(context, FAM::requestExit, FAM::closeWindow);
        boolean trayOk = TRAY.install(FAM::showWindow, FAM::requestExit);

        // Make sure volumes are restored and the database is closed even if Windows is shutting down.
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            if (context != null) {
                context.close();
            }
        }, "fam-shutdown"));

        context.start();

        if (minimized) {
            if (trayOk) {
                frame.setVisible(false);
            } else {
                frame.setExtendedState(Frame.ICONIFIED);
                frame.setVisible(true);
            }
        } else {
            frame.setVisible(true);
        }
        LOG.info(AppInfo.NAME + " " + AppInfo.VERSION + " started");
    }

    private static void showWindow() {
        SwingUtilities.invokeLater(() -> {
            frame.setVisible(true);
            frame.setExtendedState(Frame.NORMAL);
            frame.toFront();
            frame.requestFocus();
        });
    }

    /** The window's close button: hide to the tray (announcements keep running) or exit. */
    private static void closeWindow() {
        SettingsService settings = context.settings;
        if (settings.minimizeToTray() && Tray.isSupported()) {
            frame.setVisible(false);
            if (!settings.getBool(SettingsService.TRAY_HINT_SHOWN, false)) {
                settings.setQuietly(SettingsService.TRAY_HINT_SHOWN, "true");
                TRAY.message(AppInfo.NAME, "FAM is still running and will play announcements on schedule. "
                        + "Right-click this icon to open or exit.");
            }
        } else {
            requestExit();
        }
    }

    /** Asks for confirmation when needed, then shuts everything down in order. */
    private static void requestExit() {
        SwingUtilities.invokeLater(() -> {
            if (exiting) {
                return;
            }
            boolean playing = context.announcements.isPlaying();
            String message = playing
                    ? "An announcement is playing right now. Exit anyway?\nOther applications' volumes will be restored."
                    : "Exit FAM?\nScheduled announcements will not play while FAM is closed.";
            frame.setVisible(true);
            if (!Dialogs.confirm(frame, "Exit FAM", message, "Exit", true)) {
                return;
            }
            exiting = true;
            frame.setVisible(false);
            TRAY.remove();
            frame.shutdown();
            Thread closer = new Thread(() -> {
                try {
                    context.close(); // stops the scheduler, restores volumes, closes the database
                } finally {
                    INSTANCE.release();
                    System.exit(0);
                }
            }, "fam-exit");
            closer.start();
        });
    }
}
