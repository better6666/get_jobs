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
@RequestMapping("/api/agent")
@CrossOrigin(origins = "*")
@Slf4j
@RequiredArgsConstructor
public class AgentApplicationController {

    private final ApplicationRecordService recordService;

    @GetMapping("/applications")
    public ResponseEntity<Map<String, Object>> listApplications(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String platform,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "15") int size
    ) {
        Map<String, Object> res = new HashMap<>();
        try {
            Page<ApplicationRecordEntity> result = recordService.listApplications(status, platform, search, page, size);
            res.put("success", true);
            res.put("data", result.getRecords());
            res.put("total", result.getTotal());
            res.put("current", result.getCurrent());
            res.put("pages", result.getPages());
            return ResponseEntity.ok(res);
        } catch (Exception e) {
            log.error("获取投递流转记录失败", e);
            res.put("success", false);
            res.put("message", e.getMessage());
            return ResponseEntity.internalServerError().body(res);
        }
    }

    @PostMapping("/applications/{id}/status")
    public ResponseEntity<Map<String, Object>> updateStatus(
            @PathVariable Long id,
            @RequestBody Map<String, String> body
    ) {
        Map<String, Object> res = new HashMap<>();
        try {
            String status = body.get("status");
            String hrReply = body.get("hrReply");
            String interviewStatus = body.get("interviewStatus");
            String notes = body.get("notes");

            boolean ok = recordService.updateStatus(id, status, hrReply, interviewStatus, notes);
            res.put("success", ok);
            res.put("message", ok ? "流转状态更新成功" : "更新失败");
            return ResponseEntity.ok(res);
        } catch (Exception e) {
            log.error("更新投递状态失败", e);
            res.put("success", false);
            res.put("message", e.getMessage());
            return ResponseEntity.internalServerError().body(res);
        }
    }

    @GetMapping("/funnel")
    public ResponseEntity<Map<String, Object>> getFunnel() {
        Map<String, Object> res = new HashMap<>();
        try {
            Map<String, Object> funnel = recordService.getFunnelStats();
            res.put("success", true);
            res.put("data", funnel);
            return ResponseEntity.ok(res);
        } catch (Exception e) {
            log.error("获取求职漏斗统计失败", e);
            res.put("success", false);
            res.put("message", e.getMessage());
            return ResponseEntity.internalServerError().body(res);
        }
    }
}
