package com.visionqc.backend.image;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 图片状态写操作独立成 Bean，保证 REQUIRES_NEW 事务真实生效（避免同类自调用失效）。
 * 每次状态变更立即提交，配合文件系统步骤形成"暂存→提交→移动→激活"的可追踪流程。
 */
@Service
public class ImageTransactionalOps {

    private final ImageAssetRepository imageAssetRepository;

    public ImageTransactionalOps(ImageAssetRepository imageAssetRepository) {
        this.imageAssetRepository = imageAssetRepository;
    }

    /** 第一步：数据库先记录 STAGED 元数据（此时文件还在暂存区） */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ImageAsset saveStaged(ImageAsset asset) {
        asset.setStatus("STAGED");
        return imageAssetRepository.saveAndFlush(asset);
    }

    /** 第三步：文件已原子移动到正式目录，标记 ACTIVE 对外可见 */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markActive(Long id) {
        imageAssetRepository.findById(id).ifPresent(a -> {
            a.setStatus("ACTIVE");
            imageAssetRepository.saveAndFlush(a);
        });
    }

    /** 移动失败补偿：标记 FAILED，供清理任务识别 */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markFailed(Long id) {
        imageAssetRepository.findById(id).ifPresent(a -> {
            a.setStatus("FAILED");
            imageAssetRepository.saveAndFlush(a);
        });
    }

    /** 逻辑删除：保留文件与元数据供历史追溯 */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markDeleted(Long id) {
        imageAssetRepository.findById(id).ifPresent(a -> {
            a.setStatus("DELETED");
            imageAssetRepository.saveAndFlush(a);
        });
    }
}
