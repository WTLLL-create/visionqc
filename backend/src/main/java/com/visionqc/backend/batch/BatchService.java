package com.visionqc.backend.batch;

import com.visionqc.backend.common.BusinessException;
import com.visionqc.backend.device.DeviceRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;

@Service
public class BatchService {

    private final BatchRepository batchRepository;
    private final DeviceRepository deviceRepository;

    public BatchService(BatchRepository batchRepository, DeviceRepository deviceRepository) {
        this.batchRepository = batchRepository;
        this.deviceRepository = deviceRepository;
    }

    @Transactional
    public BatchResponse create(BatchRequest req) {
        var device = deviceRepository.findByIdAndDeletedFalse(req.deviceId())
                .orElseThrow(() -> BusinessException.badRequest("关联设备不存在: " + req.deviceId()));
        if (batchRepository.existsByBatchCodeAndDeletedFalse(req.batchCode())) {
            throw BusinessException.conflict("批次编码已存在: " + req.batchCode());
        }
        ProductionBatch b = new ProductionBatch();
        b.setBatchCode(req.batchCode());
        b.setDeviceId(device.getId());
        b.setProductType(req.productType());
        b.setCaptureDate(req.captureDate());
        return BatchResponse.from(batchRepository.save(b), device.getDeviceName());
    }

    @Transactional(readOnly = true)
    public Page<BatchResponse> search(Long deviceId, String status, Pageable pageable) {
        Page<ProductionBatch> page = batchRepository.search(deviceId, status, pageable);
        // 批量取设备名，避免循环内单查（N+1 防范）
        Map<Long, String> deviceNames = new HashMap<>();
        page.getContent().forEach(b -> deviceNames.put(b.getDeviceId(), ""));
        deviceRepository.findAllById(deviceNames.keySet())
                .forEach(d -> deviceNames.put(d.getId(), d.getDeviceName()));
        return page.map(b -> BatchResponse.from(b, deviceNames.get(b.getDeviceId())));
    }

    @Transactional(readOnly = true)
    public BatchResponse get(Long id) {
        ProductionBatch b = getEntity(id);
        String deviceName = deviceRepository.findByIdAndDeletedFalse(b.getDeviceId())
                .map(d -> d.getDeviceName()).orElse("已删除设备");
        return BatchResponse.from(b, deviceName);
    }

    @Transactional
    public BatchResponse close(Long id) {
        ProductionBatch b = getEntity(id);
        if ("CLOSED".equals(b.getStatus())) {
            throw BusinessException.conflict("批次已关闭: " + b.getBatchCode());
        }
        b.setStatus("CLOSED");
        return BatchResponse.from(batchRepository.save(b),
                deviceRepository.findByIdAndDeletedFalse(b.getDeviceId())
                        .map(d -> d.getDeviceName()).orElse(""));
    }

    private ProductionBatch getEntity(Long id) {
        return batchRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> BusinessException.notFound("批次不存在: " + id));
    }
}
