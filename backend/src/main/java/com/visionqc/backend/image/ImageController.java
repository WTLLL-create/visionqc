package com.visionqc.backend.image;

import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.visionqc.backend.common.PageResult;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/api/v1/images")
public class ImageController {

    private final ImageService imageService;

    public ImageController(ImageService imageService) {
        this.imageService = imageService;
    }

    /** 多图上传：逐文件返回成功/重复/失败结果，单个文件失败不影响其他文件 */
    @PostMapping("/upload/{batchId}")
    public List<ImageService.UploadResult> upload(@PathVariable Long batchId,
                                                  @RequestParam("files") MultipartFile[] files) {
        return imageService.upload(batchId, files);
    }

    @GetMapping
    public PageResult<ImageResponse> list(
            @RequestParam(required = false) Long batchId,
            @org.springframework.data.web.PageableDefault(size = 20) org.springframework.data.domain.Pageable pageable) {
        return imageService.search(batchId, pageable);
    }

    @GetMapping("/{id}")
    public ImageResponse get(@PathVariable Long id) {
        return imageService.get(id);
    }

    /** 图片二进制输出（流式），仅登录用户可访问，实际文件路径由服务端解析 */
    @GetMapping("/{id}/file")
    public ResponseEntity<Resource> file(@PathVariable Long id) {
        ImageAsset meta = imageService.getActiveEntity(id);
        Path path = imageService.resolveFile(id);
        Resource resource = new FileSystemResource(path);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(meta.getContentType()))
                .cacheControl(CacheControl.maxAge(1, TimeUnit.HOURS).cachePrivate())
                .body(resource);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        imageService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
