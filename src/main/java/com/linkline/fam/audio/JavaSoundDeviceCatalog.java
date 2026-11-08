package com.linkline.fam.audio;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import javax.sound.sampled.Mixer;

/** Fallback catalog built from Java Sound mixers, used when the Windows API is unavailable. */
public final class JavaSoundDeviceCatalog implements DeviceCatalog {

    @Override
    public List<AudioDevice> listOutputs() {
        List<AudioDevice> list = new ArrayList<>();
        boolean first = true;
        for (Mixer.Info info : JavaSoundSupport.outputMixers()) {
            String name = info.getName();
            boolean isDefault = first || name.toLowerCase(Locale.ROOT).contains("primary sound driver");
            if (isDefault && !first) {
                // only one default: prefer the explicit "Primary Sound Driver" entry
                for (int i = 0; i < list.size(); i++) {
                    AudioDevice d = list.get(i);
                    list.set(i, new AudioDevice(d.id(), d.name(), false, d.state()));
                }
            }
            list.add(new AudioDevice(name, name, isDefault, DeviceState.ACTIVE));
            first = false;
        }
        return list;
    }

    @Override
    public String description() {
        return "Java Sound mixers";
    }
}
