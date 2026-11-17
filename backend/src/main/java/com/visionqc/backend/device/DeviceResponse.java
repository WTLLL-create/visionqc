package com.visionqc.backend.device;

import java.time.LocalDateTime;

/** 响应 DTO */
public record DeviceResponse(
        Long id,
        String deviceName,
        String deviceCode,
        String deviceStatus,
        String description,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {

    static DeviceResponse from(Device d) {
        return new DeviceResponse(d.getId(), d.getDeviceName(), d.getDeviceCode(),
                d.getDeviceStatus(), d.getDescription(), d.getCreatedAt(), d.getUpdatedAt());
    }
}
