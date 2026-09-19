package com.getjobs.application.controller;

import com.getjobs.application.entity.KeywordEntity;
import com.getjobs.application.service.KeywordService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/agent/keywords")
@CrossOrigin(origins = "*")
@Slf4j
@RequiredArgsConstructor
public class AgentKeywordController {

    private final KeywordService keywordService;

    @GetMapping
    public ResponseEntity<Map<String, Object>> listKeywords(@RequestParam(required = false) String category) {
        Map<String, Object> res = new HashMap<>();
        try {
            List<KeywordEntity> list = keywordService.listKeywords(category);
            res.put("success", true);
            res.put("data", list);
            return ResponseEntity.ok(res);
        } catch (Exception e) {
            log.error("获取关键词列表失败", e);
            res.put("success", false);
            res.put("message", e.getMessage());
            return ResponseEntity.internalServerError().body(res);
        }
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> saveKeyword(@RequestBody KeywordEntity entity) {
        Map<String, Object> res = new HashMap<>();
        try {
            KeywordEntity saved = keywordService.saveOrUpdate(entity);
            res.put("success", true);
            res.put("data", saved);
            res.put("message", "关键词保存成功");
            return ResponseEntity.ok(res);
        } catch (Exception e) {
            log.error("保存关键词失败", e);
            res.put("success", false);
            res.put("message", e.getMessage());
            return ResponseEntity.internalServerError().body(res);
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, Object>> deleteKeyword(@PathVariable Long id) {
        Map<String, Object> res = new HashMap<>();
        try {
            boolean ok = keywordService.delete(id);
            res.put("success", ok);
            res.put("message", ok ? "删除成功" : "删除失败");
            return ResponseEntity.ok(res);
        } catch (Exception e) {
            log.error("删除关键词失败", e);
            res.put("success", false);
            res.put("message", e.getMessage());
            return ResponseEntity.internalServerError().body(res);
        }
    }

    @PostMapping("/{id}/toggle")
    public ResponseEntity<Map<String, Object>> toggleActive(@PathVariable Long id, @RequestBody Map<String, Integer> body) {
        Map<String, Object> res = new HashMap<>();
        try {
            Integer isActive = body.getOrDefault("isActive", 1);
            boolean ok = keywordService.toggleActive(id, isActive);
            res.put("success", ok);
            res.put("message", ok ? "状态切换成功" : "切换失败");
            return ResponseEntity.ok(res);
        } catch (Exception e) {
            log.error("切换关键词状态失败", e);
            res.put("success", false);
            res.put("message", e.getMessage());
            return ResponseEntity.internalServerError().body(res);
        }
    }
}
