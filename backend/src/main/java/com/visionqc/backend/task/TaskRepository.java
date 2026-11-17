package com.visionqc.backend.task;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface TaskRepository extends JpaRepository<InspectionTask, Long> {

    Optional<InspectionTask> findByImageId(Long imageId);

    List<InspectionTask> findByImageIdIn(java.util.Collection<Long> imageIds);

    Optional<InspectionTask> findByIdAndStatusNot(Long id, TaskStatus status);

    Page<InspectionTask> findByStatus(TaskStatus status, Pageable pageable);

    Page<InspectionTask> findByInspectorId(Long inspectorId, Pageable pageable);

    /** 审核工作台/我的任务统一查询：按状态、批次、归属过滤 */
    @Query("""
            SELECT t FROM InspectionTask t, ImageAsset i
            WHERE t.imageId = i.id
              AND (:status IS NULL OR t.status = :status)
              AND (:batchId IS NULL OR i.batchId = :batchId)
              AND (:inspectorId IS NULL OR t.inspectorId = :inspectorId)
            """)
    Page<InspectionTask> search(@Param("status") TaskStatus status,
                                @Param("batchId") Long batchId,
                                @Param("inspectorId") Long inspectorId,
                                Pageable pageable);
}
