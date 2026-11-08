package com.linkline.fam.audio;

import java.io.ByteArrayInputStream;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;

/** A built-in two-tone "ding-dong" used for test announcements, so tests need no audio file. */
public final class Chime {

    private static final float RATE = 44100f;

    private Chime() {
    }

    public static AudioInputStream open() {
        byte[] pcm = render();
        AudioFormat fmt = new AudioFormat(AudioFormat.Encoding.PCM_SIGNED, RATE, 16, 2, 4, RATE, false);
        return new AudioInputStream(new ByteArrayInputStream(pcm), fmt, pcm.length / 4);
    }

    public static long durationMs() {
        return Math.round(render().length / 4 / RATE * 1000);
    }

    private static byte[] render() {
        double[] notes = {659.25, 523.25};
        double[] lengths = {0.75, 1.35};
        int total = 0;
        for (double l : lengths) {
            total += (int) (l * RATE);
        }
        byte[] out = new byte[total * 4];
        int pos = 0;
        for (int k = 0; k < notes.length; k++) {
            int frames = (int) (lengths[k] * RATE);
            for (int i = 0; i < frames; i++) {
                double t = i / (double) RATE;
                double env = Math.min(1.0, i / (0.01 * RATE)) * Math.exp(-3.2 * t / lengths[k]);
                double s = Math.sin(2 * Math.PI * notes[k] * t)
                        + 0.35 * Math.sin(2 * Math.PI * notes[k] * 2 * t)
                        + 0.12 * Math.sin(2 * Math.PI * notes[k] * 3 * t);
                short v = (short) Math.round(s / 1.47 * env * 0.55 * Short.MAX_VALUE);
                for (int ch = 0; ch < 2; ch++) {
                    out[pos++] = (byte) (v & 0xFF);
                    out[pos++] = (byte) ((v >> 8) & 0xFF);
                }
            }
        }
        return out;
    }
}
