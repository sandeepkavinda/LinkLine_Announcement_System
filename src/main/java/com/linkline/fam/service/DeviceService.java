package com.linkline.fam.service;

import com.linkline.fam.audio.AudioDevice;
import com.linkline.fam.audio.DeviceCatalog;
import com.linkline.fam.audio.JavaSoundDeviceCatalog;
import com.linkline.fam.audio.JavaSoundSupport;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.sound.sampled.Mixer;

/**
 * Knows which output devices exist, which one the user picked for announcements, and whether it is
 * connected. The list is refreshed in the background so unplugging a device is noticed.
 */
public final class DeviceService {

    private static final Logger LOG = Logger.getLogger(DeviceService.class.getName());

    /** Where one announcement will actually be played. */
    public record Target(AudioDevice device, Mixer.Info mixer, String label, String warning) {
    }

    public enum Health { OK, WARNING, ERROR }

    public record SelectionStatus(Health health, String text) {
    }

    private final DeviceCatalog catalog;
    private final DeviceCatalog fallbackCatalog = new JavaSoundDeviceCatalog();
    private final SettingsService settings;
    private final EventBus bus;
    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "fam-device-monitor");
        t.setDaemon(true);
        return t;
    });

    private volatile List<AudioDevice> devices = List.of();
    private volatile String lastError;

    public DeviceService(DeviceCatalog catalog, SettingsService settings, EventBus bus) {
        this.catalog = catalog;
        this.settings = settings;
        this.bus = bus;
    }

    public void start() {
        executor.scheduleWithFixedDelay(this::refreshQuietly, 0, 5, TimeUnit.SECONDS);
    }

    public void shutdown() {
        executor.shutdownNow();
    }

    /** Re-reads the device list now (blocking). */
    public void refresh() {
        List<AudioDevice> fresh;
        try {
            fresh = catalog.listOutputs();
            lastError = null;
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Listing audio devices failed", e);
            lastError = e.getMessage();
            try {
                fresh = fallbackCatalog.listOutputs();
            } catch (Exception ex) {
                fresh = List.of();
            }
        }
        if (!Objects.equals(fresh, devices)) {
            devices = List.copyOf(fresh);
            bus.publish(EventBus.Topic.DEVICES);
        }
    }

    private void refreshQuietly() {
        try {
            refresh();
        } catch (RuntimeException e) {
            LOG.log(Level.WARNING, "Device refresh failed", e);
        }
    }

    public List<AudioDevice> devices() {
        return devices;
    }

    public String lastError() {
        return lastError;
    }

    public String catalogDescription() {
        return lastError == null ? catalog.description() : fallbackCatalog.description() + " (Windows API unavailable)";
    }

    public Optional<AudioDevice> defaultDevice() {
        return devices.stream().filter(d -> d.isDefault() && d.isUsable()).findFirst();
    }

    public Optional<AudioDevice> find(String id, String name) {
        if (id != null) {
            for (AudioDevice d : devices) {
                if (d.id().equals(id)) {
                    return Optional.of(d);
                }
            }
        }
        if (name != null) {
            for (AudioDevice d : devices) {
                if (d.name().equalsIgnoreCase(name)) {
                    return Optional.of(d);
                }
            }
        }
        return Optional.empty();
    }

    /** The device chosen for announcements, if one was chosen. */
    public Optional<AudioDevice> selectedDevice() {
        return settings.deviceId() == null ? Optional.empty() : find(settings.deviceId(), settings.deviceName());
    }

    public SelectionStatus selectionStatus() {
        if (devices.isEmpty()) {
            return new SelectionStatus(Health.ERROR, "No audio output devices found");
        }
        if (settings.deviceId() == null) {
            return defaultDevice()
                    .map(d -> new SelectionStatus(Health.OK, "Windows default: " + d.name()))
                    .orElse(new SelectionStatus(Health.ERROR, "No active default output device"));
        }
        Optional<AudioDevice> sel = selectedDevice();
        String shown = settings.deviceName() != null ? settings.deviceName() : settings.deviceId();
        if (sel.isEmpty()) {
            return new SelectionStatus(Health.ERROR, shown + " is not available");
        }
        if (!sel.get().isUsable()) {
            return new SelectionStatus(Health.ERROR, shown + " is " + sel.get().state().label().toLowerCase());
        }
        return new SelectionStatus(Health.OK, sel.get().name());
    }

    /** Display name of where announcements will go right now. */
    public String announcementOutputName() {
        if (settings.deviceId() == null) {
            return defaultDevice().map(d -> d.name() + " (Windows default)").orElse("Windows default output");
        }
        return settings.deviceName() != null ? settings.deviceName() : settings.deviceId();
    }

    /**
     * Decides where an announcement is played. A schedule may carry its own device; otherwise the
     * device selected in FAM is used; with no selection the Windows default is used.
     * The Windows default output device itself is never changed.
     */
    public Target resolve(String overrideId, String overrideName) throws DeviceUnavailableException {
        String id = overrideId != null && !overrideId.isBlank() ? overrideId : settings.deviceId();
        String name = overrideId != null && !overrideId.isBlank() ? overrideName : settings.deviceName();
        boolean fallbackAllowed = settings.fallbackMode() == SettingsService.FallbackMode.DEFAULT_DEVICE;

        if (id == null) {
            return defaultTarget(null);
        }
        String shown = name != null ? name : id;
        Optional<AudioDevice> found = find(id, name);
        if (found.isPresent() && found.get().isUsable()) {
            Optional<Mixer.Info> mixer = JavaSoundSupport.findMixer(found.get().name());
            if (mixer.isPresent()) {
                return new Target(found.get(), mixer.get(), found.get().name(), null);
            }
            String problem = "Output device '" + shown + "' is connected but could not be opened for playback";
            if (fallbackAllowed) {
                return defaultTarget(problem + "; played on the Windows default device instead");
            }
            throw new DeviceUnavailableException(problem);
        }
        String problem = "Output device '" + shown + "' is not connected";
        if (fallbackAllowed) {
            return defaultTarget(problem + "; played on the Windows default device instead");
        }
        throw new DeviceUnavailableException(problem);
    }

    private Target defaultTarget(String warning) {
        AudioDevice def = defaultDevice().orElse(null);
        String label = def == null ? "System default output" : def.name() + " (Windows default)";
        return new Target(def, null, label, warning);
    }
}
