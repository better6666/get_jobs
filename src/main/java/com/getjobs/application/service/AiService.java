package com.getjobs.application.service;

import com.getjobs.application.entity.AiEntity;
import com.getjobs.application.mapper.AiMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * AI 服务（Spring 管理）
 * 从数据库配置获取 BASE_URL、API_KEY、MODEL 并发起 AI 请求。
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class AiService {
    private final ConfigService configService;
    private final AiMapper aiMapper;

    /**
     * 发送 AI 请求（非流式）并返回回复内容。
     * @param content 用户消息内容
     * @return AI 回复文本
     */
    public String sendRequest(String content) {
        // 中转站（Cloudflare 前置）偶发 524/502/503 超时，自动重试最多 3 次
        RuntimeException lastError = null;
        for (int attempt = 1; attempt <= 3; attempt++) {
            try {
                return doSendRequest(content);
            } catch (RuntimeException e) {
                lastError = e;
                String msg = String.valueOf(e.getMessage());
                boolean transientError = msg.contains("524") || msg.contains("502") || msg.contains("503")
                        || msg.contains("504") || msg.contains("timeout") || msg.contains("Timeout");
                if (transientError && attempt < 3) {
                    log.warn("AI请求第{}次失败（疑似中转站临时故障，将重试）: {}", attempt, msg.length() > 120 ? msg.substring(0, 120) + "..." : msg);
                    try {
                        Thread.sleep(2000L * attempt);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw e;
                    }
                    continue;
                }
                throw e;
            }
        }
        throw lastError;
    }

    private String doSendRequest(String content) {
        // 读取并校验配置
        var cfg = configService.getAiConfigs();
        String baseUrl = cfg.get("BASE_URL");
        String apiKey = cfg.get("API_KEY");
        String model = cfg.get("MODEL");
        return sendRequestWithConfig(baseUrl, apiKey, model, content, 60);
    }

    /**
     * 测试指定配置的 AI 接口连通性
     * @param baseUrl API 接口地址（缺省则读配置）
     * @param apiKey API 密钥（缺省则读配置）
     * @param model 模型名称（缺省则读配置）
     * @param testPrompt 测试提示词
     * @return 包含 success, latencyMs, reply, model, message 等信息的测试结果
     */
    public java.util.Map<String, Object> testAiConnection(String baseUrl, String apiKey, String model, String testPrompt) {
        var cfg = configService.getAiConfigs();
        String effectiveBaseUrl = (baseUrl != null && !baseUrl.trim().isEmpty()) ? baseUrl.trim() : cfg.get("BASE_URL");
        String effectiveApiKey = (apiKey != null && !apiKey.trim().isEmpty()) ? apiKey.trim() : cfg.get("API_KEY");
        String effectiveModel = (model != null && !model.trim().isEmpty()) ? model.trim() : cfg.get("MODEL");

        if (effectiveBaseUrl == null || effectiveBaseUrl.isBlank()) {
            throw new IllegalArgumentException("API Base URL 不能为空");
        }
        if (effectiveApiKey == null || effectiveApiKey.isBlank()) {
            throw new IllegalArgumentException("API Key 不能为空");
        }
        if (effectiveModel == null || effectiveModel.isBlank()) {
            throw new IllegalArgumentException("AI 模型名称不能为空");
        }

        String prompt = (testPrompt != null && !testPrompt.trim().isEmpty())
                ? testPrompt.trim()
                : "请回复一句简短的自我介绍，用于验证接口连通性（20字以内）。";

        long start = System.currentTimeMillis();
        String reply = sendRequestWithConfig(effectiveBaseUrl, effectiveApiKey, effectiveModel, prompt, 30);
        long latencyMs = System.currentTimeMillis() - start;

        java.util.Map<String, Object> result = new java.util.HashMap<>();
        result.put("success", true);
        result.put("latencyMs", latencyMs);
        result.put("reply", reply);
        result.put("model", effectiveModel);
        result.put("baseUrl", effectiveBaseUrl);
        result.put("message", "接口连通成功！模型在 " + latencyMs + "ms 内返回响应。");
        return result;
    }

    /**
     * 指定完整配置发送单次 AI 请求
     */
    public String sendRequestWithConfig(String baseUrl, String apiKey, String model, String content, int timeoutInSeconds) {
        if (baseUrl == null || baseUrl.trim().isEmpty()) {
            throw new IllegalArgumentException("API Base URL 不能为空");
        }
        if (apiKey == null || apiKey.trim().isEmpty()) {
            throw new IllegalArgumentException("API Key 不能为空");
        }
        if (model == null || model.trim().isEmpty()) {
            throw new IllegalArgumentException("AI 模型名称不能为空");
        }

        String endpoint = isResponsesModel(model)
                ? buildResponsesEndpoint(baseUrl)
                : buildChatCompletionsEndpoint(baseUrl);

        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(timeoutInSeconds))
                .build();

        // 构建 JSON 请求体
        JSONObject requestData = new JSONObject();
        requestData.put("model", model);
        requestData.put("temperature", 0.7);
        requestData.put("max_tokens", 250);
        if (endpoint.endsWith("/responses")) {
            // Responses API 采用 input 字段
            requestData.put("input", content);
        } else {
            // Chat Completions API 使用 messages
            JSONArray messages = new JSONArray();
            JSONObject message = new JSONObject();
            message.put("role", "user");
            message.put("content", content);
            messages.put(message);
            requestData.put("messages", messages);
        }

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(endpoint))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .header("User-Agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36")
                .header("Authorization", "Bearer " + apiKey)
                // 某些服务（例如 Azure OpenAI）需要 api-key 头，额外加一层兼容
                .header("api-key", apiKey)
                .POST(HttpRequest.BodyPublishers.ofString(requestData.toString()))
                .build();

        try {
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                JSONObject responseObject = new JSONObject(response.body());

                String requestId = responseObject.optString("id");
                long created = responseObject.optLong("created", 0);
                String usedModel = responseObject.optString("model");

                String responseContent = null;
                if (endpoint.endsWith("/responses")) {
                    // Responses API：优先读取 output_text
                    responseContent = responseObject.optString("output_text", null);
                    if (responseContent == null || responseContent.isEmpty()) {
                        try {
                            JSONObject messageObject = responseObject.getJSONArray("choices")
                                    .getJSONObject(0)
                                    .getJSONObject("message");
                            responseContent = messageObject.optString("content", null);
                        } catch (Exception ignore) {
                            responseContent = response.body();
                        }
                    }
                } else {
                    // Chat Completions API：支持标准 content 与推理模型 reasoning/reasoning_content
                    try {
                        JSONArray choices = responseObject.optJSONArray("choices");
                        if (choices != null && choices.length() > 0) {
                            JSONObject choice = choices.getJSONObject(0);
                            JSONObject messageObject = choice.optJSONObject("message");
                            if (messageObject != null) {
                                responseContent = messageObject.optString("content", null);
                                if (responseContent == null || responseContent.isBlank()) {
                                    responseContent = messageObject.optString("reasoning_content", null);
                                }
                                if (responseContent == null || responseContent.isBlank()) {
                                    responseContent = messageObject.optString("reasoning", null);
                                }
                            }
                        }
                    } catch (Exception parseEx) {
                        log.warn("解析 choices.message 失败: {}", parseEx.getMessage());
                    }
                    if (responseContent == null || responseContent.isBlank()) {
                        responseContent = responseObject.optString("output_text", null);
                    }
                }

                // 清洗打招呼语文本（去除think标签、前后缀说明、引号）
                responseContent = cleanAiGreeting(responseContent);

                JSONObject usageObject = responseObject.optJSONObject("usage");
                int promptTokens = usageObject != null ? usageObject.optInt("prompt_tokens", -1) : -1;
                int completionTokens = usageObject != null ? usageObject.optInt("completion_tokens", -1) : -1;
                int totalTokens = usageObject != null ? usageObject.optInt("total_tokens", -1) : -1;

                LocalDateTime createdTime = created > 0
                        ? Instant.ofEpochSecond(created).atZone(ZoneId.systemDefault()).toLocalDateTime()
                        : LocalDateTime.now();
                DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

                log.info("AI响应: id={}, time={}, model={}, promptTokens={}, completionTokens={}, totalTokens={}",
                        requestId, createdTime.format(formatter), usedModel, promptTokens, completionTokens, totalTokens);

                return responseContent;
            } else {
                // 更详细的错误日志，便于定位 400 问题
                log.error("AI请求失败: status={}, endpoint={}, body={}", response.statusCode(), endpoint, response.body());
                // 针对 Responses-only 模型误用 Chat Completions 的常见错误做一次自动重试
                if (!endpoint.endsWith("/responses") && containsReasoningParamError(response.body())) {
                    String fallbackEndpoint = buildResponsesEndpoint(baseUrl);
                    log.warn("检测到 reasoning 相关参数错误，自动切换到 Responses API 重试: {}", fallbackEndpoint);
                    return sendRequestViaResponses(content, apiKey, model, fallbackEndpoint);
                }
                throw new RuntimeException("AI请求失败，状态码: " + response.statusCode() + ", 详情: " + response.body());
            }
        } catch (Exception e) {
            log.error("调用AI服务异常", e);
            throw e instanceof RuntimeException ? (RuntimeException) e : new RuntimeException(e);
        }
    }

    /**
     * 清洗 AI 生成的打招呼语：移除思考标签、前后缀说明、引号与无效包装
     */
    private String cleanAiGreeting(String text) {
        if (text == null) return null;
        String s = text.trim();
        // 1. 去除 <think>...</think> 标签及其内容
        if (s.contains("<think>")) {
            s = s.replaceAll("(?s)<think>.*?</think>", "").trim();
        }
        // 2. 去除常见的 Markdown 代码块标签
        if (s.startsWith("```") && s.endsWith("```")) {
            s = s.replaceAll("^```[a-zA-Z]*\\n?", "").replaceAll("\\n?```$", "").trim();
        }
        // 3. 去除常见的前缀标识（如 "打招呼语："、"招呼语：" 等）
        String[] prefixes = new String[]{
                "打招呼语：", "打招呼语:", "招呼语：", "招呼语:", "问候语：", "问候语:",
                "打招呼内容：", "打招呼内容:", "生成的打招呼语：", "求职打招呼语：",
                "【打招呼语】", "【求职打招呼语】"
        };
        for (String p : prefixes) {
            if (s.startsWith(p)) {
                s = s.substring(p.length()).trim();
                break;
            }
        }
        // 4. 去除首尾的双引号或单引号
        if ((s.startsWith("\"") && s.endsWith("\"")) || (s.startsWith("“") && s.endsWith("”")) || (s.startsWith("'") && s.endsWith("'"))) {
            s = s.substring(1, s.length() - 1).trim();
        }
        return s;
    }

    private String normalizeBaseUrl(String baseUrl) {
        if (baseUrl == null) return "";
        String trimmed = baseUrl.trim();
        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        // 兼容中转站地址：直接粘贴完整端点时自动去掉后半段，统一还原成 base
        // 如 https://xx.com/v1/chat/completions -> https://xx.com/v1
        for (String suffix : new String[]{"/chat/completions", "/responses", "/completions", "/models", "/embeddings"}) {
            if (trimmed.toLowerCase().endsWith(suffix)) {
                trimmed = trimmed.substring(0, trimmed.length() - suffix.length());
                break;
            }
        }
        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }

    /**
     * 使用数据库中已保存的 BASE_URL / API_KEY 拉取模型列表
     */
    public List<String> listModels() {
        var cfg = configService.getAiConfigs();
        return listModels(cfg.get("BASE_URL"), cfg.get("API_KEY"));
    }

    /**
     * 拉取 OpenAI 兼容接口的模型列表（GET /v1/models）
     * @param baseUrl API 地址，带不带 /v1 均可
     * @param apiKey  API 密钥
     * @return 模型 ID 列表（忽略大小写排序）
     */
    public List<String> listModels(String baseUrl, String apiKey) {
        if (baseUrl == null || baseUrl.trim().isEmpty()) {
            throw new IllegalArgumentException("请先填写 API Base URL");
        }
        if (apiKey == null || apiKey.trim().isEmpty()) {
            throw new IllegalArgumentException("请先填写 API Key");
        }
        String normalized = normalizeBaseUrl(baseUrl);
        String endpoint = (normalized.endsWith("/v1") || normalized.contains("/v1/"))
                ? normalized + "/models"
                : normalized + "/v1/models";

        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(20))
                .build();

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(endpoint))
                .header("Accept", "application/json")
                .header("Authorization", "Bearer " + apiKey.trim())
                .header("api-key", apiKey.trim())
                .timeout(Duration.ofSeconds(20))
                .GET()
                .build();

        try {
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 401) {
                throw new RuntimeException("API Key 无效或未授权（401），请检查 Key 是否正确、是否已在该中转站生成令牌");
            }
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new RuntimeException("模型列表请求失败，状态码: " + response.statusCode() + "，详情: " + snippet(response.body()));
            }
            String body = response.body();
            // 中转站可能返回 HTML 页面（地址填错成网页路径时），给出人话提示
            String head = body == null ? "" : body.trim();
            if (head.startsWith("<")) {
                throw new RuntimeException("该地址返回的是网页而不是 API 响应，请检查 Base URL：应填接口地址（如 https://xx.com/v1），不要从浏览器地址栏复制带页面路径的网址。返回内容: " + snippet(body));
            }
            JSONObject modelsRoot;
            if (head.startsWith("[")) {
                // 部分中转站直接返回数组格式的模型列表
                modelsRoot = new JSONObject();
                modelsRoot.put("data", new org.json.JSONArray(head));
            } else {
                try {
                    modelsRoot = new JSONObject(body);
                } catch (Exception parseError) {
                    throw new RuntimeException("响应不是有效的 JSON，返回内容: " + snippet(body));
                }
            }
            JSONArray data = modelsRoot.optJSONArray("data");
            if (data == null) {
                throw new RuntimeException("响应中没有 data 字段，返回内容: " + snippet(body));
            }
            List<String> models = new ArrayList<>();
            for (int i = 0; i < data.length(); i++) {
                JSONObject item = data.optJSONObject(i);
                if (item != null && item.has("id")) {
                    models.add(item.getString("id"));
                }
            }
            if (models.isEmpty()) {
                throw new RuntimeException("该 Key 下没有可用模型，请检查 Key 的模型分组/额度");
            }
            models.sort(String.CASE_INSENSITIVE_ORDER);
            return models;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("模型列表请求被中断", e);
        } catch (IOException e) {
            throw new RuntimeException("模型列表请求失败（网络异常）: " + e.getMessage(), e);
        }
    }

    /**
     * 截取响应体片段用于错误提示，避免把整个 HTML 页面塞进报错信息
     */
    private String snippet(String body) {
        if (body == null) return "(空响应)";
        String trimmed = body.trim().replaceAll("\\s+", " ");
        return trimmed.length() > 150 ? trimmed.substring(0, 150) + "..." : trimmed;
    }

    /**
     * 根据配置构造 chat/completions 端点，避免重复拼接 /v1
     */
    private String buildChatCompletionsEndpoint(String baseUrl) {
        String normalized = normalizeBaseUrl(baseUrl);
        // 如果 baseUrl 已经包含 /v1（常见配置为 https://api.openai.com/v1），则只拼接 /chat/completions
        if (normalized.endsWith("/v1") || normalized.contains("/v1/")) {
            return normalized + "/chat/completions";
        }
        return normalized + "/v1/chat/completions";
    }

    /**
     * 构造 Responses API 端点
     */
    private String buildResponsesEndpoint(String baseUrl) {
        String normalized = normalizeBaseUrl(baseUrl);
        if (normalized.endsWith("/v1") || normalized.contains("/v1/")) {
            return normalized + "/responses";
        }
        return normalized + "/v1/responses";
    }

    /**
     * 粗略识别需要使用 Responses API 的模型（部分实验性 o-系列）
     */
    private boolean isResponsesModel(String model) {
        if (model == null) return false;
        String m = model.toLowerCase();
        return (m.contains("o1") || m.contains("o3") || m.contains("o4"))
                && !m.contains("4o-mini") && !m.contains("gpt-4o-mini");
    }

    /**
     * 检查错误响应中是否包含 reasoning 相关参数错误（如 reasoning.summary unsupported_value）
     */
    private boolean containsReasoningParamError(String body) {
        if (body == null) return false;
        String s = body.toLowerCase();
        return (s.contains("reasoning") && s.contains("unsupported_value"))
                || s.contains("reasoning.summary");
    }

    /**
     * 使用 Responses API 发送一次请求（用于自动降级/重试）
     */
    private String sendRequestViaResponses(String content, String apiKey, String model, String endpoint) {
        int timeoutInSeconds = 60;
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(timeoutInSeconds))
                .build();

        JSONObject requestData = new JSONObject();
        requestData.put("model", model);
        requestData.put("temperature", 0.9);
        requestData.put("input", content);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(endpoint))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .header("Authorization", "Bearer " + apiKey)
                .header("api-key", apiKey)
                .POST(HttpRequest.BodyPublishers.ofString(requestData.toString()))
                .build();

        try {
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                JSONObject resp = new JSONObject(response.body());
                String outputText = resp.optString("output_text", null);
                if (outputText != null && !outputText.isEmpty()) {
                    return outputText;
                }
                // 兜底解析：部分兼容层可能返回 choices/message 结构
                try {
                    JSONObject messageObject = resp.getJSONArray("choices")
                            .getJSONObject(0)
                            .getJSONObject("message");
                    return messageObject.getString("content");
                } catch (Exception ignore) {
                }
                // 无法解析则直接返回原始体，避免空值中断流程
                return response.body();
            }
            log.error("Responses API 调用失败: status={}, endpoint={}, body={}", response.statusCode(), endpoint, response.body());
            throw new RuntimeException("AI请求失败，状态码: " + response.statusCode() + ", 详情: " + response.body());
        } catch (Exception e) {
            log.error("Responses API 调用异常", e);
            throw e instanceof RuntimeException ? (RuntimeException) e : new RuntimeException(e);
        }
    }

    // ================= 合并的 AI 配置管理方法 =================

    /**
     * 获取AI配置（获取最新一条，如果不存在则创建默认配置）
     */
    @Transactional(readOnly = true)
    public AiEntity getAiConfig() {
        var list = aiMapper.selectList(null);
        AiEntity aiEntity = (list == null || list.isEmpty()) ? null : list.get(list.size() - 1);
        if (aiEntity == null) {
            aiEntity = createDefaultConfig();
        }
        return aiEntity;
    }

    /**
     * 获取所有AI配置
     */
    @Transactional(readOnly = true)
    public java.util.List<AiEntity> getAllAiConfigs() {
        return aiMapper.selectList(null);
    }

    /**
     * 根据ID获取AI配置
     */
    @Transactional(readOnly = true)
    public AiEntity getAiConfigById(Long id) {
        return aiMapper.selectById(id);
    }

    /**
     * 保存或更新AI配置（introduce/prompt）
     */
    @Transactional
    public AiEntity saveOrUpdateAiConfig(String introduce, String prompt) {
        var list = aiMapper.selectList(null);
        AiEntity aiEntity = (list == null || list.isEmpty()) ? null : list.get(list.size() - 1);

        if (aiEntity == null) {
            aiEntity = new AiEntity();
            aiEntity.setIntroduce(introduce);
            aiEntity.setPrompt(prompt);
            aiEntity.setCreatedAt(java.time.LocalDateTime.now());
            aiEntity.setUpdatedAt(java.time.LocalDateTime.now());
            aiMapper.insert(aiEntity);
            log.info("创建新的AI配置，ID: {}", aiEntity.getId());
        } else {
            aiEntity.setIntroduce(introduce);
            aiEntity.setPrompt(prompt);
            aiEntity.setUpdatedAt(java.time.LocalDateTime.now());
            aiMapper.updateById(aiEntity);
            log.info("更新AI配置，ID: {}", aiEntity.getId());
        }

        return aiEntity;
    }

    /**
     * 删除AI配置
     */
    @Transactional
    public boolean deleteAiConfig(Long id) {
        int result = aiMapper.deleteById(id);
        if (result > 0) {
            log.info("删除AI配置成功，ID: {}", id);
            return true;
        }
        return false;
    }

    /**
     * 创建默认配置
     */
    @Transactional
    protected AiEntity createDefaultConfig() {
        AiEntity aiEntity = new AiEntity();
        aiEntity.setIntroduce("请在此填写您的技能介绍");
        aiEntity.setPrompt("请在此填写AI提示词模板");
        aiEntity.setCreatedAt(java.time.LocalDateTime.now());
        aiEntity.setUpdatedAt(java.time.LocalDateTime.now());
        aiMapper.insert(aiEntity);
        log.info("创建默认AI配置，ID: {}", aiEntity.getId());
        return aiEntity;
    }
}