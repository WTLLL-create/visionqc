package com.visionqc.backend.review;

import com.visionqc.backend.security.Actor;
import com.visionqc.backend.task.InspectionTask;
import com.visionqc.backend.task.TaskStatus;
import com.visionqc.backend.user.User;
import com.visionqc.backend.user.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 审核历史服务。
 * record() 用 MANDATORY 事务传播：必须在状态变更的同一事务内调用，否则直接报错，
 * 从机制上防止“任务状态已提交、审核记录另起事务失败”造成的不一致。
 */
@Service
public class ReviewService {

    private final ReviewRecordRepository reviewRecordRepository;
    private final UserRepository userRepository;

    public ReviewService(ReviewRecordRepository reviewRecordRepository, UserRepository userRepository) {
        this.reviewRecordRepository = reviewRecordRepository;
        this.userRepository = userRepository;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void record(InspectionTask task, Actor actor, String action,
                       TaskStatus from, TaskStatus to, String comment) {
        ReviewRecord record = new ReviewRecord();
        record.setTaskId(task.getId());
        record.setOperatorId(actor.requireUserId());
        record.setAction(action);
        record.setFromStatus(from.name());
        record.setToStatus(to.name());
        record.setComment(normalize(comment));
        record.setVersion(task.getVersion());
        reviewRecordRepository.save(record);
    }

    @Transactional(readOnly = true)
    public List<ReviewRecordResponse> history(Long taskId) {
        return reviewRecordRepository.findByTaskIdOrderByIdAsc(taskId).stream()
                .map(r -> new ReviewRecordResponse(r.getId(), r.getTaskId(), r.getOperatorId(),
                        operatorName(r.getOperatorId()), r.getAction(), r.getFromStatus(),
                        r.getToStatus(), r.getComment(), r.getVersion(), r.getCreatedAt()))
                .toList();
    }

    private String operatorName(Long userId) {
        return userRepository.findById(userId)
                .map(User::getDisplayName)
                .orElse("用户#" + userId);
    }

    private String normalize(String comment) {
        if (comment == null) return null;
        String trimmed = comment.trim();
        return trimmed.length() > 500 ? trimmed.substring(0, 500) : trimmed;
    }
}
