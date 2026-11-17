package com.visionqc.backend.task;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.LocalDateTime;

/**
 * 质检任务（Phase 2 最小实现：图片上传时自动建任务，供标注挂载）。
 * Phase 3 在此实体上实现分配、状态机流转和乐观锁审核。
 */
@Entity
@Table(name = "inspection_tasks")
public class InspectionTask {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "image_id", nullable = false, unique = true)
    private Long imageId;

    @Column(name = "inspector_id")
    private Long inspectorId;

    @Column(name = "reviewer_id")
    private Long reviewerId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TaskStatus status = TaskStatus.NEW;

    /** 乐观锁版本号：Phase 3 审核并发控制使用 */
    @Version
    @Column(nullable = false)
    private int version;

    @Column(length = 500)
    private String remark;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();

    @PreUpdate
    void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public Long getImageId() { return imageId; }
    public void setImageId(Long imageId) { this.imageId = imageId; }
    public Long getInspectorId() { return inspectorId; }
    public void setInspectorId(Long inspectorId) { this.inspectorId = inspectorId; }
    public Long getReviewerId() { return reviewerId; }
    public void setReviewerId(Long reviewerId) { this.reviewerId = reviewerId; }
    public TaskStatus getStatus() { return status; }
    public void setStatus(TaskStatus status) { this.status = status; }
    public int getVersion() { return version; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
