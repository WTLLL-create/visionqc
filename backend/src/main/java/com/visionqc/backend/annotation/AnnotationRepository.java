package com.visionqc.backend.annotation;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AnnotationRepository extends JpaRepository<Annotation, Long> {

    List<Annotation> findByTaskIdOrderById(Long taskId);

    void deleteByTaskId(Long taskId);

    long countByTaskId(Long taskId);
}
