package com.linkline.fam.audio.win;

import com.linkline.fam.audio.DuckJournal;
import com.linkline.fam.audio.SessionDucker;
import com.sun.jna.WString;
import com.sun.jna.ptr.FloatByReference;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.ptr.PointerByReference;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Lowers the volume of every other application's audio session while an announcement plays, using
 * IAudioSessionManager2 / IAudioSessionEnumerator / IAudioSessionControl2 / ISimpleAudioVolume.
 * Only per-application session volumes are changed. The Windows master volume is never touched and
 * nothing is muted.
 */
public final class WindowsSessionDucker implements SessionDucker {

    private static final Logger LOG = Logger.getLogger(WindowsSessionDucker.class.getName());

    /** Volume read back after ducking may differ by a hair; a bigger difference means the user moved it. */
    private static final float USER_CHANGE_TOLERANCE = 0.015f;
    private static final long RESCAN_INTERVAL_MS = 400;

    private final DuckJournal journal;
    private final long ownPid = ProcessHandle.current().pid();

    public WindowsSessionDucker(DuckJournal journal) {
        this.journal = journal;
    }

    @Override
    public boolean isSupported() {
        return true;
    }

    @Override
    public String description() {
        return "Windows Core Audio session volumes (per application)";
    }

    @Override
    public Handle begin(double factor) throws Exception {
        float f = (float) Math.max(0.0, Math.min(1.0, factor));
        Ducking ducking = new Ducking(f);
        ducking.rescan();
        ducking.startWatching();
        return ducking;
    }

    // ---- one ducking operation --------------------------------------------------------

    private record Captured(DuckJournal.Entry entry, ComPtr volume) {
    }

    private final class Ducking implements Handle {

        private final float factor;
        /** Only touched on the COM thread. */
        private final Map<String, Captured> captured = new LinkedHashMap<>();
        private boolean closed;
        private volatile int count;
        private ScheduledExecutorService watcher;

        Ducking(float factor) {
            this.factor = factor;
        }

        void rescan() throws Exception {
            ComThread.call(() -> {
                if (!closed) {
                    scanAllDevices();
                }
                return null;
            });
        }

