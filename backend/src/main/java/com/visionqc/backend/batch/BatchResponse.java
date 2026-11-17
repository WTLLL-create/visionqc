package com.visionqc.backend.batch;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record BatchResponse(
        Long id,
        String batchCode,
        Long deviceId,
        String deviceName,
        String productType,
        LocalDate captureDate,
        String status,
        LocalDateTime createdAt) {

    static BatchResponse from(ProductionBatch b, String deviceName) {
        return new BatchResponse(b.getId(), b.getBatchCode(), b.getDeviceId(), deviceName,
                b.getProductType(), b.getCaptureDate(), b.getStatus(), b.getCreatedAt());
    }
}
