package com.visionqc.backend.review;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/**
 * 审核/流转历史（review_records）。
 * 业务状态与记录必须在同一个事务中写入：任何一方失败都回滚，避免“状态变了但历史丢了”。
 */
@Entity
@Table(name = "review_records")
public class ReviewRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "task_id", nullable = false)
    private Long taskId;

    /** 操作者（审核员/质检员/管理员） */
    @Column(name = "operator_id", nullable = false)
    private Long operatorId;

    /** ASSIGN / START / SUBMIT / APPROVE / REJECT */
    @Column(nullable = false, length = 20)
    private String action;

    @Column(name = "from_status", nullable = false, length = 20)
    private String fromStatus;

    @Column(name = "to_status", nullable = false, length = 20)
    private String toStatus;

    @Column(length = 500)
    private String comment;

    /** 本次操作对应的任务版本（乐观锁） */
    @Column(nullable = false)
    private int version;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    public Long getId() { return id; }
    public Long getTaskId() { return taskId; }
    public void setTaskId(Long taskId) { this.taskId = taskId; }
    public Long getOperatorId() { return operatorId; }
    public void setOperatorId(Long operatorId) { this.operatorId = operatorId; }
    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }
    public String getFromStatus() { return fromStatus; }
    public void setFromStatus(String fromStatus) { this.fromStatus = fromStatus; }
    public String getToStatus() { return toStatus; }
    public void setToStatus(String toStatus) { this.toStatus = toStatus; }
    public String getComment() { return comment; }
    public void setComment(String comment) { this.comment = comment; }
    public int getVersion() { return version; }
    public void setVersion(int version) { this.version = version; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
