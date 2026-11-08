package com.linkline.fam.audio;

public enum DeviceState {
    ACTIVE("Connected"),
    DISABLED("Disabled"),
    NOT_PRESENT("Disconnected"),
    UNPLUGGED("Disconnected"),
    UNKNOWN("Unknown");

    private final String label;

    DeviceState(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    public boolean isUsable() {
        return this == ACTIVE;
    }
}
