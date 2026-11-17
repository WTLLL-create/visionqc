package com.visionqc.backend.batch;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "production_batches")
public class ProductionBatch {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "batch_code", nullable = false, unique = true, length = 50)
    private String batchCode;

    @Column(name = "device_id", nullable = false)
    private Long deviceId;

    @Column(name = "product_type", nullable = false, length = 100)
    private String productType;

    @Column(name = "capture_date", nullable = false)
    private LocalDate captureDate;

    /** OPEN / CLOSED */
    @Column(nullable = false, length = 20)
    private String status = "OPEN";

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(nullable = false)
    private boolean deleted = false;

    public Long getId() { return id; }
    public String getBatchCode() { return batchCode; }
    public void setBatchCode(String batchCode) { this.batchCode = batchCode; }
    public Long getDeviceId() { return deviceId; }
    public void setDeviceId(Long deviceId) { this.deviceId = deviceId; }
    public String getProductType() { return productType; }
    public void setProductType(String productType) { this.productType = productType; }
    public LocalDate getCaptureDate() { return captureDate; }
    public void setCaptureDate(LocalDate captureDate) { this.captureDate = captureDate; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public boolean isDeleted() { return deleted; }
    public void setDeleted(boolean deleted) { this.deleted = deleted; }
}
