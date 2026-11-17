package com.visionqc.backend.annotation;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 矩形缺陷标注。
 * 坐标一律存归一化值（0~1，DECIMAL(8,6)），与浏览器显示尺寸、缩放比例解耦；
 * 同时保存标注时的原图宽高，便于审计与跨分辨率校验（任务书 6.5）。
 */
@Entity
@Table(name = "annotations")
public class Annotation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "task_id", nullable = false)
    private Long taskId;

    @Column(name = "category_id", nullable = false)
    private Long categoryId;

    @Column(name = "x_norm", nullable = false, precision = 8, scale = 6)
    private BigDecimal xNorm;

    @Column(name = "y_norm", nullable = false, precision = 8, scale = 6)
    private BigDecimal yNorm;

    @Column(name = "w_norm", nullable = false, precision = 8, scale = 6)
    private BigDecimal wNorm;

    @Column(name = "h_norm", nullable = false, precision = 8, scale = 6)
    private BigDecimal hNorm;

    @Column(name = "img_width", nullable = false)
    private int imgWidth;

    @Column(name = "img_height", nullable = false)
    private int imgHeight;

    @Column(length = 255)
    private String note;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();

    @PreUpdate
    void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public Long getTaskId() { return taskId; }
    public void setTaskId(Long taskId) { this.taskId = taskId; }
    public Long getCategoryId() { return categoryId; }
    public void setCategoryId(Long categoryId) { this.categoryId = categoryId; }
    public BigDecimal getXNorm() { return xNorm; }
    public void setXNorm(BigDecimal xNorm) { this.xNorm = xNorm; }
    public BigDecimal getYNorm() { return yNorm; }
    public void setYNorm(BigDecimal yNorm) { this.yNorm = yNorm; }
    public BigDecimal getWNorm() { return wNorm; }
    public void setWNorm(BigDecimal wNorm) { this.wNorm = wNorm; }
    public BigDecimal getHNorm() { return hNorm; }
    public void setHNorm(BigDecimal hNorm) { this.hNorm = hNorm; }
    public int getImgWidth() { return imgWidth; }
    public void setImgWidth(int imgWidth) { this.imgWidth = imgWidth; }
    public int getImgHeight() { return imgHeight; }
    public void setImgHeight(int imgHeight) { this.imgHeight = imgHeight; }
    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
