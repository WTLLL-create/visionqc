package com.visionqc.backend.task;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.visionqc.backend.support.TestSupport;
import com.visionqc.backend.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Phase 3 审核闭环集成测试：真实 MySQL + 完整安全过滤链。
 * 流程：上传 → 分配 → 开始 → 标注 → 提交 → 驳回 → 修改 → 再提交 → 通过 → 历史。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ReviewWorkflowIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired TestSupport support;
    @Autowired ObjectMapper objectMapper;
    @Autowired TaskRepository taskRepository;

    private User admin;
    private User inspector;
    private User reviewer;
    private Long taskId;

    private RequestPostProcessor asAdmin() {
        return user(TestSupport.appUser(admin, "ADMIN"));
    }

    private RequestPostProcessor asInspector() {
        return user(TestSupport.appUser(inspector, "INSPECTOR"));
    }

    private RequestPostProcessor asReviewer() {
        return user(TestSupport.appUser(reviewer, "REVIEWER"));
    }

    @BeforeEach
    void setUp() throws Exception {
        support.cleanBusinessTables();
        admin = support.createUser("ADMIN");
        inspector = support.createUser("INSPECTOR");
        reviewer = support.createUser("REVIEWER");

        var batch = support.createBatch();
        String uploadBody = mockMvc.perform(multipart("/api/v1/images/upload/" + batch.getId())
                        .file(new MockMultipartFile("files", "p.png", "image/png",
                                TestSupport.pngBytes(800, 600)))
                        .with(asAdmin()).with(csrf()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        taskId = objectMapper.readTree(uploadBody).get(0).get("taskId").asLong();
    }

    private JsonNode postAction(String path, RequestPostProcessor actor, int version, String comment,
                                int expectedStatus) throws Exception {
        String body = objectMapper.writeValueAsString(new TaskActionRequest(version, comment));
        var result = mockMvc.perform(post("/api/v1/tasks/{id}/" + path, taskId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
                        .with(actor).with(csrf()))
                .andExpect(status().is(expectedStatus))
                .andReturn().getResponse().getContentAsString();
        return result.isBlank() ? null : objectMapper.readTree(result);
    }

    private JsonNode assign() throws Exception {
        String body = objectMapper.writeValueAsString(new AssignRequest(inspector.getId(), 0, "分配"));
        return objectMapper.readTree(mockMvc.perform(post("/api/v1/tasks/{id}/assign", taskId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
                        .with(asAdmin()).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ASSIGNED"))
                .andReturn().getResponse().getContentAsString());
    }

    private void saveAnnotations() throws Exception {
        String body = """
                {"annotations":[{"categoryId":1,"x":0.1,"y":0.1,"w":0.2,"h":0.2,
                 "imgWidth":800,"imgHeight":600,"note":"划痕 A"}]}
                """;
        mockMvc.perform(put("/api/v1/tasks/{id}/annotations", taskId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
                        .with(asInspector()).with(csrf()))
                .andExpect(status().isOk());
    }

    @Test
    void 完整审核闭环_驳回后修改再提交并通过() throws Exception {
        JsonNode assigned = assign();
        int v1 = assigned.get("version").asInt();

        JsonNode started = postAction("start", asInspector(), v1, "开始标注", 200);
        assertThat(started.get("status").asText()).isEqualTo("IN_PROGRESS");
        int v2 = started.get("version").asInt();

        saveAnnotations();

        JsonNode submitted = postAction("submit", asInspector(), v2, "提交审核", 200);
        assertThat(submitted.get("status").asText()).isEqualTo("SUBMITTED");
        int v3 = submitted.get("version").asInt();

        // 审核员能在待审核列表中看到任务
        mockMvc.perform(get("/api/v1/tasks").param("status", "SUBMITTED").with(asReviewer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(taskId));

        JsonNode rejected = postAction("reject", asReviewer(), v3, "框选边界不清，请重新标注", 200);
        assertThat(rejected.get("status").asText()).isEqualTo("REJECTED");
        int v4 = rejected.get("version").asInt();

        // 驳回后质检员继续修改（REJECTED → IN_PROGRESS）
        JsonNode resumed = postAction("start", asInspector(), v4, "按意见修改", 200);
        int v5 = resumed.get("version").asInt();
        saveAnnotations();

        JsonNode resubmitted = postAction("submit", asInspector(), v5, "已修改", 200);
        int v6 = resubmitted.get("version").asInt();

        JsonNode approved = postAction("approve", asReviewer(), v6, "标注符合要求", 200);
        assertThat(approved.get("status").asText()).isEqualTo("APPROVED");

        // 历史完整且顺序正确
        mockMvc.perform(get("/api/v1/tasks/{id}/history", taskId).with(asInspector()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(7))
                .andExpect(jsonPath("$[0].action").value("ASSIGN"))
                .andExpect(jsonPath("$[3].action").value("REJECT"))
                .andExpect(jsonPath("$[3].comment").value("框选边界不清，请重新标注"))
                .andExpect(jsonPath("$[6].action").value("APPROVE"))
                .andExpect(jsonPath("$[6].operatorId").value(reviewer.getId()));

        // 已通过任务不能绕过页面继续改标注
        mockMvc.perform(put("/api/v1/tasks/{id}/annotations", taskId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"annotations\":[]}")
                        .with(asInspector()).with(csrf()))
                .andExpect(status().isConflict());
    }

    @Test
    void 权限矩阵_质检员不能审批且审核员不能改标注() throws Exception {
        assign();
        postAction("start", asInspector(), 1, null, 200);
        saveAnnotations();
        postAction("submit", asInspector(), 2, null, 200);

        mockMvc.perform(post("/api/v1/tasks/{id}/approve", taskId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\":3}")
                        .with(asInspector()).with(csrf()))
                .andExpect(status().isForbidden());

        mockMvc.perform(put("/api/v1/tasks/{id}/annotations", taskId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"annotations\":[]}")
                        .with(asReviewer()).with(csrf()))
                .andExpect(status().isForbidden());

        User otherInspector = support.createUser("INSPECTOR");
        mockMvc.perform(get("/api/v1/tasks/{id}", taskId)
                        .with(user(TestSupport.appUser(otherInspector, "INSPECTOR"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void 版本过期与驳回原因为空都被拒绝() throws Exception {
        assign();
        postAction("start", asInspector(), 1, null, 200);
        saveAnnotations();
        postAction("submit", asInspector(), 2, null, 200);

        // 当前版本 3，用旧版本 1 审批 → 409
        postAction("approve", asReviewer(), 1, "越权尝试", 409);

        // 驳回不填原因 → 400
        postAction("reject", asReviewer(), 3, "", 400);

        // 合法驳回后仍为 REJECTED，历史里没有空原因记录
        postAction("reject", asReviewer(), 3, "裂纹不清晰", 200);
        assertThat(taskRepository.findById(taskId).orElseThrow().getStatus()).isEqualTo(TaskStatus.REJECTED);
    }
}
