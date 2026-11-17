package com.visionqc.backend.image;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface ImageAssetRepository extends JpaRepository<ImageAsset, Long> {

    /** 批次内按 sha256 去重（唯一约束 uk_batch_sha 的查询侧） */
    Optional<ImageAsset> findFirstByBatchIdAndSha256(Long batchId, String sha256);

    Optional<ImageAsset> findByIdAndStatus(Long id, String status);

    @Query("""
            SELECT i FROM ImageAsset i
            WHERE i.status = 'ACTIVE'
              AND (:batchId IS NULL OR i.batchId = :batchId)
            ORDER BY i.id DESC
            """)
    Page<ImageAsset> search(@Param("batchId") Long batchId, Pageable pageable);

    List<ImageAsset> findByStatusAndCreatedAtBefore(String status, LocalDateTime before);

    long countByStatus(String status);
}
