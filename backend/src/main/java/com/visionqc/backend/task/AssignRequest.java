package com.visionqc.backend.task;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** 管理员分配质检员：请求必须携带读取时的任务版本 */
public record AssignRequest(
        @NotNull(message = "必须选择质检员")
        Long inspectorId,
        @NotNull(message = "必须携带任务版本号")
        Integer version,
        @Size(max = 500, message = "备注最多 500 字")
        String comment) {
}
