package com.getjobs.application.controller;

import com.getjobs.application.entity.CandidateProfileEntity;
import com.getjobs.application.service.CandidateProfileService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/agent/profile")
@CrossOrigin(origins = "*")
@Slf4j
@RequiredArgsConstructor
public class AgentProfileController {

    private final CandidateProfileService profileService;

    @GetMapping
    public ResponseEntity<Map<String, Object>> getProfile() {
        Map<String, Object> res = new HashMap<>();
        try {
            CandidateProfileEntity profile = profileService.getProfile();
            res.put("success", true);
            res.put("data", profile);
            return ResponseEntity.ok(res);
        } catch (Exception e) {
            log.error("获取求职者画像失败", e);
            res.put("success", false);
            res.put("message", e.getMessage());
            return ResponseEntity.internalServerError().body(res);
        }
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> saveProfile(@RequestBody CandidateProfileEntity entity) {
        Map<String, Object> res = new HashMap<>();
        try {
            CandidateProfileEntity saved = profileService.saveOrUpdateProfile(entity);
            res.put("success", true);
            res.put("data", saved);
            res.put("message", "画像保存成功");
            return ResponseEntity.ok(res);
        } catch (Exception e) {
            log.error("保存求职者画像失败", e);
            res.put("success", false);
            res.put("message", e.getMessage());
            return ResponseEntity.internalServerError().body(res);
        }
    }
}
