package com.getjobs.application.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.getjobs.application.entity.ApplicationRecordEntity;
import com.getjobs.application.service.ApplicationRecordService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/agent/review-queue")
@CrossOrigin(origins = "*")
@Slf4j
@RequiredArgsConstructor
public class AgentReviewQueueController {

    private final ApplicationRecordService recordService;

    @GetMapping
    public ResponseEntity<Map<String, Object>> getReviewQueue(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        Map<String, Object> res = new HashMap<>();
        try {
            Page<ApplicationRecordEntity> result = recordService.getReviewQueue(page, size);
            res.put("success", true);
            res.put("data", result.getRecords());
            res.put("total", result.getTotal());
            res.put("current", result.getCurrent());
            res.put("pages", result.getPages());
            return ResponseEntity.ok(res);
        } catch (Exception e) {
            log.error("获取复核队列失败", e);
            res.put("success", false);
            res.put("message", e.getMessage());
            return ResponseEntity.internalServerError().body(res);
        }
    }

    @PostMapping("/action")
    public ResponseEntity<Map<String, Object>> handleAction(@RequestBody Map<String, Object> body) {
        Map<String, Object> res = new HashMap<>();
        try {
            Long id = Long.valueOf(String.valueOf(body.get("id")));
            String action = String.valueOf(body.get("action"));
            String customGreeting = body.containsKey("customGreeting") ? String.valueOf(body.get("customGreeting")) : null;

            boolean ok = recordService.handleReviewAction(id, action, customGreeting);
            res.put("success", ok);
            res.put("message", ok ? "操作执行成功" : "操作未生效");
            return ResponseEntity.ok(res);
        } catch (Exception e) {
            log.error("执行复核操作失败", e);
            res.put("success", false);
            res.put("message", e.getMessage());
            return ResponseEntity.internalServerError().body(res);
        }
    }
}
