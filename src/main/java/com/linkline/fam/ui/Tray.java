package com.linkline.fam.ui;

import com.linkline.fam.AppInfo;
import java.awt.AWTException;
import java.awt.Dimension;
import java.awt.MenuItem;
import java.awt.PopupMenu;
import java.awt.SystemTray;
import java.awt.TrayIcon;
import java.util.logging.Level;
import java.util.logging.Logger;

/** System tray icon so FAM keeps announcing while its window is closed. */
public final class Tray {

    private static final Logger LOG = Logger.getLogger(Tray.class.getName());

    private TrayIcon icon;

    public static boolean isSupported() {
        return SystemTray.isSupported();
    }

    /** @return true if the tray icon was added */
    public boolean install(Runnable open, Runnable exit) {
        if (!SystemTray.isSupported()) {
            return false;
        }
        try {
            SystemTray tray = SystemTray.getSystemTray();
            Dimension size = tray.getTrayIconSize();
            icon = new TrayIcon(AppIcon.image(Math.max(16, size.width)), AppInfo.NAME + " is running");
            icon.setImageAutoSize(true);

            PopupMenu menu = new PopupMenu();
            MenuItem openItem = new MenuItem("Open " + AppInfo.SHORT_NAME);
            openItem.addActionListener(e -> open.run());
            MenuItem exitItem = new MenuItem("Exit");
            exitItem.addActionListener(e -> exit.run());
            menu.add(openItem);
            menu.addSeparator();
            menu.add(exitItem);
            icon.setPopupMenu(menu);
            icon.addActionListener(e -> open.run()); // double click
            tray.add(icon);
            return true;
        } catch (AWTException | RuntimeException e) {
            LOG.log(Level.WARNING, "Could not add the tray icon", e);
            icon = null;
            return false;
        }
    }

    public void message(String title, String text) {
        if (icon != null) {
            icon.displayMessage(title, text, TrayIcon.MessageType.INFO);
        }
    }

    public void remove() {
        if (icon != null && SystemTray.isSupported()) {
            SystemTray.getSystemTray().remove(icon);
            icon = null;
        }
    }
}
