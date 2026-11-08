package com.linkline.fam.audio;

/**
 * A Windows audio output (render) endpoint.
 *
 * @param id        stable endpoint id (Windows) or mixer name (other platforms)
 * @param name      friendly name shown to the user
 * @param isDefault true for the current default Windows output device
 */
public record AudioDevice(String id, String name, boolean isDefault, DeviceState state) {

    public boolean isUsable() {
        return state.isUsable();
    }

    @Override
    public String toString() {
        return name;
    }
}
