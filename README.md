# Link Line Embroidery FAM

Offline Windows desktop app (Java 17, Swing, FlatLaf, SQLite) that plays scheduled factory announcements
through a chosen audio device and lowers other applications' volume while they play.

## Run

    mvn compile exec:java -Dexec.mainClass=com.linkline.fam.FAM     (or run FAM.java from your IDE)
    java -jar target/FAM-app.jar                                    (after `mvn package`)

Start minimized to the tray: add `--minimized`.

## Package

`package.bat` builds `dist\FAM\FAM.exe`; `package.bat installer` builds a Windows installer (needs WiX 3).
"Start with Windows" only works from the packaged FAM.exe.

## Data

`%APPDATA%\FAM` (database, imported audio, logs). Nothing leaves the computer.

## Layout

- `service/` scheduler, announcement queue, audio library, devices, settings, event bus
- `audio/` playback, device catalog, session ducking (`audio/win` = Windows Core Audio through JNA)
- `db/` SQLite repositories and schema
- `ui/` Swing UI: `MainFrame`, `pages/`, `dialogs/`, `components/`
