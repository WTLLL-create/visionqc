package com.visionqc.backend.batch;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record BatchRequest(
        @NotBlank(message = "批次编码不能为空")
        @Pattern(regexp = "^[A-Za-z0-9_-]{2,50}$", message = "编码仅允许字母/数字/下划线/中划线")
        String batchCode,

        @NotNull(message = "必须关联设备")
        Long deviceId,

        @NotBlank(message = "产品型号不能为空")
        @Size(max = 100)
        String productType,

        @NotNull(message = "拍摄日期不能为空")
        @PastOrPresent(message = "拍摄日期不能晚于今天")
        LocalDate captureDate) {
}
