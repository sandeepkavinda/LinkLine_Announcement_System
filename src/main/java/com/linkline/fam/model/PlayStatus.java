package com.linkline.fam.model;

/** Lifecycle of one announcement run, as stored in the history table. */
public enum PlayStatus {
    QUEUED("Queued"),
    PLAYING("Playing"),
    COMPLETED("Completed"),
    FAILED("Failed"),
    SKIPPED("Skipped");

    private final String label;

    PlayStatus(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    @Override
    public String toString() {
        return label;
    }
}
