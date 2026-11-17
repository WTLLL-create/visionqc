package com.visionqc.backend.device;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** 请求 DTO：Entity 不直接暴露给 API（任务书第七节要求） */
public record DeviceRequest(
        @NotBlank(message = "设备名称不能为空")
        @Size(max = 100, message = "设备名称过长")
        String deviceName,

        @NotBlank(message = "设备编码不能为空")
        @Pattern(regexp = "^[A-Za-z0-9_-]{2,50}$", message = "编码仅允许字母/数字/下划线/中划线")
        String deviceCode,

        @Size(max = 255, message = "描述过长")
        String description) {
}
