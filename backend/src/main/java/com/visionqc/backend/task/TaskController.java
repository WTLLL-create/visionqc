package com.visionqc.backend.task;

import com.visionqc.backend.common.PageResult;
import com.visionqc.backend.review.ReviewRecordResponse;
import com.visionqc.backend.security.Actor;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/tasks")
public class TaskController {

    private final TaskService taskService;

    public TaskController(TaskService taskService) {
        this.taskService = taskService;
    }

    @GetMapping
    public PageResult<TaskService.TaskResponse> list(
            @RequestParam(required = false) TaskStatus status,
            @RequestParam(required = false) Long batchId,
            @PageableDefault(size = 20) Pageable pageable,
            Authentication auth) {
        return taskService.list(status, batchId, pageable, Actor.from(auth));
    }

    @GetMapping("/{id}")
    public TaskService.TaskResponse get(@PathVariable Long id, Authentication auth) {
        return taskService.get(id, Actor.from(auth));
    }

    @GetMapping("/{id}/history")
    public List<ReviewRecordResponse> history(@PathVariable Long id, Authentication auth) {
        return taskService.history(id, Actor.from(auth));
    }

    @PostMapping("/{id}/assign")
    public TaskService.TaskResponse assign(@PathVariable Long id,
                                           @Valid @RequestBody AssignRequest req,
                                           Authentication auth) {
        return taskService.assign(id, req, Actor.from(auth));
    }

    @PostMapping("/{id}/start")
    public TaskService.TaskResponse start(@PathVariable Long id,
                                          @Valid @RequestBody TaskActionRequest req,
                                          Authentication auth) {
        return taskService.start(id, req, Actor.from(auth));
    }

    @PostMapping("/{id}/submit")
    public TaskService.TaskResponse submit(@PathVariable Long id,
                                          @Valid @RequestBody TaskActionRequest req,
                                          Authentication auth) {
        return taskService.submit(id, req, Actor.from(auth));
    }

    @PostMapping("/{id}/approve")
    public TaskService.TaskResponse approve(@PathVariable Long id,
                                           @Valid @RequestBody TaskActionRequest req,
                                           Authentication auth) {
        return taskService.approve(id, req, Actor.from(auth));
    }

    @PostMapping("/{id}/reject")
    public TaskService.TaskResponse reject(@PathVariable Long id,
                                          @Valid @RequestBody TaskActionRequest req,
                                          Authentication auth) {
        return taskService.reject(id, req, Actor.from(auth));
    }
}
