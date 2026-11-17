package com.visionqc.backend.annotation;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;

/**
 * 标注保存请求（全量替换语义：一次提交当前任务的全部矩形框）。
 * 前端画布缩放只影响显示，提交的必须是归一化坐标（x/y/w/h ∈ (0,1]）。
 */
public record AnnotationBatchRequest(
        @NotNull @Valid List<AnnotationItem> annotations) {

    public record AnnotationItem(
            @NotNull(message = "缺陷类别不能为空") Long categoryId,

            @NotNull @DecimalMin(value = "0.0", message = "x 不能为负")
            @DecimalMax(value = "0.999999", message = "x 超出图片范围")
            BigDecimal x,

            @NotNull @DecimalMin(value = "0.0", message = "y 不能为负")
            @DecimalMax(value = "0.999999", message = "y 超出图片范围")
            BigDecimal y,

            @NotNull @DecimalMin(value = "0.000001", message = "宽度必须大于 0")
            @DecimalMax(value = "1.0", message = "宽度超过图片")
            BigDecimal w,

            @NotNull @DecimalMin(value = "0.000001", message = "高度必须大于 0")
            @DecimalMax(value = "1.0", message = "高度超过图片")
            BigDecimal h,

            @NotNull Integer imgWidth,
            @NotNull Integer imgHeight,

            @Size(max = 255, message = "备注过长")
            String note) {
    }
}
