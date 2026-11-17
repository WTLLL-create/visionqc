package com.visionqc.backend.image;

import com.visionqc.backend.batch.BatchRepository;
import com.visionqc.backend.common.BusinessException;
import com.visionqc.backend.common.PageResult;
import com.visionqc.backend.storage.FileStorageService;
import com.visionqc.backend.task.InspectionTask;
import com.visionqc.backend.task.TaskRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 图片上传编排（任务书第六节 6.4 + 第十节一致性）：
 * 暂存文件 → 校验真实格式/尺寸 → SHA-256 去重 → DB(STAGED) → 原子移动 → DB(ACTIVE)。
 * 失败路径全部可追踪：非法文件不落库；移动失败标记 FAILED 并清理暂存。
 */
@Service
public class ImageService {

    private static final Logger log = LoggerFactory.getLogger(ImageService.class);

    private final ImageAssetRepository imageAssetRepository;
    private final ImageTransactionalOps ops;
    private final FileStorageService storage;
    private final BatchRepository batchRepository;
    private final TaskRepository taskRepository;

    @Value("${app.max-image-bytes:10485760}")
    private long maxImageBytes;

    /** 最大解码像素数：防止 10MB 压缩图解压出几百 MB 位图（decompression bomb） */
    @Value("${app.max-image-pixels:25000000}")
    private long maxImagePixels;

    public ImageService(ImageAssetRepository imageAssetRepository,
                        ImageTransactionalOps ops,
                        FileStorageService storage,
                        BatchRepository batchRepository,
                        TaskRepository taskRepository) {
        this.imageAssetRepository = imageAssetRepository;
        this.ops = ops;
        this.storage = storage;
        this.batchRepository = batchRepository;
        this.taskRepository = taskRepository;
    }

    public List<UploadResult> upload(Long batchId, MultipartFile[] files) {
        var batch = batchRepository.findByIdAndDeletedFalse(batchId)
                .orElseThrow(() -> BusinessException.notFound("批次不存在: " + batchId));
        if ("CLOSED".equals(batch.getStatus())) {
            throw BusinessException.conflict("批次已关闭，不能继续上传: " + batch.getBatchCode());
        }

        List<UploadResult> results = new ArrayList<>();
        for (MultipartFile file : files) {
            results.add(storeOne(batchId, file));
        }
        return results;
    }

