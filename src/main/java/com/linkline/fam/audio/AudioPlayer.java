package com.linkline.fam.audio;

import java.util.logging.Logger;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.Mixer;
import javax.sound.sampled.SourceDataLine;

/**
 * Plays one audio stream to one Java Sound mixer. {@link #play} blocks until the audio ends or
 * {@link #stop()} is called, so callers run it on their own thread. One instance plays one item.
 */
public final class AudioPlayer {

    private static final Logger LOG = Logger.getLogger(AudioPlayer.class.getName());

    /** Opens a fresh stream each time playback starts. */
    @FunctionalInterface
    public interface Source {
        AudioInputStream open() throws Exception;
    }

    public enum State { IDLE, PLAYING, PAUSED, STOPPED, FINISHED }

    private final Object lock = new Object();
    private volatile State state = State.IDLE;
    private volatile boolean stopRequested;
    private volatile boolean pauseRequested;
    private volatile SourceDataLine activeLine;

    public State state() {
        return state;
    }

    public void pause() {
        if (state == State.PLAYING) {
            pauseRequested = true;
        }
    }

    public void resume() {
        synchronized (lock) {
            pauseRequested = false;
            lock.notifyAll();
        }
    }

    public void stop() {
        synchronized (lock) {
            stopRequested = true;
            pauseRequested = false;
            lock.notifyAll();
        }
        SourceDataLine line = activeLine;
        if (line != null) {
            line.flush();
        }
    }

    public long positionMs() {
        SourceDataLine line = activeLine;
        return line == null ? 0 : line.getMicrosecondPosition() / 1000;
    }

    /**
     * Plays the source on the given mixer, or on the system default when {@code target} is null.
     *
     * @throws Exception if the file cannot be decoded or the device cannot be opened
     */
    public void play(Source source, Mixer.Info target) throws Exception {
        stopRequested = false;
        pauseRequested = false;
        state = State.PLAYING;
        try (AudioInputStream raw = source.open()) {
            AudioInputStream pcm = toPcm(raw);
            AudioFormat fmt = pcm.getFormat();
            Mixer mixer = target == null ? null : AudioSystem.getMixer(target);

            DataLine.Info info = new DataLine.Info(SourceDataLine.class, fmt);
            if (!supported(mixer, info)) {
                AudioFormat std = new AudioFormat(AudioFormat.Encoding.PCM_SIGNED, 44100f, 16, 2, 4, 44100f, false);
                if (AudioSystem.isConversionSupported(std, fmt)) {
                    pcm = AudioSystem.getAudioInputStream(std, pcm);
                    fmt = std;
                    info = new DataLine.Info(SourceDataLine.class, fmt);
                }
                if (!supported(mixer, info)) {
                    throw new LineUnavailableException(
                            "The output device does not support this audio format (" + fmt + ")");
                }
            }

            SourceDataLine line = (SourceDataLine) (mixer == null ? AudioSystem.getLine(info) : mixer.getLine(info));
            line.open(fmt);
            activeLine = line;
            try {
                run(pcm, line, fmt.getFrameSize());
            } finally {
                activeLine = null;
                line.close();
            }
        } finally {
            if (state == State.PLAYING || state == State.PAUSED) {
                state = State.FINISHED;
            }
        }
    }

    private static boolean supported(Mixer mixer, DataLine.Info info) {
        return mixer == null ? AudioSystem.isLineSupported(info) : mixer.isLineSupported(info);
    }

    private void run(AudioInputStream pcm, SourceDataLine line, int frameSize) throws Exception {
        byte[] buf = new byte[Math.max(1, frameSize) * 2048];
        line.start();
        int n;
        while (!stopRequested && (n = pcm.read(buf, 0, buf.length)) != -1) {
            if (pauseRequested) {
                line.stop();
                state = State.PAUSED;
                synchronized (lock) {
                    while (pauseRequested && !stopRequested) {
                        lock.wait(250);
                    }
                }
                if (stopRequested) {
                    break;
                }
                line.start();
                state = State.PLAYING;
            }
            int off = 0;
            while (off < n && !stopRequested) {
                int written = line.write(buf, off, n - off);
                if (written <= 0) {
                    break;
                }
                off += written;
            }
        }
        if (stopRequested) {
            line.stop();
            line.flush();
            state = State.STOPPED;
        } else {
            line.drain();
            line.stop();
            state = State.FINISHED;
        }
    }

    /** Converts anything Java Sound can decode (including MP3 via the SPI) to 16-bit signed PCM. */
    public static AudioInputStream toPcm(AudioInputStream in) {
        AudioFormat f = in.getFormat();
        if (f.getEncoding() == AudioFormat.Encoding.PCM_SIGNED && f.getSampleSizeInBits() == 16 && !f.isBigEndian()) {
            return in;
        }
        int channels = f.getChannels();
        float rate = f.getSampleRate();
        AudioFormat target = new AudioFormat(AudioFormat.Encoding.PCM_SIGNED, rate, 16, channels, channels * 2, rate, false);
        LOG.fine(() -> "Converting " + f + " to " + target);
        return AudioSystem.getAudioInputStream(target, in);
    }
}
