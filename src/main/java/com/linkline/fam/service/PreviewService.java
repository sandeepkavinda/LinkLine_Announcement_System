package com.linkline.fam.service;

import com.linkline.fam.audio.AudioFiles;
import com.linkline.fam.audio.AudioPlayer;
import com.linkline.fam.model.AudioItem;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Preview playback for the audio library. Previews always use the Windows default output, never the
 * announcement device, so checking a file in the office cannot sound through the factory PA.
 */
public final class PreviewService {

    private static final Logger LOG = Logger.getLogger(PreviewService.class.getName());

    public enum State { STOPPED, PLAYING, PAUSED }

    private final AudioLibraryService library;
    private final EventBus bus;
    private volatile AudioPlayer player;
    private volatile long currentId = -1;
    private volatile State state = State.STOPPED;

    public PreviewService(AudioLibraryService library, EventBus bus) {
        this.library = library;
        this.bus = bus;
    }

    public State state() {
        return state;
    }

    public long currentId() {
        return currentId;
    }

    public long positionMs() {
        AudioPlayer p = player;
        return p == null ? 0 : p.positionMs();
    }

    /** Starts previewing; any running preview is stopped first. */
    public synchronized void play(AudioItem item, Consumer<String> onError) {
        stop();
        if (!library.fileExists(item)) {
            onError.accept("The audio file is missing from FAM's storage.");
            return;
        }
        AudioPlayer p = new AudioPlayer();
        player = p;
        currentId = item.id();
        state = State.PLAYING;
        bus.publish(EventBus.Topic.PREVIEW);
        Thread t = new Thread(() -> {
            try {
                p.play(() -> AudioFiles.open(library.fileOf(item)), null);
            } catch (Exception e) {
                LOG.log(Level.INFO, "Preview failed", e);
                onError.accept("Cannot play this file: " + e.getMessage());
            } finally {
                synchronized (PreviewService.this) {
                    if (player == p) {
                        player = null;
                        currentId = -1;
                        state = State.STOPPED;
                    }
                }
                bus.publish(EventBus.Topic.PREVIEW);
            }
        }, "fam-preview");
        t.setDaemon(true);
        t.start();
    }

    public synchronized void pause() {
        AudioPlayer p = player;
        if (p != null && state == State.PLAYING) {
            p.pause();
            state = State.PAUSED;
            bus.publish(EventBus.Topic.PREVIEW);
        }
    }

    public synchronized void resume() {
        AudioPlayer p = player;
        if (p != null && state == State.PAUSED) {
            p.resume();
            state = State.PLAYING;
            bus.publish(EventBus.Topic.PREVIEW);
        }
    }

    public synchronized void stop() {
        AudioPlayer p = player;
        if (p != null) {
            p.stop();
        }
    }
}