        /** Applications can open (or start playing) during an announcement; duck those as well. */
        void startWatching() {
            watcher = Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "fam-duck-watch");
                t.setDaemon(true);
                return t;
            });
            watcher.scheduleWithFixedDelay(() -> {
                try {
                    rescan();
                } catch (Exception e) {
                    LOG.log(Level.FINE, "Re-scan of audio sessions failed", e);
                }
            }, RESCAN_INTERVAL_MS, RESCAN_INTERVAL_MS, TimeUnit.MILLISECONDS);
        }

        @Override
        public int sessionCount() {
            return count;
        }

        @Override
        public void restore() {
            if (watcher != null) {
                watcher.shutdownNow();
            }
            try {
                ComThread.call(() -> {
                    if (!closed) {
                        closed = true;
                        restoreCaptured();
                    }
                    return null;
                });
            } catch (Exception e) {
                // The journal still holds the original volumes, so the next start can finish the job.
                LOG.log(Level.SEVERE, "Restoring application volumes failed", e);
            }
        }

        private void scanAllDevices() {
            try (ComPtr enumerator = CoreAudio.createEnumerator();
                 ComPtr devices = enumerator.getInterface("EnumAudioEndpoints",
                         CoreAudio.ENUM_ENUM_AUDIO_ENDPOINTS, CoreAudio.E_RENDER, CoreAudio.DEVICE_STATE_ACTIVE)) {
                int n = CoreAudio.count(devices, CoreAudio.COLL_GET_COUNT);
                for (int i = 0; i < n; i++) {
                    try (ComPtr device = devices.getInterface("IMMDeviceCollection::Item", CoreAudio.COLL_ITEM, i)) {
                        scanDevice(device);
                    } catch (ComException e) {
                        LOG.log(Level.FINE, "Skipping an output device while ducking", e);
                    }
                }
            }
        }

        private void scanDevice(ComPtr device) {
            String deviceId = CoreAudio.deviceId(device);
            try (ComPtr manager = CoreAudio.activate(device, CoreAudio.IID_IAUDIO_SESSION_MANAGER2);
                 ComPtr sessions = manager.getInterface("GetSessionEnumerator", CoreAudio.MGR_GET_SESSION_ENUMERATOR)) {
                int n = CoreAudio.count(sessions, CoreAudio.SENUM_GET_COUNT);
                for (int i = 0; i < n; i++) {
                    // A session can disappear between enumeration and use; that is not an error.
                    try (ComPtr control = sessions.getInterface("GetSession", CoreAudio.SENUM_GET_SESSION, i)) {
                        duckSession(deviceId, control);
                    } catch (ComException e) {
                        LOG.log(Level.FINE, "Skipping an audio session while ducking", e);
                    }
                }
            }
        }

        private void duckSession(String deviceId, ComPtr control) {
            IntByReference state = new IntByReference();
            control.check("IAudioSessionControl::GetState", CoreAudio.CTL_GET_STATE, state);
            if (state.getValue() == CoreAudio.AUDIO_SESSION_STATE_EXPIRED) {
                return;
            }
            try (ComPtr control2 = control.queryInterface(CoreAudio.IID_IAUDIO_SESSION_CONTROL2)) {
                IntByReference pid = new IntByReference();
                control2.check("GetProcessId", CoreAudio.CTL2_GET_PROCESS_ID, pid);
                if (pid.getValue() == ownPid) {
                    return; // FAM's own audio session is never lowered
                }
                PointerByReference idRef = new PointerByReference();
                control2.check("GetSessionInstanceIdentifier", CoreAudio.CTL2_GET_SESSION_INSTANCE_ID, idRef);
                String instanceId = CoreAudio.takeString(idRef);
                if (instanceId == null) {
                    return;
                }
                String key = deviceId + "|" + instanceId;
                if (captured.containsKey(key)) {
                    return; // already lowered; never capture an already-lowered volume as "original"
                }

                ComPtr volume = control.queryInterface(CoreAudio.IID_ISIMPLE_AUDIO_VOLUME);
                boolean keep = false;
                try {
                    FloatByReference current = new FloatByReference();
                    volume.check("ISimpleAudioVolume::GetMasterVolume", CoreAudio.VOL_GET_MASTER, current);
                    float original = current.getValue();
                    float ducked = original * factor;
                    DuckJournal.Entry entry = new DuckJournal.Entry(key, deviceId, instanceId, original, ducked);
                    journal.save(entry); // written first, so a crash can still be repaired
                    volume.check("ISimpleAudioVolume::SetMasterVolume", CoreAudio.VOL_SET_MASTER,
                            Float.valueOf(ducked), null);
                    captured.put(key, new Captured(entry, volume));
                    count = captured.size();
                    keep = true;
                } finally {
                    if (!keep) {
                        volume.close();
                    }
                }
            }
        }

        private void restoreCaptured() {
            for (Captured c : captured.values()) {
                try {
                    FloatByReference current = new FloatByReference();
                    if (c.volume().invoke(CoreAudio.VOL_GET_MASTER, current) >= 0) {
                        restoreIfUntouched(c.volume(), current.getValue(), c.entry());
                    }
                } catch (RuntimeException e) {
                    LOG.log(Level.FINE, "Could not restore one session (it probably closed)", e);
                } finally {
                    c.volume().close();
                }
            }
            captured.clear();
            journal.clear();
        }
    }

    /** Sets the original volume back, unless the user changed the volume themselves in the meantime. */
    private static void restoreIfUntouched(ComPtr volume, float currentVolume, DuckJournal.Entry entry) {
        if (Math.abs(currentVolume - entry.ducked()) <= USER_CHANGE_TOLERANCE) {
            volume.invoke(CoreAudio.VOL_SET_MASTER, Float.valueOf(entry.original()), null);
        } else {
            LOG.info("Volume of a session was changed during the announcement; leaving the user's value");
        }
    }

    // ---- crash recovery ---------------------------------------------------------------

    @Override
    public void recoverFromJournal() {
        List<DuckJournal.Entry> entries = journal.load();
        if (entries.isEmpty()) {
            return;
        }
        LOG.warning("Found " + entries.size() + " ducked sessions from a previous run; restoring volumes");
        try {
            ComThread.call(() -> {
                restoreEntries(entries);
                return null;
            });
            journal.clear();
        } catch (Exception e) {
            LOG.log(Level.SEVERE, "Recovery of application volumes failed; will retry at next start", e);
        }
    }

    private void restoreEntries(List<DuckJournal.Entry> entries) {
        Map<String, DuckJournal.Entry> byKey = new HashMap<>();
        Map<String, Boolean> devices = new LinkedHashMap<>();
        for (DuckJournal.Entry e : entries) {
            byKey.put(e.key(), e);
            devices.put(e.deviceId(), Boolean.TRUE);
        }
        try (ComPtr enumerator = CoreAudio.createEnumerator()) {
            for (String deviceId : devices.keySet()) {
                try (ComPtr device = enumerator.getInterface("IMMDeviceEnumerator::GetDevice",
                        CoreAudio.ENUM_GET_DEVICE, new WString(deviceId));
                     ComPtr manager = CoreAudio.activate(device, CoreAudio.IID_IAUDIO_SESSION_MANAGER2);
                     ComPtr sessions = manager.getInterface("GetSessionEnumerator", CoreAudio.MGR_GET_SESSION_ENUMERATOR)) {
                    int n = CoreAudio.count(sessions, CoreAudio.SENUM_GET_COUNT);
                    for (int i = 0; i < n; i++) {
                        try (ComPtr control = sessions.getInterface("GetSession", CoreAudio.SENUM_GET_SESSION, i);
                             ComPtr control2 = control.queryInterface(CoreAudio.IID_IAUDIO_SESSION_CONTROL2)) {
                            PointerByReference idRef = new PointerByReference();
                            control2.check("GetSessionInstanceIdentifier", CoreAudio.CTL2_GET_SESSION_INSTANCE_ID, idRef);
                            String instanceId = CoreAudio.takeString(idRef);
                            DuckJournal.Entry entry = byKey.get(deviceId + "|" + instanceId);
                            if (entry == null) {
                                continue;
                            }
                            try (ComPtr volume = control.queryInterface(CoreAudio.IID_ISIMPLE_AUDIO_VOLUME)) {
                                FloatByReference current = new FloatByReference();
                                volume.check("GetMasterVolume", CoreAudio.VOL_GET_MASTER, current);
                                restoreIfUntouched(volume, current.getValue(), entry);
                            }
                        } catch (ComException e) {
                            LOG.log(Level.FINE, "Skipping a session during recovery", e);
                        }
                    }
                } catch (ComException e) {
                    LOG.log(Level.FINE, "Device unavailable during recovery: " + deviceId, e);
                }
            }
        }
    }
}
