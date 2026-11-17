package com.visionqc.backend.annotation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.visionqc.backend.support.TestSupport;
import com.visionqc.backend.task.InspectionTask;
import com.visionqc.backend.task.TaskRepository;
import com.visionqc.backend.task.TaskStatus;
import com.visionqc.backend.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 标注集成测试：验证归一化坐标保存/恢复精度、越界拒绝、全量替换、状态与权限约束。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AnnotationIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired TestSupport support;
    @Autowired TaskRepository taskRepository;
    @Autowired AnnotationRepository annotationRepository;
    @Autowired ObjectMapper objectMapper;

    private Long taskId;
    private User inspector;
    private int imgWidth;
    private int imgHeight;

    private RequestPostProcessor asInspector() {
        return user(TestSupport.appUser(inspector, "INSPECTOR"));
    }

    @BeforeEach
    void setUp() throws Exception {
        support.cleanBusinessTables();
        inspector = support.createUser("INSPECTOR");
        var batch = support.createBatch();
        imgWidth = 800;
        imgHeight = 600;
        MockMultipartFile file = new MockMultipartFile("files", "part.png", "image/png",
                TestSupport.pngBytes(imgWidth, imgHeight));

        String body = mockMvc.perform(multipart("/api/v1/images/upload/" + batch.getId())
                        .file(file)
                        .with(user("admin").roles("ADMIN"))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode node = objectMapper.readTree(body).get(0);
        taskId = node.get("taskId").asLong();
        assertThat(taskId).isNotNull();

        // Phase 3 起质检员只能操作分配给自己的任务
        InspectionTask task = taskRepository.findById(taskId).orElseThrow();
        task.setInspectorId(inspector.getId());
        task.setStatus(TaskStatus.ASSIGNED);
        taskRepository.save(task);
    }

    @Test
    void 保存后重新读取标注坐标完全一致() throws Exception {
        String json = """
                {"annotations":[
                  {"categoryId":1,"x":0.123456,"y":0.234567,"w":0.1,"h":0.2,
                   "imgWidth":800,"imgHeight":600,"note":"第一处划痕"}
                ]}
                """;
        mockMvc.perform(put("/api/v1/tasks/{id}/annotations", taskId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json)
                        .with(asInspector())
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].xNorm").value(0.123456))
                .andExpect(jsonPath("$[0].note").value("第一处划痕"));

        mockMvc.perform(get("/api/v1/tasks/{id}/annotations", taskId)
                        .with(asInspector()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].xNorm").value(0.123456))
                .andExpect(jsonPath("$[0].wNorm").value(0.1));
    }

    @Test
    void 归一化坐标换算回像素与预期一致() throws Exception {
        String json = """
                {"annotations":[
                  {"categoryId":1,"x":0.25,"y":0.5,"w":0.5,"h":0.25,
                   "imgWidth":800,"imgHeight":600,"note":""}
                ]}
                """;
        mockMvc.perform(put("/api/v1/tasks/{id}/annotations", taskId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json)
                        .with(asInspector())
                        .with(csrf()))
                .andExpect(status().isOk());

        Annotation a = annotationRepository.findByTaskIdOrderById(taskId).get(0);
        // 像素坐标 = 归一化 * 原图尺寸（前端恢复逻辑即此公式）
        assertThat(a.getXNorm().doubleValue() * 800).isEqualTo(200.0);
        assertThat(a.getYNorm().doubleValue() * 600).isEqualTo(300.0);
        assertThat(a.getWNorm().doubleValue() * 800).isEqualTo(400.0);
        assertThat(a.getHNorm().doubleValue() * 600).isEqualTo(150.0);
    }

    @Test
    void 全量替换语义删除旧标注() throws Exception {
        mockMvc.perform(put("/api/v1/tasks/{id}/annotations", taskId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"annotations\":[{\"categoryId\":1,\"x\":0.1,\"y\":0.1,\"w\":0.1,\"h\":0.1,\"imgWidth\":800,\"imgHeight\":600}]}")
                        .with(asInspector()).with(csrf()))
                .andExpect(status().isOk());

        mockMvc.perform(put("/api/v1/tasks/{id}/annotations", taskId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"annotations\":[]}")
                        .with(asInspector()).with(csrf()))
                .andExpect(status().isOk());

        assertThat(annotationRepository.findByTaskIdOrderById(taskId)).isEmpty();
    }

    @Test
    void 越界标注被后端拒绝() throws Exception {
        String json = """
                {"annotations":[
                  {"categoryId":1,"x":0.6,"y":0.1,"w":0.5,"h":0.2,"imgWidth":800,"imgHeight":600}
                ]}
                """;
        mockMvc.perform(put("/api/v1/tasks/{id}/annotations", taskId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json)
                        .with(asInspector())
                        .with(csrf()))
                .andExpect(status().isBadRequest());
        assertThat(annotationRepository.findByTaskIdOrderById(taskId)).isEmpty();
    }

    @Test
    void 缺陷类别不存在时拒绝() throws Exception {
        String json = """
                {"annotations":[{"categoryId":9999,"x":0.1,"y":0.1,"w":0.1,"h":0.1,"imgWidth":800,"imgHeight":600}]}
                """;
        mockMvc.perform(put("/api/v1/tasks/{id}/annotations", taskId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json)
                        .with(asInspector())
                        .with(csrf()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void 已提交任务不能修改标注() throws Exception {
        InspectionTask task = taskRepository.findById(taskId).orElseThrow();
        task.setStatus(TaskStatus.SUBMITTED);
        taskRepository.save(task);

        mockMvc.perform(put("/api/v1/tasks/{id}/annotations", taskId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"annotations\":[]}")
                        .with(asInspector()).with(csrf()))
                .andExpect(status().isConflict());
    }

    @Test
    void 审核员可只读查看标注但不可修改() throws Exception {
        mockMvc.perform(get("/api/v1/tasks/{id}/annotations", taskId)
                        .with(user("reviewer").roles("REVIEWER")))
                .andExpect(status().isOk());

        mockMvc.perform(put("/api/v1/tasks/{id}/annotations", taskId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"annotations\":[]}")
                        .with(user("reviewer").roles("REVIEWER")).with(csrf()))
                .andExpect(status().isForbidden());
    }
}
