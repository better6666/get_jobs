package com.getjobs.application.controller;

import com.getjobs.application.entity.AgentStrategyEntity;
import com.getjobs.application.service.AgentStrategyService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/agent/strategy")
@CrossOrigin(origins = "*")
@Slf4j
@RequiredArgsConstructor
public class AgentStrategyController {

    private final AgentStrategyService strategyService;

    @GetMapping
    public ResponseEntity<Map<String, Object>> getStrategy() {
        Map<String, Object> res = new HashMap<>();
        try {
            AgentStrategyEntity strategy = strategyService.getStrategy();
            res.put("success", true);
            res.put("data", strategy);
            return ResponseEntity.ok(res);
        } catch (Exception e) {
            log.error("获取投递策略失败", e);
            res.put("success", false);
            res.put("message", e.getMessage());
            return ResponseEntity.internalServerError().body(res);
        }
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> updateStrategy(@RequestBody AgentStrategyEntity entity) {
        Map<String, Object> res = new HashMap<>();
        try {
            AgentStrategyEntity updated = strategyService.updateStrategy(entity);
            res.put("success", true);
            res.put("data", updated);
            res.put("message", "策略配置已更新");
            return ResponseEntity.ok(res);
        } catch (Exception e) {
            log.error("更新投递策略失败", e);
            res.put("success", false);
            res.put("message", e.getMessage());
            return ResponseEntity.internalServerError().body(res);
        }
    }

    @PostMapping("/switch-mode")
    public ResponseEntity<Map<String, Object>> switchMode(@RequestBody Map<String, String> body) {
        Map<String, Object> res = new HashMap<>();
        try {
            String mode = body.get("mode");
            AgentStrategyEntity updated = strategyService.switchMode(mode);
            res.put("success", true);
            res.put("data", updated);
            res.put("message", "已切换至模式: " + updated.getRunMode());
            return ResponseEntity.ok(res);
        } catch (Exception e) {
            log.error("切换投递模式失败", e);
            res.put("success", false);
            res.put("message", e.getMessage());
            return ResponseEntity.internalServerError().body(res);
        }
    }
}