    private UploadResult storeOne(Long batchId, MultipartFile file) {
        if (file.isEmpty()) {
            return UploadResult.fail(file.getOriginalFilename(), "空文件");
        }
        if (file.getSize() > maxImageBytes) {
            return UploadResult.fail(file.getOriginalFilename(),
                    "文件超过大小限制 " + (maxImageBytes / 1024 / 1024) + "MB");
        }

        String tempName = UUID.randomUUID() + ".tmp";
        Path staged = null;
        try (InputStream in = file.getInputStream()) {
            staged = storage.saveToStaging(in, tempName);
        } catch (IOException e) {
            log.warn("Save staging failed", e);
            return UploadResult.fail(file.getOriginalFilename(), "文件写入失败");
        }

        try {
            // 真实格式识别：不信任客户端 Content-Type 与扩展名，防止伪装图片上传
            String format = storage.detectImageFormat(staged);
            if (format == null) {
                storage.deleteQuietly(staged);
                return UploadResult.fail(file.getOriginalFilename(), "不是有效的 JPG/PNG 图片");
            }
            int[] size = storage.readImageSize(staged);
            if (size == null || size[0] <= 0 || size[1] <= 0) {
                storage.deleteQuietly(staged);
                return UploadResult.fail(file.getOriginalFilename(), "图片已损坏或无法解析");
            }
            long pixels = (long) size[0] * size[1];
            if (pixels > maxImagePixels) {
                storage.deleteQuietly(staged);
                return UploadResult.fail(file.getOriginalFilename(),
                        "图片分辨率过高（" + size[0] + "×" + size[1] + "，上限 "
                                + maxImagePixels + " 像素），请压缩后上传");
            }
            if ("jpg".equals(format)) {
                // Java ImageIO 不会按 EXIF 旋转，浏览器会；带方向标记的图会导致标注坐标错位
                int orientation = storage.readExifOrientation(staged);
                if (orientation != 1) {
                    storage.deleteQuietly(staged);
                    return UploadResult.fail(file.getOriginalFilename(),
                            "图片带 EXIF 旋转标记（方向 " + orientation + "），请先转为正向图片后上传");
                }
            }
            // 头部合法不代表像素数据完整：解码一次并把失败文件清理掉
            if (!storage.decodeMatches(staged, size[0], size[1])) {
                storage.deleteQuietly(staged);
                return UploadResult.fail(file.getOriginalFilename(), "图片像素数据损坏，无法解码");
            }

            String sha256 = storage.sha256(staged);

            // 批次内重复上传：幂等返回已有记录，不产生新文件
            Optional<ImageAsset> existing = imageAssetRepository.findFirstByBatchIdAndSha256(batchId, sha256);
            if (existing.isPresent()) {
                storage.deleteQuietly(staged);
                ImageAsset asset = existing.get();
                if ("FAILED".equals(asset.getStatus())) {
                    return UploadResult.fail(file.getOriginalFilename(), "该文件此前上传失败，请联系管理员清理后重试");
                }
                return UploadResult.duplicate(asset);
            }

            String fileKey = UUID.randomUUID() + "." + format;
            ImageAsset asset = new ImageAsset();
            asset.setBatchId(batchId);
            asset.setFileKey(fileKey);
            asset.setOriginalName(sanitizeName(file.getOriginalFilename()));
            asset.setSha256(sha256);
            asset.setWidth(size[0]);
            asset.setHeight(size[1]);
            asset.setSizeBytes(file.getSize());
            asset.setContentType("png".equals(format) ? "image/png" : "image/jpeg");
            ImageAsset saved = ops.saveStaged(asset);

            try {
                storage.promoteToFinal(staged, batchId, fileKey);
            } catch (IOException e) {
                log.error("Promote file failed, imageId={}", saved.getId(), e);
                ops.markFailed(saved.getId());
                storage.deleteQuietly(staged);
                return UploadResult.fail(file.getOriginalFilename(), "文件落盘失败，已回滚记录状态");
            }

            ops.markActive(saved.getId());
            InspectionTask task = taskRepository.findByImageId(saved.getId()).orElseGet(() -> {
                InspectionTask t = new InspectionTask();
                t.setImageId(saved.getId());
                return taskRepository.save(t);
            });
            return UploadResult.success(saved.getId(), task.getId());
        } catch (IOException e) {
            log.warn("Upload processing failed", e);
            storage.deleteQuietly(staged);
            return UploadResult.fail(file.getOriginalFilename(), "上传处理失败");
        }
    }

    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public PageResult<ImageResponse> search(Long batchId, Pageable pageable) {
        Page<ImageAsset> page = imageAssetRepository.search(batchId, pageable);
        Map<Long, Long> taskIds = taskRepository.findByImageIdIn(
                        page.getContent().stream().map(ImageAsset::getId).toList()).stream()
                .collect(Collectors.toMap(InspectionTask::getImageId, InspectionTask::getId));
        return PageResult.of(page.map(a -> toResponse(a, taskIds.get(a.getId()))));
    }

    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public ImageResponse get(Long id) {
        ImageAsset asset = imageAssetRepository.findByIdAndStatus(id, "ACTIVE")
                .orElseThrow(() -> BusinessException.notFound("图片不存在或未激活: " + id));
        Long taskId = taskRepository.findByImageId(id).map(InspectionTask::getId).orElse(null);
        return toResponse(asset, taskId);
    }

    /** 读取图片文件：仅 ACTIVE 状态可访问，路径由服务端解析（防穿越） */
    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public Path resolveFile(Long id) {
        ImageAsset asset = imageAssetRepository.findByIdAndStatus(id, "ACTIVE")
                .orElseThrow(() -> BusinessException.notFound("图片不存在或未激活: " + id));
        Path file = storage.resolveFinal(asset.getBatchId(), asset.getFileKey());
        if (!java.nio.file.Files.isReadable(file)) {
            throw BusinessException.notFound("图片文件缺失，请联系管理员: " + id);
        }
        return file;
    }

    public ImageAsset getActiveEntity(Long id) {
        return imageAssetRepository.findByIdAndStatus(id, "ACTIVE")
                .orElseThrow(() -> BusinessException.notFound("图片不存在或未激活: " + id));
    }

    public void delete(Long id) {
        ops.markDeleted(id);
    }

    private ImageResponse toResponse(ImageAsset a, Long taskId) {
        return new ImageResponse(a.getId(), a.getBatchId(), taskId, a.getOriginalName(),
                a.getContentType(), a.getWidth(), a.getHeight(), a.getSizeBytes(),
                a.getSha256(), a.getStatus(), a.getCreatedAt());
    }

    /** 文件名只做展示字段：截断、去路径分隔符，不参与存储路径 */
    private String sanitizeName(String name) {
        if (name == null || name.isBlank()) {
            return "unnamed";
        }
        String cleaned = name.replaceAll("[\\\\/]", "_");
        return cleaned.length() > 200 ? cleaned.substring(0, 200) : cleaned;
    }

    public record UploadResult(Long imageId, Long taskId, String originalName,
                               boolean duplicate, boolean success, String message) {
        static UploadResult success(Long imageId, Long taskId) {
            return new UploadResult(imageId, taskId, null, false, true, null);
        }
        static UploadResult duplicate(ImageAsset a) {
            return new UploadResult(a.getId(), null, a.getOriginalName(), true, true, "重复文件，已返回已有记录");
        }
        static UploadResult fail(String name, String message) {
            return new UploadResult(null, null, name, false, false, message);
        }
    }
}
