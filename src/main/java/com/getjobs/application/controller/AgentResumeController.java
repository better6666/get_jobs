package com.getjobs.application.controller;

import com.getjobs.application.entity.ResumeVersionEntity;
import com.getjobs.application.service.ResumeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/agent/resumes")
@CrossOrigin(origins = "*")
@Slf4j
@RequiredArgsConstructor
public class AgentResumeController {

    private final ResumeService resumeService;

    @GetMapping
    public ResponseEntity<Map<String, Object>> listResumes() {
        Map<String, Object> res = new HashMap<>();
        try {
            List<ResumeVersionEntity> list = resumeService.listAllResumes();
            res.put("success", true);
            res.put("data", list);
            return ResponseEntity.ok(res);
        } catch (Exception e) {
            log.error("获取简历版本列表失败", e);
            res.put("success", false);
            res.put("message", e.getMessage());
            return ResponseEntity.internalServerError().body(res);
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> getResume(@PathVariable Long id) {
        Map<String, Object> res = new HashMap<>();
        try {
            ResumeVersionEntity entity = resumeService.getResumeById(id);
            res.put("success", true);
            res.put("data", entity);
            return ResponseEntity.ok(res);
        } catch (Exception e) {
            log.error("获取简历详情失败", e);
            res.put("success", false);
            res.put("message", e.getMessage());
            return ResponseEntity.internalServerError().body(res);
        }
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> saveResume(@RequestBody ResumeVersionEntity entity) {
        Map<String, Object> res = new HashMap<>();
        try {
            ResumeVersionEntity saved = resumeService.saveOrUpdate(entity);
            res.put("success", true);
            res.put("data", saved);
            res.put("message", "简历版本保存成功");
            return ResponseEntity.ok(res);
        } catch (Exception e) {
            log.error("保存简历版本失败", e);
            res.put("success", false);
            res.put("message", e.getMessage());
            return ResponseEntity.internalServerError().body(res);
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, Object>> deleteResume(@PathVariable Long id) {
        Map<String, Object> res = new HashMap<>();
        try {
            boolean ok = resumeService.delete(id);
            res.put("success", ok);
            res.put("message", ok ? "删除成功" : "删除失败");
            return ResponseEntity.ok(res);
        } catch (Exception e) {
            log.error("删除简历版本失败", e);
            res.put("success", false);
            res.put("message", e.getMessage());
            return ResponseEntity.internalServerError().body(res);
        }
    }
}
