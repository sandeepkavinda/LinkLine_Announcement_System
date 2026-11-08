package com.linkline.fam.service;

import com.linkline.fam.audio.AudioFiles;
import com.linkline.fam.db.AudioRepository;
import com.linkline.fam.db.ScheduleRepository;
import com.linkline.fam.model.AudioItem;
import com.linkline.fam.model.Schedule;
import com.linkline.fam.util.AppPaths;
import com.linkline.fam.util.Fmt;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Imports audio into FAM's own storage and manages the library. */
public final class AudioLibraryService {

    private static final Logger LOG = Logger.getLogger(AudioLibraryService.class.getName());

    public record ImportResult(List<AudioItem> imported, List<String> errors) {
    }

    /** Thrown when an audio file is still assigned to schedules. */
    public static final class AudioInUseException extends Exception {
        private final transient List<Schedule> schedules;

        public AudioInUseException(List<Schedule> schedules) {
            super("This audio file is assigned to " + schedules.size() + " schedule(s).");
            this.schedules = schedules;
        }

        public List<Schedule> schedules() {
            return schedules;
        }
    }

    private final AudioRepository audio;
    private final ScheduleRepository schedules;
    private final EventBus bus;

    public AudioLibraryService(AudioRepository audio, ScheduleRepository schedules, EventBus bus) {
        this.audio = audio;
        this.schedules = schedules;
        this.bus = bus;
    }

    public List<AudioItem> list() {
        return audio.findAll();
    }

    public Optional<AudioItem> find(long id) {
        return audio.find(id);
    }

    public Path fileOf(AudioItem item) {
        return AppPaths.audioDir().resolve(item.storedName());
    }

    public boolean fileExists(AudioItem item) {
        return Files.isRegularFile(fileOf(item));
    }

    /** Validates and copies each file into FAM's data folder. Failures are reported per file. */
    public ImportResult importFiles(List<Path> sources) {
        List<AudioItem> imported = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        for (Path src : sources) {
            String fileName = src.getFileName().toString();
            try {
                imported.add(importOne(src));
            } catch (IOException e) {
                LOG.log(Level.INFO, "Import failed for " + src, e);
                errors.add(fileName + ": " + e.getMessage());
            } catch (RuntimeException e) {
                LOG.log(Level.WARNING, "Import failed for " + src, e);
                errors.add(fileName + ": unexpected error (" + e.getMessage() + ")");
            }
        }
        if (!imported.isEmpty()) {
            bus.publish(EventBus.Topic.AUDIO);
        }
        return new ImportResult(imported, errors);
    }

    private AudioItem importOne(Path src) throws IOException {
        String fileName = src.getFileName().toString();
        if (!Files.isRegularFile(src)) {
            throw new IOException("The file could not be found.");
        }
        long size = Files.size(src);
        if (size == 0) {
            throw new IOException("The file is empty.");
        }
        String ext = Fmt.extension(fileName);
        if (!AudioFiles.isSupportedExtension(ext)) {
            throw new IOException("Unsupported file type '." + ext + "'. Supported: "
                    + String.join(", ", AudioFiles.SUPPORTED_EXTENSIONS) + ".");
        }
        AudioFiles.Probe probe = AudioFiles.probe(src);

        String storedName = UUID.randomUUID() + "." + ext;
        Path target = AppPaths.audioDir().resolve(storedName);
        Files.createDirectories(target.getParent());
        Files.copy(src, target, StandardCopyOption.COPY_ATTRIBUTES);
        try {
            AudioItem draft = new AudioItem(0, Fmt.stripExtension(fileName), fileName, storedName,
                    ext.toUpperCase(Locale.ROOT), probe.durationMs(), size, LocalDateTime.now().withNano(0), 0);
            long id = audio.insert(draft);
            return audio.find(id).orElseThrow(() -> new IOException("The file was saved but could not be read back."));
        } catch (RuntimeException e) {
            Files.deleteIfExists(target); // do not leave an orphan copy behind
            throw e;
        }
    }

    public void rename(long id, String newName) {
        String name = newName == null ? "" : newName.strip();
        if (name.isEmpty()) {
            throw new IllegalArgumentException("The name cannot be empty.");
        }
        audio.rename(id, name);
        bus.publish(EventBus.Topic.AUDIO);
        bus.publish(EventBus.Topic.SCHEDULES);
    }

    /**
     * Deletes the file and its database record.
     *
     * @throws AudioInUseException if any schedule still uses it; the caller must reassign or clear
     *                             those references first (see {@link #reassignAndDelete})
     */
    public void delete(AudioItem item) throws AudioInUseException {
        List<Schedule> using = schedules.findByAudio(item.id());
        if (!using.isEmpty()) {
            throw new AudioInUseException(using);
        }
        audio.delete(item.id());
        try {
            Files.deleteIfExists(fileOf(item));
        } catch (IOException e) {
            LOG.log(Level.WARNING, "Could not delete audio file " + fileOf(item), e);
        }
        bus.publish(EventBus.Topic.AUDIO);
    }

    /**
     * Points every affected schedule at {@code replacementId} (or removes the audio reference and
     * disables the schedule when null), then deletes the file.
     */
    public void reassignAndDelete(AudioItem item, Long replacementId) throws AudioInUseException {
        schedules.reassignAudio(item.id(), replacementId);
        bus.publish(EventBus.Topic.SCHEDULES);
        delete(item);
    }
}
