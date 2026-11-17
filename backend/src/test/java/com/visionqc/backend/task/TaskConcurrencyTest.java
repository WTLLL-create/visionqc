package com.visionqc.backend.task;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.visionqc.backend.review.ReviewRecordRepository;
import com.visionqc.backend.support.TestSupport;
import com.visionqc.backend.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 真实数据库并发测试：
 * 1) 两个审核员同时审批同一任务，只允许一个成功，另一个 409，审核记录只写一条；
 * 2) 两个 JPA 事务先后读取同一版本后提交，后提交者触发 OptimisticLockingFailureException，
 *    证明冲突来自数据库版本列，而不是异常处理器伪造的 409。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class TaskConcurrencyTest {

    @Autowired MockMvc mockMvc;
    @Autowired TestSupport support;
    @Autowired ObjectMapper objectMapper;
    @Autowired TaskRepository taskRepository;
    @Autowired ReviewRecordRepository reviewRecordRepository;
    @Autowired PlatformTransactionManager transactionManager;

    private User admin;
    private User reviewer1;
    private User reviewer2;
    private Long taskId;

    @BeforeEach
    void setUp() throws Exception {
        support.cleanBusinessTables();
        admin = support.createUser("ADMIN");
        reviewer1 = support.createUser("REVIEWER");
        reviewer2 = support.createUser("REVIEWER");

        var batch = support.createBatch();
        String body = mockMvc.perform(multipart("/api/v1/images/upload/" + batch.getId())
                        .file(new MockMultipartFile("files", "p.png", "image/png",
                                TestSupport.pngBytes(800, 600)))
                        .with(user(TestSupport.appUser(admin, "ADMIN"))).with(csrf()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        taskId = objectMapper.readTree(body).get(0).get("taskId").asLong();

        // 直接推进到 SUBMITTED（审批并发不需要重复完整流程，完整流程由 ReviewWorkflowIntegrationTest 覆盖）
        InspectionTask task = taskRepository.findById(taskId).orElseThrow();
        task.setStatus(TaskStatus.SUBMITTED);
        taskRepository.saveAndFlush(task);
    }

    private int currentVersion() {
        return taskRepository.findById(taskId).orElseThrow().getVersion();
    }

    @Test
    void 两个审核员同时审批仅一人成功且只写一条审核记录() throws Exception {
        int version = currentVersion();
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        Callable<Integer> call = () -> {
            ready.countDown();
            start.await(10, TimeUnit.SECONDS);
            return mockMvc.perform(post("/api/v1/tasks/{id}/approve", taskId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"version\":" + version + ",\"comment\":\"并发审批\"}")
                            .with(user(TestSupport.appUser(reviewer1, "REVIEWER"))).with(csrf()))
                    .andReturn().getResponse().getStatus();
        };
        // 第二个线程使用 reviewer2：用闭包变量区分
        Callable<Integer> call2 = () -> {
            ready.countDown();
            start.await(10, TimeUnit.SECONDS);
            return mockMvc.perform(post("/api/v1/tasks/{id}/approve", taskId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"version\":" + version + ",\"comment\":\"并发审批\"}")
                            .with(user(TestSupport.appUser(reviewer2, "REVIEWER"))).with(csrf()))
                    .andReturn().getResponse().getStatus();
        };

        Future<Integer> f1 = pool.submit(call);
        Future<Integer> f2 = pool.submit(call2);
        ready.await(10, TimeUnit.SECONDS);
        start.countDown();
        int s1 = f1.get(30, TimeUnit.SECONDS);
        int s2 = f2.get(30, TimeUnit.SECONDS);
        pool.shutdownNow();

        assertThat(List.of(s1, s2)).containsExactlyInAnyOrder(200, 409);
        assertThat(taskRepository.findById(taskId).orElseThrow().getStatus()).isEqualTo(TaskStatus.APPROVED);
        assertThat(reviewRecordRepository.findByTaskIdOrderByIdAsc(taskId).stream()
                .filter(r -> "APPROVE".equals(r.getAction())).count()).isEqualTo(1);
    }

    @Test
    void JPA乐观锁_后提交事务因版本过期回滚() throws Exception {
        CountDownLatch loaded = new CountDownLatch(1);
        CountDownLatch firstCommitted = new CountDownLatch(1);
        AtomicReference<Throwable> firstError = new AtomicReference<>();
        TransactionTemplate txTemplate = new TransactionTemplate(transactionManager);

        Thread t1 = new Thread(() -> {
            try {
                txTemplate.executeWithoutResult(tx -> {
                    InspectionTask task = taskRepository.findById(taskId).orElseThrow();
                    loaded.countDown();
                    try {
                        firstCommitted.await(10, TimeUnit.SECONDS);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    task.setStatus(TaskStatus.APPROVED);
                    taskRepository.saveAndFlush(task); // 版本已被第二个事务改掉，这里应抛乐观锁异常
                });
            } catch (Throwable e) {
                firstError.set(e);
            }
        });
        Thread t2 = new Thread(() -> {
            try {
                loaded.await(10, TimeUnit.SECONDS);
                txTemplate.executeWithoutResult(tx -> {
                    InspectionTask task = taskRepository.findById(taskId).orElseThrow();
                    task.setStatus(TaskStatus.REJECTED);
                    taskRepository.saveAndFlush(task);
                });
            } catch (Throwable ignored) {
                // t2 先提交，正常情况下不失败
            } finally {
                firstCommitted.countDown();
            }
        });

        t1.start();
        t2.start();
        t1.join(20_000);
        t2.join(20_000);

        assertThat(firstError.get())
                .as("第一个事务应因版本冲突失败")
                .isInstanceOf(OptimisticLockingFailureException.class);
        assertThat(taskRepository.findById(taskId).orElseThrow().getStatus()).isEqualTo(TaskStatus.REJECTED);
    }
}
