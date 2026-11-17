package com.visionqc.backend.device;

import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.visionqc.backend.common.PageResult;

@RestController
@RequestMapping("/api/v1/devices")
public class DeviceController {

    private final DeviceService deviceService;

    public DeviceController(DeviceService deviceService) {
        this.deviceService = deviceService;
    }

    @GetMapping
    public PageResult<DeviceResponse> list(
            @RequestParam(required = false) String keyword,
            @PageableDefault(size = 20) Pageable pageable) {
        return PageResult.of(deviceService.search(keyword, pageable));
    }

    @GetMapping("/{id}")
    public DeviceResponse get(@PathVariable Long id) {
        return deviceService.get(id);
    }

    @PostMapping
    public DeviceResponse create(@Valid @RequestBody DeviceRequest req) {
        return deviceService.create(req);
    }

    @PutMapping("/{id}")
    public DeviceResponse update(@PathVariable Long id, @Valid @RequestBody DeviceRequest req) {
        return deviceService.update(id, req);
    }

    @PatchMapping("/{id}/status")
    public DeviceResponse changeStatus(@PathVariable Long id, @RequestParam boolean enable) {
        return deviceService.changeStatus(id, enable);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        deviceService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
