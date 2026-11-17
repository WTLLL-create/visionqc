package com.visionqc.backend.task;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 状态机单元测试：纯 JVM 测试，不依赖 Spring 与数据库。
 * 覆盖任务书 6.6 全部合法/非法转换。
 */
class TaskStatusTest {

    @Test
    void 合法转换应全部通过() {
        assertTrue(TaskStatus.NEW.canTransitionTo(TaskStatus.ASSIGNED));
        assertTrue(TaskStatus.ASSIGNED.canTransitionTo(TaskStatus.IN_PROGRESS));
        assertTrue(TaskStatus.IN_PROGRESS.canTransitionTo(TaskStatus.SUBMITTED));
        assertTrue(TaskStatus.SUBMITTED.canTransitionTo(TaskStatus.APPROVED));
        assertTrue(TaskStatus.SUBMITTED.canTransitionTo(TaskStatus.REJECTED));
        assertTrue(TaskStatus.REJECTED.canTransitionTo(TaskStatus.IN_PROGRESS)); // 驳回后重新编辑
    }

    @Test
    void 未经审核不能直接通过() {
        // 未提交的标注不能直接审核通过
        assertFalse(TaskStatus.NEW.canTransitionTo(TaskStatus.APPROVED));
        assertFalse(TaskStatus.ASSIGNED.canTransitionTo(TaskStatus.APPROVED));
        assertFalse(TaskStatus.IN_PROGRESS.canTransitionTo(TaskStatus.APPROVED));
    }

    @Test
    void 终态APPROVED不可再流转() {
        for (TaskStatus target : TaskStatus.values()) {
            assertFalse(TaskStatus.APPROVED.canTransitionTo(target),
                    "APPROVED 不应流转到 " + target);
        }
    }

    @Test
    void 常见非法转换全部被拒绝() {
        List<Map.Entry<TaskStatus, TaskStatus>> illegal = List.of(
                Map.entry(TaskStatus.NEW, TaskStatus.IN_PROGRESS),      // 未分配不能开始
                Map.entry(TaskStatus.NEW, TaskStatus.SUBMITTED),       // 不能跳过开始直接提交
                Map.entry(TaskStatus.REJECTED, TaskStatus.SUBMITTED),  // 驳回后必须先回 IN_PROGRESS
                Map.entry(TaskStatus.REJECTED, TaskStatus.APPROVED),   // 未重新提交不能直接通过
                Map.entry(TaskStatus.ASSIGNED, TaskStatus.REJECTED));   // 未提交不存在审核
        illegal.forEach(e ->
                assertFalse(e.getKey().canTransitionTo(e.getValue()),
                        e.getKey() + " 不应流转到 " + e.getValue()));
    }
}
