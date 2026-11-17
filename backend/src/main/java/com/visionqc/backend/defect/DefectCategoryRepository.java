package com.visionqc.backend.defect;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DefectCategoryRepository extends JpaRepository<DefectCategory, Long> {
    List<DefectCategory> findByEnabledTrueAndDeletedFalseOrderById();
}
