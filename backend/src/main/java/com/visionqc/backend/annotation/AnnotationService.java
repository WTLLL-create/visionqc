package com.visionqc.backend.annotation;

import com.visionqc.backend.common.BusinessException;
import com.visionqc.backend.defect.DefectCategoryRepository;
import com.visionqc.backend.security.Actor;
import com.visionqc.backend.task.InspectionTask;
import com.visionqc.backend.task.TaskService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

@Service
public class AnnotationService {

    private final AnnotationRepository annotationRepository;
    private final DefectCategoryRepository categoryRepository;
    private final TaskService taskService;

    public AnnotationService(AnnotationRepository annotationRepository,
                             DefectCategoryRepository categoryRepository,
                             TaskService taskService) {
        this.annotationRepository = annotationRepository;
        this.categoryRepository = categoryRepository;
        this.taskService = taskService;
    }

    @Transactional(readOnly = true)
    public List<AnnotationResponse> list(Long taskId, Actor actor) {
        taskService.assertCanView(taskService.getEntity(taskId), actor);
        return annotationRepository.findByTaskIdOrderById(taskId).stream()
                .map(AnnotationResponse::from).toList();
    }

    /**
     * 全量替换保存：在一个事务里删除旧标注再插入新标注，保证不会出现半新半旧。
     * 前置校验：任务状态可编辑 + 类别有效 + 坐标越界检查（x+w<=1, y+h<=1）。
     */
    @Transactional
    public List<AnnotationResponse> replace(Long taskId, AnnotationBatchRequest request, Actor actor) {
        InspectionTask task = taskService.getEntity(taskId);
        // 审核员不能改标注；质检员只能改分配给自己的任务；管理员不具备标注权限
        actor.requireRole("INSPECTOR");
        taskService.assertInspectorOwns(task, actor);
        if (!taskService.isEditable(task.getStatus())) {
            throw BusinessException.conflict("任务当前状态不可编辑: " + task.getStatus());
        }

        for (AnnotationBatchRequest.AnnotationItem item : request.annotations()) {
            var category = categoryRepository.findById(item.categoryId())
                    .orElseThrow(() -> BusinessException.badRequest("缺陷类别不存在: " + item.categoryId()));
            if (!category.isEnabled() || category.isDeleted()) {
                throw BusinessException.badRequest("缺陷类别已停用: " + category.getCode());
            }
            validateGeometry(item);
        }

        annotationRepository.deleteByTaskId(taskId);
        annotationRepository.flush();

        List<Annotation> saved = request.annotations().stream().map(item -> {
            Annotation a = new Annotation();
            a.setTaskId(taskId);
            a.setCategoryId(item.categoryId());
            a.setXNorm(scale(item.x()));
            a.setYNorm(scale(item.y()));
            a.setWNorm(scale(item.w()));
            a.setHNorm(scale(item.h()));
            a.setImgWidth(item.imgWidth());
            a.setImgHeight(item.imgHeight());
            a.setNote(item.note());
            return annotationRepository.save(a);
        }).toList();

        return saved.stream().map(AnnotationResponse::from).toList();
    }

    /** 矩形必须完全落在图片内：x+w 与 y+h 不得超过 1（容忍 6 位小数舍入误差） */
    private void validateGeometry(AnnotationBatchRequest.AnnotationItem item) {
        if (item.imgWidth() <= 0 || item.imgHeight() <= 0) {
            throw BusinessException.badRequest("原图尺寸无效");
        }
        BigDecimal right = item.x().add(item.w());
        BigDecimal bottom = item.y().add(item.h());
        BigDecimal limit = BigDecimal.valueOf(1.000001);
        if (right.compareTo(limit) > 0 || bottom.compareTo(limit) > 0) {
            throw BusinessException.badRequest(
                    "标注框越界：x+w=" + right + ", y+h=" + bottom + "，必须在 0~1 范围内");
        }
    }

    private BigDecimal scale(BigDecimal v) {
        return v.setScale(6, RoundingMode.HALF_UP);
    }
}
