package com.visionqc.backend.device;

import com.visionqc.backend.common.BusinessException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DeviceService {

    private final DeviceRepository deviceRepository;

    public DeviceService(DeviceRepository deviceRepository) {
        this.deviceRepository = deviceRepository;
    }

    @Transactional
    public DeviceResponse create(DeviceRequest req) {
        if (deviceRepository.existsByDeviceCodeAndDeletedFalse(req.deviceCode())) {
            throw BusinessException.conflict("设备编码已存在: " + req.deviceCode());
        }
        Device d = new Device();
        d.setDeviceName(req.deviceName());
        d.setDeviceCode(req.deviceCode());
        d.setDescription(req.description());
        return DeviceResponse.from(deviceRepository.save(d));
    }

    @Transactional
    public DeviceResponse update(Long id, DeviceRequest req) {
        Device d = getEntity(id);
        // deviceCode 是对接产线的稳定业务编码，创建后不可修改（前端编辑框同步禁用）
        if (req.deviceName() != null) d.setDeviceName(req.deviceName());
        if (req.description() != null) d.setDescription(req.description());
        return DeviceResponse.from(deviceRepository.save(d));
    }

    @Transactional
    public DeviceResponse changeStatus(Long id, boolean enable) {
        Device d = getEntity(id);
        d.setDeviceStatus(enable ? "ENABLED" : "DISABLED");
        return DeviceResponse.from(deviceRepository.save(d));
    }

    @Transactional
    public void delete(Long id) {
        // 逻辑删除，保住批次/图片的历史引用（任务书 6.4：删除操作与历史引用）
        Device d = getEntity(id);
        d.setDeleted(true);
        deviceRepository.save(d);
    }

    @Transactional(readOnly = true)
    public Page<DeviceResponse> search(String keyword, Pageable pageable) {
        if (keyword == null || keyword.isBlank()) {
            return deviceRepository.findByDeletedFalse(pageable).map(DeviceResponse::from);
        }
        return deviceRepository.search(keyword.trim(), pageable).map(DeviceResponse::from);
    }

    @Transactional(readOnly = true)
    public DeviceResponse get(Long id) {
        return DeviceResponse.from(getEntity(id));
    }

    private Device getEntity(Long id) {
        return deviceRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> BusinessException.notFound("设备不存在: " + id));
    }
}
