package com.visionqc.backend.annotation;

import com.visionqc.backend.security.Actor;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/tasks/{taskId}/annotations")
public class AnnotationController {

    private final AnnotationService annotationService;

    public AnnotationController(AnnotationService annotationService) {
        this.annotationService = annotationService;
    }

    @GetMapping
    public List<AnnotationResponse> list(@PathVariable Long taskId, Authentication auth) {
        return annotationService.list(taskId, Actor.from(auth));
    }

    /**
     * 保存标注：全量替换语义（创建/修改/删除在一次原子提交中完成）。
     * 前端刷新后按此接口恢复标注。
     */
    @PutMapping
    public List<AnnotationResponse> save(@PathVariable Long taskId,
                                         @Valid @RequestBody AnnotationBatchRequest request,
                                         Authentication auth) {
        return annotationService.replace(taskId, request, Actor.from(auth));
    }
}
