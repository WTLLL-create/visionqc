package com.visionqc.backend.storage;

import com.visionqc.backend.image.ImageAsset;
import com.visionqc.backend.image.ImageAssetRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 一致性兜底清理：数据库事务无法回滚文件系统，用定时任务回收孤儿数据。
 * - STAGED 超过 1 小时：文件未成功落盘（进程中断/移动失败前的残留），标记 FAILED 并删文件；
 * - FAILED 超过 24 小时：清理记录与文件，避免脏数据长期占用。
 */
@Component
public class StorageCleanupJob {

    private static final Logger log = LoggerFactory.getLogger(StorageCleanupJob.class);

    private final ImageAssetRepository imageAssetRepository;
    private final FileStorageService storage;

    public StorageCleanupJob(ImageAssetRepository imageAssetRepository, FileStorageService storage) {
        this.imageAssetRepository = imageAssetRepository;
        this.storage = storage;
    }

    @Scheduled(fixedDelay = 3600_000L, initialDelay = 600_000L)
    public void cleanup() {
        LocalDateTime oneHourAgo = LocalDateTime.now().minusHours(1);
        List<ImageAsset> stale = imageAssetRepository.findByStatusAndCreatedAtBefore("STAGED", oneHourAgo);
        for (ImageAsset a : stale) {
            log.warn("Cleanup stale STAGED image: id={}, fileKey={}", a.getId(), a.getFileKey());
            a.setStatus("FAILED");
            imageAssetRepository.save(a);
            storage.deleteQuietly(storage.resolveFinal(a.getBatchId(), a.getFileKey()));
        }

        LocalDateTime dayAgo = LocalDateTime.now().minusHours(24);
        List<ImageAsset> failed = imageAssetRepository.findByStatusAndCreatedAtBefore("FAILED", dayAgo);
        for (ImageAsset a : failed) {
            log.warn("Purge old FAILED image record: id={}", a.getId());
            storage.deleteQuietly(storage.resolveFinal(a.getBatchId(), a.getFileKey()));
            imageAssetRepository.deleteById(a.getId());
        }
    }
}
