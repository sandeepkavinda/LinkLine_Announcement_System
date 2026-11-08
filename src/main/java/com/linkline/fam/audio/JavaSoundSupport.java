package com.linkline.fam.audio;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Line;
import javax.sound.sampled.Mixer;
import javax.sound.sampled.SourceDataLine;

/** Maps Windows endpoint names to Java Sound mixers, which is how FAM routes playback to one device. */
public final class JavaSoundSupport {

    private JavaSoundSupport() {
    }

    /** All mixers that can play audio. */
    public static List<Mixer.Info> outputMixers() {
        List<Mixer.Info> list = new ArrayList<>();
        for (Mixer.Info info : AudioSystem.getMixerInfo()) {
            try {
                Mixer m = AudioSystem.getMixer(info);
                for (Line.Info li : m.getSourceLineInfo()) {
                    if (SourceDataLine.class.isAssignableFrom(li.getLineClass())) {
                        list.add(info);
                        break;
                    }
                }
            } catch (RuntimeException ignored) {
                // a broken driver must not take the whole list down
            }
        }
        return list;
    }

    private static String norm(String s) {
        return s == null ? "" : s.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", " ").strip();
    }

    /**
     * Finds the Java Sound mixer that belongs to a Windows endpoint name such as
     * "Speakers (USB Audio Device)". Java may shorten long names, so a prefix match is accepted.
     */
    public static Optional<Mixer.Info> findMixer(String endpointName) {
        String want = norm(endpointName);
        if (want.isEmpty()) {
            return Optional.empty();
        }
        List<Mixer.Info> mixers = outputMixers();
        for (Mixer.Info info : mixers) {
            if (norm(info.getName()).equals(want)) {
                return Optional.of(info);
            }
        }
        Mixer.Info best = null;
        int bestLen = 0;
        for (Mixer.Info info : mixers) {
            String have = norm(info.getName());
            if (have.length() < 12) {
                continue;
            }
            if ((want.startsWith(have) || have.startsWith(want)) && have.length() > bestLen) {
                best = info;
                bestLen = have.length();
            }
        }
        return Optional.ofNullable(best);
    }
}
