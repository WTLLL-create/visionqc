package com.visionqc.backend.common;

import java.util.List;

/** 统一分页响应结构，前端按 content/totalElements/page/size 消费 */
public record PageResult<T>(List<T> content, long totalElements, int page, int size) {
    public static <T> PageResult<T> of(org.springframework.data.domain.Page<T> p) {
        return new PageResult<>(p.getContent(), p.getTotalElements(), p.getNumber(), p.getSize());
    }
}
