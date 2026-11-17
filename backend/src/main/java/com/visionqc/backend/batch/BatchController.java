package com.visionqc.backend.batch;

import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.visionqc.backend.common.PageResult;

@RestController
@RequestMapping("/api/v1/batches")
public class BatchController {

    private final BatchService batchService;

    public BatchController(BatchService batchService) {
        this.batchService = batchService;
    }

    @GetMapping
    public PageResult<BatchResponse> list(
            @RequestParam(required = false) Long deviceId,
            @RequestParam(required = false) String status,
            @PageableDefault(size = 20) Pageable pageable) {
        return PageResult.of(batchService.search(deviceId, status, pageable));
    }

    @GetMapping("/{id}")
    public BatchResponse get(@PathVariable Long id) {
        return batchService.get(id);
    }

    @PostMapping
    public BatchResponse create(@Valid @RequestBody BatchRequest req) {
        return batchService.create(req);
    }

    @PostMapping("/{id}/close")
    public BatchResponse close(@PathVariable Long id) {
        return batchService.close(id);
    }
}
