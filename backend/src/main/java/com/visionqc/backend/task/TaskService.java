package com.visionqc.backend.task;

import com.visionqc.backend.annotation.AnnotationRepository;
import com.visionqc.backend.common.BusinessException;
import com.visionqc.backend.common.PageResult;
import com.visionqc.backend.review.ReviewRecordResponse;
import com.visionqc.backend.review.ReviewService;
import com.visionqc.backend.security.Actor;
import com.visionqc.backend.user.User;
import com.visionqc.backend.user.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 任务状态机 + 审核动作。
 *
 * 设计要点：
 * 1. 每次动作先校验“客户端版本 == 数据库版本”，过期直接 409；随后由 JPA @Version 在 flush 时兜底并发冲突。
 * 2. 状态变更与 review_records 写入在同一事务内完成，任何一步失败整体回滚。
 * 3. 权限与归属在这里再做一次服务层校验，SecurityConfig 的路径规则只是第一道门。
 */
@Service
public class TaskService {

    private final TaskRepository taskRepository;
    private final AnnotationRepository annotationRepository;
    private final ReviewService reviewService;
    private final UserRepository userRepository;

    public TaskService(TaskRepository taskRepository,
                       AnnotationRepository annotationRepository,
                       ReviewService reviewService,
                       UserRepository userRepository) {
        this.taskRepository = taskRepository;
        this.annotationRepository = annotationRepository;
        this.reviewService = reviewService;
        this.userRepository = userRepository;
    }

    /** 图片入库时自动创建任务（一图一任务，唯一约束 uk_task_image 兜底） */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public InspectionTask createForImage(Long imageId) {
        return taskRepository.findByImageId(imageId).orElseGet(() -> {
            InspectionTask task = new InspectionTask();
            task.setImageId(imageId);
            task.setStatus(TaskStatus.NEW);
            return taskRepository.save(task);
        });
    }

    @Transactional(readOnly = true)
    public InspectionTask getEntity(Long id) {
        return taskRepository.findById(id)
                .orElseThrow(() -> BusinessException.notFound("任务不存在: " + id));
    }

    /** 读取任务前校验访问权：质检员只能看自己的任务，审核员/管理员可看 */
    @Transactional(readOnly = true)
    public InspectionTask getEntityForActor(Long id, Actor actor) {
        InspectionTask task = getEntity(id);
        assertCanView(task, actor);
        return task;
    }

    @Transactional(readOnly = true)
    public TaskResponse get(Long id, Actor actor) {
        return TaskResponse.from(getEntityForActor(id, actor));
    }

