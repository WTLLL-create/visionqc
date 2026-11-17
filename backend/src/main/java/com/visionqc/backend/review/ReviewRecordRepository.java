package com.visionqc.backend.review;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ReviewRecordRepository extends JpaRepository<ReviewRecord, Long> {
    List<ReviewRecord> findByTaskIdOrderByIdAsc(Long taskId);
}
