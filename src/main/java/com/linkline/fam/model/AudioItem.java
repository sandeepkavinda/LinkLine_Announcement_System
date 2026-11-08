package com.linkline.fam.model;

import java.time.LocalDateTime;

/**
 * One audio file in the library.
 *
 * @param storedName    internal file name inside FAM's audio folder
 * @param assignedCount number of schedules currently using this file
 */
public record AudioItem(
        long id,
        String displayName,
        String originalName,
        String storedName,
        String format,
        long durationMs,
        long sizeBytes,
        LocalDateTime importedAt,
        int assignedCount) {

    public boolean isAssigned() {
        return assignedCount > 0;
    }

    @Override
    public String toString() {
        return displayName;
    }
}
