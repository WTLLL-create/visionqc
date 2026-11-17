package com.visionqc.backend.task;

/**
 * 审核状态机（任务书 6.6）。
 *
 * 为什么用枚举集中定义转换表：
 * - 非法转换在 Service 层之前就被拒绝，规则集中一处，杜绝散落在各方法里的 if 判断。
 * - 单元测试可直接对转换表测试，不需要起 Spring 上下文。
 */
public enum TaskStatus {
    NEW,
    ASSIGNED,
    IN_PROGRESS,
    SUBMITTED,
    REJECTED,
    APPROVED;

    /** 判断当前状态能否流转到目标状态 */
    public boolean canTransitionTo(TaskStatus target) {
        return switch (this) {
            case NEW -> target == ASSIGNED;
            case ASSIGNED -> target == IN_PROGRESS;
            case IN_PROGRESS -> target == SUBMITTED;
            case SUBMITTED -> target == APPROVED || target == REJECTED;
            case REJECTED -> target == IN_PROGRESS;
            case APPROVED -> false; // 终态：审核通过后不可被普通用户覆盖
        };
    }
}
