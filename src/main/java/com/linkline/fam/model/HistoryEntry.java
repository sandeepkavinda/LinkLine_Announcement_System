package com.linkline.fam.model;

import java.time.LocalDateTime;

public record HistoryEntry(
        long id,
        Long scheduleId,
        String scheduleName,
        String audioName,
        String deviceName,
        LocalDateTime plannedAt,
        LocalDateTime startedAt,
        LocalDateTime finishedAt,
        PlayStatus status,
        TriggerType trigger,
        String message) {
}