    @Transactional(readOnly = true)
    public PageResult<TaskResponse> list(TaskStatus status, Long batchId, Pageable pageable, Actor actor) {
        // 质检员只能看到分配给自己的任务；管理员/审核员看全量
        Long inspectorId = (!actor.isAdmin() && actor.isInspector()) ? actor.requireUserId() : null;
        Page<InspectionTask> page = taskRepository.search(status, batchId, inspectorId,
                PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(),
                        Sort.by(Sort.Direction.DESC, "id")));
        return PageResult.of(page.map(TaskResponse::from));
    }

    @Transactional(readOnly = true)
    public List<ReviewRecordResponse> history(Long id, Actor actor) {
        assertCanView(getEntity(id), actor);
        return reviewService.history(id);
    }

    /** 管理员分配质检员：NEW → ASSIGNED */
    @Transactional
    public TaskResponse assign(Long id, AssignRequest req, Actor actor) {
        actor.requireRole("ADMIN");
        InspectionTask task = getEntity(id);
        checkVersion(task, req.version());
        assertTransition(task, TaskStatus.ASSIGNED);

        User inspector = userRepository.findById(req.inspectorId())
                .orElseThrow(() -> BusinessException.badRequest("质检员不存在: " + req.inspectorId()));
        if (inspector.isDeleted() || !inspector.isEnabled() || !userHasRole(inspector.getId(), "INSPECTOR")) {
            throw BusinessException.badRequest("指定用户不是可用的质检员: " + req.inspectorId());
        }

        TaskStatus from = task.getStatus();
        task.setInspectorId(inspector.getId());
        task.setStatus(TaskStatus.ASSIGNED);
        task.setRemark(normalize(req.comment()));
        taskRepository.saveAndFlush(task);
        reviewService.record(task, actor, "ASSIGN", from, TaskStatus.ASSIGNED, req.comment());
        return TaskResponse.from(task);
    }

    /** 质检员开始/继续修改：ASSIGNED 或 REJECTED → IN_PROGRESS */
    @Transactional
    public TaskResponse start(Long id, TaskActionRequest req, Actor actor) {
        actor.requireRole("INSPECTOR");
        InspectionTask task = getEntity(id);
        assertInspectorOwns(task, actor);
        checkVersion(task, req.version());
        assertTransition(task, TaskStatus.IN_PROGRESS);

        TaskStatus from = task.getStatus();
        task.setStatus(TaskStatus.IN_PROGRESS);
        taskRepository.saveAndFlush(task);
        reviewService.record(task, actor, "START", from, TaskStatus.IN_PROGRESS, req.comment());
        return TaskResponse.from(task);
    }

    /** 提交审核：IN_PROGRESS → SUBMITTED，至少一个标注框 */
    @Transactional
    public TaskResponse submit(Long id, TaskActionRequest req, Actor actor) {
        actor.requireRole("INSPECTOR");
        InspectionTask task = getEntity(id);
        assertInspectorOwns(task, actor);
        checkVersion(task, req.version());
        assertTransition(task, TaskStatus.SUBMITTED);
        if (annotationRepository.countByTaskId(id) < 1) {
            throw BusinessException.conflict("至少需要一个标注框才能提交审核");
        }

        TaskStatus from = task.getStatus();
        task.setStatus(TaskStatus.SUBMITTED);
        task.setRemark(normalize(req.comment()));
        taskRepository.saveAndFlush(task);
        reviewService.record(task, actor, "SUBMIT", from, TaskStatus.SUBMITTED, req.comment());
        return TaskResponse.from(task);
    }

    /** 审核通过：SUBMITTED → APPROVED，审核员不能审核自己标注的任务 */
    @Transactional
    public TaskResponse approve(Long id, TaskActionRequest req, Actor actor) {
        actor.requireRole("REVIEWER");
        InspectionTask task = getEntity(id);
        checkVersion(task, req.version());
        assertTransition(task, TaskStatus.APPROVED);
        assertNotSelfReview(task, actor);

        TaskStatus from = task.getStatus();
        task.setStatus(TaskStatus.APPROVED);
        task.setReviewerId(actor.requireUserId());
        task.setRemark(normalize(req.comment()));
        taskRepository.saveAndFlush(task);
        reviewService.record(task, actor, "APPROVE", from, TaskStatus.APPROVED, req.comment());
        return TaskResponse.from(task);
    }

    /** 审核驳回：SUBMITTED → REJECTED，驳回原因必填 */
    @Transactional
    public TaskResponse reject(Long id, TaskActionRequest req, Actor actor) {
        actor.requireRole("REVIEWER");
        InspectionTask task = getEntity(id);
        checkVersion(task, req.version());
        assertTransition(task, TaskStatus.REJECTED);
        assertNotSelfReview(task, actor);
        if (req.comment() == null || req.comment().isBlank()) {
            throw BusinessException.badRequest("驳回必须填写原因");
        }

        TaskStatus from = task.getStatus();
        task.setStatus(TaskStatus.REJECTED);
        task.setReviewerId(actor.requireUserId());
        task.setRemark(normalize(req.comment()));
        taskRepository.saveAndFlush(task);
        reviewService.record(task, actor, "REJECT", from, TaskStatus.REJECTED, req.comment());
        return TaskResponse.from(task);
    }

    /** 标注可编辑的任务状态：提交后必须驳回或重新开始才能编辑 */
    public boolean isEditable(TaskStatus status) {
        return status == TaskStatus.NEW || status == TaskStatus.ASSIGNED
                || status == TaskStatus.IN_PROGRESS || status == TaskStatus.REJECTED;
    }

    public void assertInspectorOwns(InspectionTask task, Actor actor) {
        if (task.getInspectorId() == null || !task.getInspectorId().equals(actor.requireUserId())) {
            throw BusinessException.forbidden("该任务未分配给当前质检员");
        }
    }

    public void assertCanView(InspectionTask task, Actor actor) {
        if (actor.isAdmin() || actor.isReviewer()) {
            return;
        }
        assertInspectorOwns(task, actor);
    }

    private void assertNotSelfReview(InspectionTask task, Actor actor) {
        if (task.getInspectorId() != null && task.getInspectorId().equals(actor.requireUserId())) {
            throw BusinessException.forbidden("不能审核自己标注的任务");
        }
    }

    private void checkVersion(InspectionTask task, Integer expected) {
        if (expected == null || expected != task.getVersion()) {
            throw BusinessException.conflict(
                    "任务已被其他用户修改（当前版本 " + task.getVersion() + "，请求版本 " + expected + "），请刷新后重试");
        }
    }

    private void assertTransition(InspectionTask task, TaskStatus target) {
        if (!task.getStatus().canTransitionTo(target)) {
            throw BusinessException.conflict(
                    "非法状态转换：" + task.getStatus() + " → " + target);
        }
    }

    private boolean userHasRole(Long userId, String code) {
        return userRepository.findIdsByRoleCode(code).contains(userId);
    }

    private String normalize(String comment) {
        if (comment == null) return null;
        String trimmed = comment.trim();
        return trimmed.length() > 500 ? trimmed.substring(0, 500) : trimmed;
    }

    public record TaskResponse(Long id, Long imageId, Long inspectorId, Long reviewerId,
                               String status, int version, String remark,
                               java.time.LocalDateTime createdAt, java.time.LocalDateTime updatedAt) {
        static TaskResponse from(InspectionTask t) {
            return new TaskResponse(t.getId(), t.getImageId(), t.getInspectorId(), t.getReviewerId(),
                    t.getStatus().name(), t.getVersion(), t.getRemark(), t.getCreatedAt(), t.getUpdatedAt());
        }
    }
}
