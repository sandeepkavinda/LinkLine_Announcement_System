package com.linkline.fam.model;

public enum IntervalUnit {
    DAYS("day(s)"),
    WEEKS("week(s)");

    private final String label;

    IntervalUnit(String label) {
        this.label = label;
    }

    @Override
    public String toString() {
        return label;
    }
}
