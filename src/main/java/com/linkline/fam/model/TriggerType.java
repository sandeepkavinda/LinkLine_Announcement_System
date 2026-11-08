package com.linkline.fam.model;

public enum TriggerType {
    /** Fired by the scheduler at the planned time. */
    SCHEDULED("Scheduled"),
    /** Fired late by the scheduler under the missed-schedule recovery rules. */
    RECOVERED("Recovered"),
    /** "Play now" on a schedule. */
    MANUAL("Manual"),
    /** Test announcement from the dashboard or device page. */
    TEST("Test");

    private final String label;

    TriggerType(String label) {
        this.label = label;
    }

    public boolean isScheduledKind() {
        return this == SCHEDULED || this == RECOVERED;
    }

    @Override
    public String toString() {
        return label;
    }
}
