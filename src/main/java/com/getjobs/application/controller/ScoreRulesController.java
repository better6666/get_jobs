package com.getjobs.application.controller;

import com.getjobs.application.entity.ConfigEntity;
import com.getjobs.application.mapper.ConfigMapper;
import com.getjobs.application.service.ScoreRulesService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONObject;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * JD评分规则管理接口：查看/自定义岗位评分标准。
 * 规则保存在 config 表（config_key=SCORE_RULES），是产品级的用户可配置项。
 */
@RestController
@RequestMapping("/api/score/rules")
@RequiredArgsConstructor
@Slf4j
public class ScoreRulesController {

    private final ScoreRulesService scoreRulesService;
    private final ConfigMapper configMapper;

    /** 查看当前生效的评分规则（用户自定义的，未配置时返回内置默认） */
    @GetMapping
    public ResponseEntity<Map<String, Object>> getRules() {
        Map<String, Object> response = new HashMap<>();
        try {
            ConfigEntity entity = configMapper.selectOne(new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<ConfigEntity>()
                    .eq("config_key", "SCORE_RULES").last("LIMIT 1"));
            String rulesJson = entity != null && entity.getConfigValue() != null && !entity.getConfigValue().isBlank()
                    ? entity.getConfigValue()
                    : ScoreRulesService.DEFAULT_RULES_JSON;
            boolean customized = entity != null;
            response.put("success", true);
            response.put("data", new JSONObject(rulesJson).toMap());
            response.put("customized", customized);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "读取评分规则失败: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }

    /** 保存自定义评分规则（JSON 格式，结构见 GET 返回） */
    @PutMapping
    public ResponseEntity<Map<String, Object>> saveRules(@RequestBody String rulesJson) {
        Map<String, Object> response = new HashMap<>();
        try {
            // 先校验 JSON 合法性
            new JSONObject(rulesJson);
            ConfigEntity entity = configMapper.selectOne(new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<ConfigEntity>()
                    .eq("config_key", "SCORE_RULES").last("LIMIT 1"));
            LocalDateTime now = LocalDateTime.now();
            if (entity == null) {
                entity = new ConfigEntity();
                entity.setConfigKey("SCORE_RULES");
                entity.setConfigValue(rulesJson);
                entity.setConfigType("json");
                entity.setCategory("score");
                entity.setDescription("JD评分规则（用户自定义）");
                entity.setCreatedAt(now);
                entity.setUpdatedAt(now);
                configMapper.insert(entity);
            } else {
                entity.setConfigValue(rulesJson);
                entity.setUpdatedAt(now);
                configMapper.updateById(entity);
            }
            response.put("success", true);
            response.put("message", "评分规则已保存，下次投递任务开始时生效");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "保存失败（JSON格式错误？）: " + e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    /** 恢复内置默认规则 */
    @DeleteMapping
    public ResponseEntity<Map<String, Object>> resetRules() {
        Map<String, Object> response = new HashMap<>();
        try {
            configMapper.delete(new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<ConfigEntity>()
                    .eq("config_key", "SCORE_RULES"));
            response.put("success", true);
            response.put("message", "已恢复内置默认规则");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }
}
