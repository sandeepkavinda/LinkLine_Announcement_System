package com.linkline.fam.audio;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.UnsupportedAudioFileException;

/** Opening and validating audio files. */
public final class AudioFiles {

    /** Extensions FAM accepts. MP3 needs the mp3spi decoder that is bundled with the application. */
    public static final List<String> SUPPORTED_EXTENSIONS = List.of("mp3", "wav", "aif", "aiff", "au");

    public record Probe(long durationMs) {
    }

    private AudioFiles() {
    }

    public static boolean isSupportedExtension(String ext) {
        return SUPPORTED_EXTENSIONS.contains(ext.toLowerCase(java.util.Locale.ROOT));
    }

    public static AudioInputStream open(Path file) throws UnsupportedAudioFileException, IOException {
        return AudioSystem.getAudioInputStream(file.toFile());
    }

    /**
     * Fully decodes the file to prove it is playable and to measure its real duration.
     *
     * @throws IOException if the file is damaged, empty or in a format that cannot be decoded
     */
    public static Probe probe(Path file) throws IOException {
        try (AudioInputStream raw = open(file); AudioInputStream pcm = AudioPlayer.toPcm(raw)) {
            AudioFormat f = pcm.getFormat();
            byte[] buf = new byte[64 * 1024];
            long bytes = 0;
            int n;
            while ((n = pcm.read(buf)) != -1) {
                bytes += n;
            }
            if (bytes <= 0 || f.getFrameSize() <= 0 || f.getFrameRate() <= 0) {
                throw new IOException("The file contains no audio data.");
            }
            double seconds = (bytes / (double) f.getFrameSize()) / f.getFrameRate();
            return new Probe(Math.round(seconds * 1000));
        } catch (UnsupportedAudioFileException e) {
            throw new IOException("This file type is not supported or the file is not valid audio.", e);
        } catch (IOException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new IOException("The audio data is damaged and cannot be decoded.", e);
        }
    }
}
