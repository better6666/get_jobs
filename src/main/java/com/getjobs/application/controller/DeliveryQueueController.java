package com.getjobs.application.controller;

import com.getjobs.worker.service.DeliveryTaskQueue;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * 全平台投递队列状态查询（供前端轮询按钮真实状态）
 */
@RestController
@RequestMapping("/api/delivery")
@RequiredArgsConstructor
public class DeliveryQueueController {

    private final DeliveryTaskQueue deliveryTaskQueue;

    /**
     * 各平台是否在投递队列中排队
     */
    @GetMapping("/queue")
    public ResponseEntity<Map<String, Object>> queueStatus() {
        Map<String, Object> response = new HashMap<>();
        Map<String, Boolean> queued = new HashMap<>();
        for (String p : new String[]{"boss", "liepin", "51job", "zhilian"}) {
            queued.put(p, deliveryTaskQueue.isQueued(p));
        }
        response.put("success", true);
        response.put("data", queued);
        return ResponseEntity.ok(response);
    }
}
