package com.visionqc.backend.annotation;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record AnnotationResponse(
        Long id,
        Long taskId,
        Long categoryId,
        BigDecimal xNorm,
        BigDecimal yNorm,
        BigDecimal wNorm,
        BigDecimal hNorm,
        int imgWidth,
        int imgHeight,
        String note,
        LocalDateTime updatedAt) {

    static AnnotationResponse from(Annotation a) {
        return new AnnotationResponse(a.getId(), a.getTaskId(), a.getCategoryId(),
                a.getXNorm(), a.getYNorm(), a.getWNorm(), a.getHNorm(),
                a.getImgWidth(), a.getImgHeight(), a.getNote(), a.getUpdatedAt());
    }
}
