package com.visionqc.backend.batch;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface BatchRepository extends JpaRepository<ProductionBatch, Long> {

    Page<ProductionBatch> findByDeletedFalse(Pageable pageable);

    Optional<ProductionBatch> findByIdAndDeletedFalse(Long id);

    boolean existsByBatchCodeAndDeletedFalse(String batchCode);

    @Query("""
            SELECT b FROM ProductionBatch b
            WHERE b.deleted = false
              AND (:deviceId IS NULL OR b.deviceId = :deviceId)
              AND (:status IS NULL OR b.status = :status)
            """)
    Page<ProductionBatch> search(@Param("deviceId") Long deviceId,
                                @Param("status") String status,
                                Pageable pageable);
}
