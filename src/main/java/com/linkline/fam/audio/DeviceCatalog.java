package com.linkline.fam.audio;

import java.util.List;

/** Lists the audio output devices that exist on this machine. */
public interface DeviceCatalog {

    /** All known output devices, including disabled or unplugged ones. May block briefly. */
    List<AudioDevice> listOutputs() throws Exception;

    /** Short description of where the list comes from, for diagnostics. */
    String description();
}
