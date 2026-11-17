package com.visionqc.backend.image;

import com.visionqc.backend.support.TestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 图片上传集成测试（真实 MySQL 测试库 + 真实文件系统 + 完整安全过滤链）。
 * 覆盖任务书 Phase 2 验收：正常上传、非法格式、重复文件、权限拒绝。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ImageUploadIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired TestSupport support;
    @Autowired ImageAssetRepository imageAssetRepository;
    @Autowired com.visionqc.backend.task.TaskRepository taskRepository;
    @Autowired com.visionqc.backend.batch.BatchRepository batchRepository;

    @BeforeEach
    void setUp() {
        support.cleanBusinessTables();
    }

    @Test
    void 管理员上传PNG成功且自动创建任务() throws Exception {
        var batch = support.createBatch();
        byte[] png = TestSupport.pngBytes(320, 200);
        MockMultipartFile file = new MockMultipartFile("files", "part.png", "image/png", png);

        mockMvc.perform(multipart("/api/v1/images/upload/" + batch.getId())
                        .file(file)
                        .with(user("admin").roles("ADMIN"))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].success").value(true))
                .andExpect(jsonPath("$[0].duplicate").value(false));

        assertThat(imageAssetRepository.findAll()).hasSize(1);
        ImageAsset asset = imageAssetRepository.findAll().get(0);
        assertThat(asset.getStatus()).isEqualTo("ACTIVE");
        assertThat(asset.getWidth()).isEqualTo(320);
        assertThat(asset.getHeight()).isEqualTo(200);
        assertThat(taskRepository.findByImageId(asset.getId())).isPresent();

        // 预览接口：真实文件可读
        mockMvc.perform(get("/api/v1/images/{id}/file", asset.getId())
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk());
    }

    @Test
    void 同批次重复文件幂等返回已有记录() throws Exception {
        var batch = support.createBatch();
        byte[] png = TestSupport.pngBytes(100, 100);
        for (int i = 0; i < 2; i++) {
            MockMultipartFile file = new MockMultipartFile("files", "dup.png", "image/png", png);
            mockMvc.perform(multipart("/api/v1/images/upload/" + batch.getId())
                            .file(file)
                            .with(user("admin").roles("ADMIN"))
                            .with(csrf()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].success").value(true))
                    .andExpect(jsonPath("$[0].duplicate").value(i == 1));
        }
        assertThat(imageAssetRepository.findAll()).hasSize(1);
    }

    @Test
    void 非法文本文件被拒绝且不落库() throws Exception {
        var batch = support.createBatch();
        MockMultipartFile txt = new MockMultipartFile("files", "evil.png", "image/png",
                "not an image".getBytes());

        mockMvc.perform(multipart("/api/v1/images/upload/" + batch.getId())
                        .file(txt)
                        .with(user("admin").roles("ADMIN"))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].success").value(false));

        assertThat(imageAssetRepository.findAll()).isEmpty();
    }

    @Test
    void 正常JPEG可上传且尺寸正确() throws Exception {
        var batch = support.createBatch();
        MockMultipartFile file = new MockMultipartFile("files", "part.jpg", "image/jpeg",
                TestSupport.jpegBytes(200, 150));

        mockMvc.perform(multipart("/api/v1/images/upload/" + batch.getId())
                        .file(file)
                        .with(user("admin").roles("ADMIN"))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].success").value(true));

        ImageAsset asset = imageAssetRepository.findAll().get(0);
        assertThat(asset.getWidth()).isEqualTo(200);
        assertThat(asset.getHeight()).isEqualTo(150);
        assertThat(asset.getContentType()).isEqualTo("image/jpeg");
    }

    @Test
    void 带EXIF旋转标记的JPEG被拒绝() throws Exception {
        var batch = support.createBatch();
        MockMultipartFile file = new MockMultipartFile("files", "rotated.jpg", "image/jpeg",
                TestSupport.withExifOrientation(TestSupport.jpegBytes(200, 150), 6));

        mockMvc.perform(multipart("/api/v1/images/upload/" + batch.getId())
                        .file(file)
                        .with(user("admin").roles("ADMIN"))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].success").value(false))
                .andExpect(jsonPath("$[0].message").value(org.hamcrest.Matchers.containsString("EXIF")));

        assertThat(imageAssetRepository.findAll()).isEmpty();
    }

    @Test
    void 超出解码像素上限的图片被拒绝() throws Exception {
        var batch = support.createBatch();
        // 测试配置上限 50 万像素，1000x600=60 万
        MockMultipartFile file = new MockMultipartFile("files", "huge.png", "image/png",
                TestSupport.pngBytes(1000, 600));

        mockMvc.perform(multipart("/api/v1/images/upload/" + batch.getId())
                        .file(file)
                        .with(user("admin").roles("ADMIN"))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].success").value(false))
                .andExpect(jsonPath("$[0].message").value(org.hamcrest.Matchers.containsString("分辨率")));

        assertThat(imageAssetRepository.findAll()).isEmpty();
    }

    @Test
    void 质检员调用管理员上传接口被拒绝() throws Exception {
        var batch = support.createBatch();
        MockMultipartFile file = new MockMultipartFile("files", "a.png", "image/png",
                TestSupport.pngBytes(50, 50));

        mockMvc.perform(multipart("/api/v1/images/upload/" + batch.getId())
                        .file(file)
                        .with(user("inspector").roles("INSPECTOR"))
                        .with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    void 未登录访问图片接口被拒绝() throws Exception {
        mockMvc.perform(get("/api/v1/images"))
                .andExpect(status().isForbidden()); // 无认证入口时 Spring 返回 403
    }

    @Test
    void 已关闭批次不允许继续上传() throws Exception {
        var batch = support.createBatch();
        batch.setStatus("CLOSED");
        batchRepository.save(batch);
        mockMvc.perform(multipart("/api/v1/images/upload/" + batch.getId())
                        .file(new MockMultipartFile("files", "a.png", "image/png", TestSupport.pngBytes(10, 10)))
                        .with(user("admin").roles("ADMIN"))
                        .with(csrf()))
                .andExpect(status().is4xxClientError());
    }
}
