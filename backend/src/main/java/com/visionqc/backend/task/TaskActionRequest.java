package com.visionqc.backend.task;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** 状态流转请求：version 必须是客户端读取到的任务版本，过期即 409 */
public record TaskActionRequest(
        @NotNull(message = "必须携带任务版本号")
        Integer version,
        @Size(max = 500, message = "备注最多 500 字")
        String comment) {
}
