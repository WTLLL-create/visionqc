package com.visionqc.backend.defect;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/defect-categories")
public class DefectCategoryController {

    private final DefectCategoryRepository repository;

    public DefectCategoryController(DefectCategoryRepository repository) {
        this.repository = repository;
    }

    /** 标注工作台的缺陷类别选择来源（Phase 4 再补管理端 CRUD） */
    @GetMapping
    public List<CategoryResponse> list() {
        return repository.findByEnabledTrueAndDeletedFalseOrderById().stream()
                .map(c -> new CategoryResponse(c.getId(), c.getCode(), c.getName(), c.getDescription()))
                .toList();
    }

    public record CategoryResponse(Long id, String code, String name, String description) {}
}
