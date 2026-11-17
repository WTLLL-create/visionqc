package com.visionqc.backend.image;

import java.time.LocalDateTime;

public record ImageResponse(
        Long id,
        Long batchId,
        Long taskId,
        String originalName,
        String contentType,
        int width,
        int height,
        long sizeBytes,
        String sha256,
        String status,
        LocalDateTime createdAt) {
}
