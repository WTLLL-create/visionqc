package com.visionqc.backend.review;

import java.time.LocalDateTime;

public record ReviewRecordResponse(
        Long id,
        Long taskId,
        Long operatorId,
        String operatorName,
        String action,
        String fromStatus,
        String toStatus,
        String comment,
        int version,
        LocalDateTime createdAt) {
}
