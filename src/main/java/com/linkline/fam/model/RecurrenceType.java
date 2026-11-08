package com.linkline.fam.model;

public enum RecurrenceType {
    ONCE("Once"),
    DAILY("Every day"),
    WEEKDAYS("Weekdays (Mon–Fri)"),
    WEEKENDS("Weekends (Sat–Sun)"),
    SELECTED_DAYS("Selected days of the week"),
    CUSTOM("Custom interval");

    private final String label;

    RecurrenceType(String label) {
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
