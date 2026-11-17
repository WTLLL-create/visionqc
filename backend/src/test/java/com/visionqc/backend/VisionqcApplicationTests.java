package com.visionqc.backend;

import com.visionqc.backend.task.TaskStatus;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;

/** 状态机枚举冒烟测试（不加载 Spring 上下文，避免依赖数据库） */
class VisionqcApplicationTests {

    @Test
    void 状态机枚举可加载() {
        assertNotNull(TaskStatus.NEW);
    }
}
