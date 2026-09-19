package com.getjobs.application.controller;

import com.getjobs.application.entity.AiEntity;
import com.getjobs.application.service.AiService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * AI配置控制器
 * 提供AI配置管理的REST API接口
 */
@RestController
@RequestMapping("/api/ai")
@CrossOrigin(origins = "*")
@Slf4j
public class AiConfigController {


    @Autowired
    private AiService aiService;

    /**
     * 获取AI配置
     * @return AI配置信息
     */
    @GetMapping("/config")
    public ResponseEntity<Map<String, Object>> getAiConfig() {
        Map<String, Object> response = new HashMap<>();

        try {
            AiEntity aiEntity = aiService.getAiConfig();

            response.put("success", true);
            response.put("data", aiEntity);
            response.put("message", "获取AI配置成功");

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("获取AI配置失败", e);
            response.put("success", false);
            response.put("message", "获取AI配置失败: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }

    /**
     * 保存或更新AI配置
     * @param requestBody 请求体包含introduce和prompt
     * @return 保存结果
     */
    @PostMapping("/config")
    public ResponseEntity<Map<String, Object>> saveAiConfig(@RequestBody Map<String, String> requestBody) {
        Map<String, Object> response = new HashMap<>();

        try {
            String introduce = requestBody.get("introduce");
            String prompt = requestBody.get("prompt");

            if (introduce == null || prompt == null) {
                response.put("success", false);
                response.put("message", "参数不完整，introduce和prompt不能为空");
                return ResponseEntity.badRequest().body(response);
            }

            AiEntity aiEntity = aiService.saveOrUpdateAiConfig(introduce, prompt);

            response.put("success", true);
            response.put("data", aiEntity);
            response.put("message", "保存AI配置成功");

            log.info("保存AI配置成功，ID: {}", aiEntity.getId());
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("保存AI配置失败", e);
            response.put("success", false);
            response.put("message", "保存AI配置失败: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }

    /**
     * 健康检查接口
     * @return 服务状态
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> healthCheck() {
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("service", "AiConfigController");
        response.put("status", "healthy");
        response.put("timestamp", System.currentTimeMillis());

        return ResponseEntity.ok(response);
    }

    /**
     * AI 文本生成测试接口（GET）
     * 示例：/api/ai/chat?content=你好，帮我写一句简洁的问候语
     */
    @GetMapping("/chat")
    public ResponseEntity<Map<String, Object>> chat(@RequestParam(name = "content") String content) {
        Map<String, Object> response = new HashMap<>();
        try {
            if (content == null || content.trim().isEmpty()) {
                response.put("success", false);
                response.put("message", "content 参数不能为空");
                return ResponseEntity.badRequest().body(response);
            }

            String reply = aiService.sendRequest(content.trim());
            response.put("success", true);
            response.put("data", reply);
            response.put("message", "AI 请求成功");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("AI 请求失败", e);
            response.put("success", false);
            response.put("message", e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }

    /**
     * 获取可用模型列表（GET）
     * 示例：/api/ai/models?baseUrl=https://api.openai.com&apiKey=sk-xxx
     * baseUrl/apiKey 缺省时使用数据库中已保存的配置
     */
    @GetMapping("/models")
    public ResponseEntity<Map<String, Object>> listModels(
            @RequestParam(name = "baseUrl", required = false) String baseUrl,
            @RequestParam(name = "apiKey", required = false) String apiKey) {
        Map<String, Object> response = new HashMap<>();
        try {
            java.util.List<String> models;
            boolean hasBaseUrl = baseUrl != null && !baseUrl.trim().isEmpty();
            boolean hasApiKey = apiKey != null && !apiKey.trim().isEmpty();
            if (hasBaseUrl && hasApiKey) {
                // 优先用页面传入的值，方便保存前先试拉取
                models = aiService.listModels(baseUrl, apiKey);
            } else {
                models = aiService.listModels();
            }
            response.put("success", true);
            response.put("data", models);
            response.put("message", "获取模型列表成功，共 " + models.size() + " 个模型");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("获取模型列表失败", e);
            response.put("success", false);
            response.put("message", e.getMessage() == null ? "获取模型列表失败" : e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    /**
     * 测试 AI 接口是否可用（POST）
     * 请求体可带 baseUrl, apiKey, model, prompt；若未传则默认使用已保存配置
     */
    @PostMapping("/test")
    public ResponseEntity<Map<String, Object>> testConnection(@RequestBody(required = false) Map<String, String> body) {
        Map<String, Object> response = new HashMap<>();
        try {
            String baseUrl = body != null ? body.get("baseUrl") : null;
            String apiKey = body != null ? body.get("apiKey") : null;
            String model = body != null ? body.get("model") : null;
            String prompt = body != null ? body.get("prompt") : null;

            Map<String, Object> testResult = aiService.testAiConnection(baseUrl, apiKey, model, prompt);
            response.put("success", true);
            response.put("data", testResult);
            response.put("message", testResult.get("message"));
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("AI 连通性测试失败", e);
            response.put("success", false);
            String errMsg = e.getMessage() == null ? "连通性测试失败，未知错误" : e.getMessage();
            response.put("message", errMsg);
            return ResponseEntity.badRequest().body(response);
        }
    }
}
