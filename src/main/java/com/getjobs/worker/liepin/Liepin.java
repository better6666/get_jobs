package com.getjobs.worker.liepin;

import com.getjobs.worker.utils.PlaywrightUtil;
import com.getjobs.application.service.LiepinService;
import com.getjobs.application.entity.LiepinEntity;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Response;
import com.microsoft.playwright.options.WaitForSelectorState;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Autowired;

// 移除保存页面源码相关的导入
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.function.Supplier;

import static com.getjobs.worker.liepin.Locators.*;


/**
 * @author loks666
 * 项目链接: <a href="https://github.com/loks666/get_jobs">https://github.com/loks666/get_jobs</a>
 */
@Slf4j
@Component
@Scope("prototype")
public class Liepin {
    static {
        // 在类加载时就设置日志文件名，确保Logger初始化时能获取到正确的属性
        System.setProperty("log.name", "liepin");
    }

    private int maxPage = 50;
    private final List<String> resultList = new ArrayList<>();
    private final List<LiepinEntity> lastApiEntities = new ArrayList<>();
    private boolean monitoringRegistered = false;
    @Setter
    private LiepinConfig config;
    @Getter
    private Date startDate;
    @Setter
    private Page page;
    @Autowired
    private LiepinService liepinService;

    @Autowired
    private com.getjobs.application.service.AiService aiService;

    /** 最近发出的AI追问消息（去重，避免句式雷同） */
    private final java.util.Deque<String> recentGreetings = new java.util.concurrent.ConcurrentLinkedDeque<>();

    public interface ProgressCallback {
        void onProgress(String message, Integer current, Integer total);
    }

    @Setter
    private ProgressCallback progressCallback;
    @Setter
    private Supplier<Boolean> shouldStopCallback;

    public void prepare() {
        this.startDate = new Date();
        this.resultList.clear();

        // 监控猎聘接口请求与返回，输出被拦截的URL（精确匹配PC搜索相关接口）
        if (page != null && !monitoringRegistered) {
            // 请求拦截日志（仅搜索岗位接口）
            page.onRequest(req -> {
                try {
                    String url = req.url();
                    if (url != null && url.contains("com.liepin.searchfront4c.pc-search-job")
                            && !url.contains("com.liepin.searchfront4c.pc-search-job-cond-init")) {
                        // log.info("[拦截][请求] {}", url);
                    }
                } catch (Exception ignored) {}
            });
            page.onResponse((Response response) -> {
                try {
                    String url = response.url();
                    if (url != null && response.status() == 200 &&
                            url.contains("com.liepin.searchfront4c.pc-search-job") &&
                            !url.contains("com.liepin.searchfront4c.pc-search-job-cond-init")) {
                        log.info("[拦截][响应] {}", url);
                        String contentType = null;
                        try { contentType = response.headers().get("content-type"); } catch (Exception ignored) {}
                        if (contentType == null || contentType.contains("application/json")) {
                            String text = response.text();
                            if (text != null && !text.isEmpty()) {
                                // 尝试解析，若结构不符合则在方法内直接返回
                                parseAndPersistLiepinData(text);
                            }
                        }
                    }
                } catch (Exception e) {
                    log.warn("监控猎聘接口响应时发生错误: {}", e.getMessage());
                }
            });
            monitoringRegistered = true;
        }
    }

    public int execute() {
        if (page == null) {
            throw new IllegalStateException("Liepin.page 未设置");
        }
        if (config == null) {
            throw new IllegalStateException("Liepin.config 未设置");
        }

        // 在开始执行前确保已注册接口监听
        prepare();

        List<String> keywords = config.getKeywords();
        if (keywords == null || keywords.isEmpty()) {
            log.warn("未配置关键词，执行结束");
            return 0;
        }

        int failedKeywords = 0;
        for (String keyword : keywords) {
            if (shouldStop()) {
                info("收到停止指令，提前结束关键词循环");
                break;
            }
            try {
                submit(keyword);
            } catch (Exception e) {
                // 单个关键词失败不影响其余关键词
                failedKeywords++;
                log.error("关键词【{}】投递失败，跳过继续: {}", keyword, e.getMessage());
            }
        }
        if (failedKeywords > 0) {
            info(String.format("有 %d 个关键词投递失败（已跳过）", failedKeywords));
        }
        return resultList.size();
    }

    // ========== 解析接口JSON并保存到数据库 ==========
    private void parseAndPersistLiepinData(String json) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            JsonNode root = mapper.readTree(json);
            // 兼容两种结构：data.data.jobCardList 或 data.jobCardList
            JsonNode cardList = root.path("data").path("data").path("jobCardList");
            if (!cardList.isArray()) {
                cardList = root.path("data").path("jobCardList");
            }
            if (!cardList.isArray()) {
                return;
            }
            lastApiEntities.clear();
            for (JsonNode item : cardList) {
                JsonNode job = item.path("job");
                JsonNode comp = item.path("comp");
                JsonNode recruiter = item.path("recruiter");

                Long jobId = readLong(job.path("jobId"));
                if (jobId == null) {
                    continue;
                }

                LiepinEntity entity = new LiepinEntity();
                entity.setJobId(jobId);
                entity.setJobTitle(readText(job.path("title")));
                entity.setJobLink(readText(job.path("link")));
                entity.setJobSalaryText(readText(job.path("salary")));
                entity.setJobArea(readText(job.path("dq")));
                entity.setJobEduReq(readText(job.path("requireEduLevel")));
                entity.setJobExpReq(readText(job.path("requireWorkYears")));
                entity.setJobPublishTime(readText(job.path("refreshTime")));

                entity.setCompId(readLong(comp.path("compId")));
                entity.setCompName(readText(comp.path("compName")));
                entity.setCompIndustry(readText(comp.path("compIndustry")));
                entity.setCompScale(readText(comp.path("compScale")));

                entity.setHrId(readText(recruiter.path("recruiterId")));
                entity.setHrName(readText(recruiter.path("recruiterName")));
                entity.setHrTitle(readText(recruiter.path("recruiterTitle")));
                entity.setHrImId(readText(recruiter.path("imId")));

                // 缓存到内存供页面投递显示使用（避免从页面读取文本）
                lastApiEntities.add(entity);
            }
            // 批量持久化：仅不存在时插入，默认 delivered=0
            try {
                liepinService.insertSnapshotsIfNotExistsBatch(lastApiEntities);
            } catch (Exception e) {
                log.warn("批量保存猎聘岗位数据失败: {}", e.getMessage());
            }
        } catch (Exception e) {
            log.warn("解析猎聘JSON失败: {}", e.getMessage());
        }
    }

    private String readText(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) return null;
        String v = node.asText();
        return (v == null || v.isEmpty()) ? null : v;
    }

    private Long readLong(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) return null;
        try {
            if (node.isNumber()) {
                long v = node.asLong();
                return v == 0 ? null : v;
            }
            if (node.isTextual()) {
                String t = node.asText();
                if (t == null || t.isEmpty()) return null;
                long v = Long.parseLong(t.trim());
                return v == 0 ? null : v;
            }
        } catch (Exception ignored) {}
        return null;
    }

    private String safeText(String s) {
        if (s == null) return null;
        return s.replaceAll("\n", " ").replaceAll("【 ", "[").replaceAll(" 】", "]");
    }

    private boolean shouldStop() {
        return shouldStopCallback != null && Boolean.TRUE.equals(shouldStopCallback.get());
    }

    private void info(String msg) {
        if (progressCallback != null) {
            progressCallback.onProgress(msg, null, null);
        } else {
            log.info(msg);
        }
    }

    private void submit(String keyword) {
        // 清洗关键词：去掉前后引号与多余空白
        String cleanKeyword = keyword == null ? "" : keyword.replace("\"", "").trim();
        // navigate 偶发被连接层的僵尸响应事件干扰（Object doesn't exist），重试一次即可恢复
        boolean navigated = false;
        for (int attempt = 0; attempt < 2 && !navigated; attempt++) {
            try {
                page.navigate(getSearchUrl() + "&key=" + cleanKeyword);
                navigated = true;
            } catch (Exception navError) {
                if (attempt == 0 && String.valueOf(navError.getMessage()).contains("Object doesn't exist")) {
                    log.warn("导航遇到僵尸事件，重试: {}", navError.getMessage());
                    PlaywrightUtil.sleep(2);
                } else {
                    throw navError;
                }
            }
        }

        // 等待分页元素加载（轮询实现：waitForSelector 会被响应拦截器里已销毁对象的异常干扰）
        Locator paginationBox = page.locator(PAGINATION_BOX);
        boolean paginationAppeared = false;
        for (int waitIdx = 0; waitIdx < 20; waitIdx++) {
            if (shouldStop()) {
                info("收到停止指令，结束投递");
                return;
            }
            try {
                if (paginationBox.count() > 0 && paginationBox.first().isVisible()) {
                    paginationAppeared = true;
                    break;
                }
            } catch (Exception ignored) {
            }
            PlaywrightUtil.sleep(1);
        }
        if (!paginationAppeared) {
            log.warn("搜索结果分页未出现（可能被登录/验证弹窗挡住），跳过关键词: {}", cleanKeyword);
            return;
        }
        Locator lis = paginationBox.locator("li");
        setMaxPage(lis);
        
        for (int i = 0; i < maxPage; i++) {
            if (shouldStop()) {
                info("收到停止指令，结束分页循环");
                return;
            }
            try {
                // 尝试关闭订阅弹窗
                Locator closeBtn = page.locator(SUBSCRIBE_CLOSE_BTN);
                if (closeBtn.count() > 0) {
                    closeBtn.click();
                }
            } catch (Exception ignored) {
            }
            
        // 等待岗位卡片挂载（轮询实现，不要求可见，避免因遮挡或已销毁对象异常造成超时）
        Locator jobCardsWait = page.locator(JOB_CARDS);
        boolean cardsAppeared = false;
        for (int waitIdx = 0; waitIdx < 15; waitIdx++) {
            if (shouldStop()) {
                info("收到停止指令，结束投递");
                return;
            }
            try {
                if (jobCardsWait.count() > 0) {
                    cardsAppeared = true;
                    break;
                }
            } catch (Exception ignored) {
            }
            PlaywrightUtil.sleep(1);
        }
        if (!cardsAppeared) {
            log.warn("岗位卡片未加载出来，跳过关键词: {}", cleanKeyword);
            return;
        }
            // 额外等待一次接口响应，确保 lastApiEntities 刷新（精确匹配PC搜索接口）
            try {
                page.waitForResponse(r -> {
                    try {
                        String u = r.url();
                        return u != null && u.contains("com.liepin.searchfront4c.pc-search-job") && r.status() == 200;
                    } catch (Exception ignored) { return false; }
                }, () -> {});
            } catch (Exception ignored) {}
            info(String.format("正在投递【%s】第【%d】页...", cleanKeyword, i + 1));
            submitJob();
            info(String.format("已投递第【%d】页所有的岗位...", i + 1));
            
            // 查找下一页按钮（AntD v5 结构）
            paginationBox = page.locator(PAGINATION_BOX);
            Locator nextLi = paginationBox.locator(NEXT_PAGE);
            if (nextLi.count() > 0) {
                String cls = nextLi.first().getAttribute("class");
                boolean disabled = cls != null && cls.contains("ant-pagination-disabled");
                if (!disabled) {
                    dismissBlockingModals();
                    Locator btn = nextLi.first().locator("button.ant-pagination-item-link");
                    try {
                        if (btn.count() > 0) {
                            btn.first().click(new Locator.ClickOptions().setTimeout(10_000));
                        } else {
                            nextLi.first().click(new Locator.ClickOptions().setTimeout(10_000));
                        }
                    } catch (Exception clickError) {
                        // 翻页被弹窗挡住：关闭后重试一次
                        log.warn("翻页点击被弹窗拦截，关闭弹窗后重试: {}", clickError.getMessage());
                        dismissBlockingModals();
                        if (btn.count() > 0) {
                            btn.first().click(new Locator.ClickOptions().setTimeout(10_000));
                        } else {
                            nextLi.first().click(new Locator.ClickOptions().setTimeout(10_000));
                        }
                    }
                } else {
                    break;
                }
            } else {
                break;
            }
        }
        info(String.format("【%s】关键词投递完成！", cleanKeyword));
    }

    private String getSearchUrl() {
        String baseUrl = "https://www.liepin.com/zhaopin/?";
        StringBuilder sb = new StringBuilder(baseUrl);
        // 直接拼接参数，参数为空则忽略
        if (config.getCityCode() != null && !config.getCityCode().isEmpty()) {
            sb.append("city=").append(config.getCityCode()).append("&");
            sb.append("dq=").append(config.getCityCode()).append("&");
        }
        if (config.getSalary() != null && !config.getSalary().isEmpty()) {
            sb.append("salary=").append(config.getSalary()).append("&");
        }
        sb.append("currentPage=0");
        return sb.toString();
    }

    private void setMaxPage(Locator lis) {
        try {
            int count = lis.count();
            if (count >= 2) {
                String pageText = lis.nth(count - 2).textContent();
                int page = Integer.parseInt(pageText);
                if (page > 1) {
                    maxPage = page;
                }
            }
        } catch (Exception ignored) {
        }
    }

    /**
     * 关闭猎聘弹出的遮挡弹窗（如"完善简历" ant-modal），避免拦截后续所有点击
     */
    private void dismissBlockingModals() {
        try {
            Locator modal = page.locator(".ant-modal-wrap:visible .ant-modal-close");
            if (modal.count() > 0 && modal.first().isVisible()) {
                modal.first().click();
                log.info("已关闭猎聘遮挡弹窗（完善简历等）");
                PlaywrightUtil.sleep(1);
            } else if (page.locator(".ant-modal-wrap:visible").count() > 0) {
                // 没有 close 按钮时用 ESC 兜底
                page.keyboard().press("Escape");
                PlaywrightUtil.sleep(1);
            }
        } catch (Exception ignore) {
        }
    }

    /**
     * 猎聘聊天窗口里追加一条 AI 生成的个性化消息。
     * 平台自动发的招呼语是 App 里的固定文案，这条追问让每个 HR 收到的内容都不同。
     * 全程 best-effort：找不到输入框/AI失败都静默跳过，不影响投递主流程。
     */
    private void sendAiFollowUp(String jobName, String companyName) {
        try {
            // 1) 定位聊天输入框（猎聘IM是 contenteditable 或 textarea）
            Locator input = page.locator(
                    "div.__im_basic__chat-wrapper [contenteditable='true']:not([style*='display: none']), "
                  + ".__im_basic__chat-input textarea, "
                  + "div[class*='im-basic'] textarea, "
                  + "div[class*='chat'] div[contenteditable='true']").first();
            boolean inputReady = false;
            for (int i = 0; i < 5; i++) {
                try {
                    if (input.count() > 0 && input.isVisible()) {
                        inputReady = true;
                        break;
                    }
                } catch (Exception ignore) {
                }
                PlaywrightUtil.sleep(1);
            }
            if (!inputReady) {
                log.debug("猎聘聊天输入框未找到，跳过AI追问");
                return;
            }

            // 2) 生成AI消息（带去重与风格轮换）
            String aiConfigIntroduce = "";
            String prompt = null;
            try {
                var aiEntity = aiService.getAiConfig();
                if (aiEntity != null) {
                    aiConfigIntroduce = aiEntity.getIntroduce() != null ? aiEntity.getIntroduce() : "";
                    prompt = aiEntity.getPrompt();
                }
            } catch (Exception ignore) {
            }
            String requestMessage = "你是一名求职顾问。求职者背景：【" + aiConfigIntroduce + "】\n"
                    + "猎聘平台刚向「" + (companyName == null ? "该公司" : companyName) + "」的「" + (jobName == null ? "该岗位" : jobName) + "」岗位发送过系统默认打招呼语。\n"
                    + "请写一条40到70字的补充消息，要求：\n"
                    + "1. 不重复自我介绍套话，从岗位/公司业务切入，展示一个具体的理解或亮点；\n"
                    + "2. 开头句式必须与下面这些历史消息明显不同：\n";
            int shown = 0;
            for (String g : recentGreetings) {
                requestMessage += "- " + g + "\n";
                if (++shown >= 6) break;
            }
            requestMessage += "3. 只输出消息正文。";

            String message = sanitizeAiGreeting(aiService.sendRequest(requestMessage));
            if (message == null || message.isBlank()) return;
            message = message.trim();
            boolean looksLikeGreeting = message.contains("我") && !message.contains("求职者")
                    && !message.contains("差异化") && message.length() >= 15 && message.length() <= 250;
            if (!looksLikeGreeting) {
                log.debug("AI输出不像追问消息，跳过");
                return;
            }
            recentGreetings.addFirst(message);
            while (recentGreetings.size() > 6) {
                recentGreetings.removeLast();
            }

            // 3) 写入并发送
            input.click();
            Object tagObj = input.evaluate("el => el.tagName.toLowerCase()");
            if (tagObj instanceof String && ((String) tagObj).equals("textarea")) {
                input.fill(message);
            } else {
                input.evaluate("(el, msg) => { el.innerText = msg; el.dispatchEvent(new Event('input', {bubbles: true})); }", message);
            }
            PlaywrightUtil.sleep(1);
            input.press("Enter");
            PlaywrightUtil.sleep(1);
            log.info("猎聘AI追问消息已发送 | 岗位: {} | 内容: {}", jobName, message);
        } catch (Exception e) {
            log.debug("猎聘AI追问消息发送失败（不影响投递）: {}", e.getMessage());
        }
    }


    /**
     * 清洗AI输出：剥掉思考过程/前缀废话，只留招呼语正文。
     */
    private String sanitizeAiGreeting(String raw) {
        if (raw == null) return null;
        String s = raw.trim();
        // 去掉markdown代码块包裹
        s = s.replaceAll("(?s)```[a-z]*\n?", "").replace("```", "").trim();
        boolean looksLikeReasoning = s.startsWith("好的") || s.startsWith("明白") || s.startsWith("以下是")
                || s.contains("这个任务") || s.contains("打招呼语") || s.contains("分析一下")
                || s.contains("核心是") || s.contains("求职者是") && s.contains("写一条");
        if (looksLikeReasoning) {
            // 优先提取「」内的内容
            java.util.regex.Matcher m1 = java.util.regex.Pattern.compile("「([^」]{20,})」").matcher(s);
            if (m1.find()) return m1.group(1).trim();
            // 再试中文双引号
            java.util.regex.Matcher m2 = java.util.regex.Pattern.compile("“([^”]{20,})”").matcher(s);
            if (m2.find()) return m2.group(1).trim();
            // 兜底：取最后一个非空行（模型通常最后才给正文）
            String[] lines = s.split("\n");
            for (int i = lines.length - 1; i >= 0; i--) {
                String line = lines[i].trim();
                if (line.length() >= 15 && !line.startsWith("好的") && !line.startsWith("以下是")
                        && !line.contains("打招呼语") && !line.contains("核心是")) {
                    return line;
                }
            }
        }
        // 去掉开头的客套前缀
        s = s.replaceFirst("^^(好的[，,！!]?|明白了[，,。.]?|以下是[：:]?)\\s*", "").trim();
        return s;
    }

    private void submitJob() {
        // 获取hr数量
        Locator jobCards = page.locator(JOB_CARDS);
        int count = jobCards.count();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < count; i++) {
            if (shouldStop()) {
                info("收到停止指令，结束卡片遍历");
                return;
            }
            // 猎聘会弹"完善简历"等 ant-modal 弹窗挡住所有点击，处理每张卡片前先关掉
            dismissBlockingModals();
            // 获取当前岗位卡片（用于后续操作与缺省展示）
            Locator currentJobCard = page.locator(JOB_CARDS).nth(i);
            // 从接口数据获取展示所需字段，若接口数据缺失则使用缺省占位，仍尝试打招呼
            String jobName = null;
            String companyName = null;
            String salary = null;
            String recruiterName = null;
            if (i < lastApiEntities.size()) {
                LiepinEntity apiEntity = lastApiEntities.get(i);
                jobName = safeText(apiEntity.getJobTitle());
                companyName = safeText(apiEntity.getCompName());
                salary = safeText(apiEntity.getJobSalaryText());
                recruiterName = safeText(apiEntity.getHrName());
            }
            if (recruiterName == null) recruiterName = "HR";
            if (jobName == null) jobName = "岗位";
            if (companyName == null) companyName = "公司";
            if (salary == null) salary = "";
            
            try {
                // 使用JavaScript滚动到卡片位置，更稳定
                try {
                    // 先滚动到卡片位置
                    page.evaluate("(element) => element.scrollIntoView({behavior: 'instant', block: 'center'})", currentJobCard.elementHandle());
                    // PlaywrightUtil.sleep(1); // 等待滚动完成
                    
                    // 再次确保元素在视窗中
                    page.evaluate("(element) => { const rect = element.getBoundingClientRect(); if (rect.top < 0 || rect.bottom > window.innerHeight) { element.scrollIntoView({behavior: 'instant', block: 'center'}); } }", currentJobCard.elementHandle());
                    // PlaywrightUtil.sleep(1);
                } catch (Exception scrollError) {
                    log.warn("JavaScript滚动失败，尝试页面滚动: {}", scrollError.getMessage());
                    // 备用方案：滚动页面到大概位置
                    page.evaluate("window.scrollBy(0, " + (i * 200) + ")");
                    // PlaywrightUtil.sleep(1);
                }
                
                // 查找HR区域 - 尝试多种可能的HR标签选择器
                Locator hrArea = null;
                String[] hrSelectors = {
                    ".recruiter-info-box",  // 根据页面源码，这是主要的HR区域类名
                    ".recruiter-info, .hr-info, .contact-info",
                    "[class*='recruiter'], [class*='hr-'], [class*='contact']",
                    ".job-card-footer, .card-footer",
                    ".job-bottom, .bottom-info"
                };
                
                for (String selector : hrSelectors) {
                    Locator tempHrArea = currentJobCard.locator(selector);
                    if (tempHrArea.count() > 0) {
                        hrArea = tempHrArea.first();
                        log.debug("找到HR区域，使用选择器: {}", selector);
                        break;
                    }
                }
                
                // 如果找不到特定的HR区域，使用整个卡片
                if (hrArea == null) {
                    log.debug("未找到特定HR区域，使用整个岗位卡片");
                    hrArea = currentJobCard;
                }
                
                // 鼠标悬停到HR区域，触发按钮显示 - 简化悬停逻辑
                boolean hoverSuccess = false;
                int hoverRetries = 3;
                for (int retry = 0; retry < hoverRetries; retry++) {
                    try {
                        // 检查HR区域是否可见，如果不可见则跳过悬停
                        if (!hrArea.isVisible()) {
                            log.debug("HR区域不可见，跳过悬停操作");
                            hoverSuccess = true; // 设为成功，继续后续流程
                            break;
                        }
                        
                        // 直接悬停，不再进行复杂的微调
                        hrArea.hover(new Locator.HoverOptions().setTimeout(5000));
                        hoverSuccess = true;
                        break;
                    } catch (Exception hoverError) {
                        log.warn("第{}次悬停失败: {}", retry + 1, hoverError.getMessage());
                        if (retry < hoverRetries - 1) {
                            // 重试前重新滚动确保元素可见
                            try {
                                page.evaluate("(element) => element.scrollIntoView({behavior: 'instant', block: 'center'})", currentJobCard.elementHandle());
                                Thread.sleep(500); // 等待滚动完成
                            } catch (Exception e) {
                                log.warn("重试前滚动失败: {}", e.getMessage());
                            }
                        }
                    }
                }
                
                if (!hoverSuccess) {
                    log.warn("悬停操作失败，但继续查找按钮");
                    // 不再跳过，而是继续查找按钮，因为有些按钮可能不需要悬停就能显示
                }
                
                // PlaywrightUtil.sleep(1); // 等待按钮显示
                
                // 获取hr名字
                // 已从接口获取 HR 名字，无需再从页面读
                
            } catch (Exception e) {
                log.error("处理岗位卡片失败: {}", e.getMessage());
                continue;
            }
            
            // 查找聊一聊按钮
            Locator button = null;
            String buttonText = "";
            try {
                // 在当前岗位卡片中查找按钮，尝试多种选择器
                
                String[] buttonSelectors = {
                    "button.ant-btn.ant-btn-primary.ant-btn-round",
                    "button.ant-btn.ant-btn-round.ant-btn-primary", 
                    "button[class*='ant-btn'][class*='primary']",
                    "button[class*='ant-btn'][class*='round']",
                    "button[class*='chat'], button[class*='talk']",
                    ".chat-btn, .talk-btn, .contact-btn",
                    "button:has-text('聊一聊')",
                    "button" // 最后尝试所有按钮
                };
                
                for (String selector : buttonSelectors) {
                    try {
                        Locator tempButtons = currentJobCard.locator(selector);
                        int buttonCount = tempButtons.count();
                        log.debug("选择器 '{}' 找到 {} 个按钮", selector, buttonCount);
                        
                        for (int j = 0; j < buttonCount; j++) {
                            Locator tempButton = tempButtons.nth(j);
                            try {
                                if (tempButton.isVisible()) {
                                    String text = tempButton.textContent();
                                    log.debug("按钮文本: '{}'", text);
                                    if (text != null && !text.trim().isEmpty()) {
                                        button = tempButton;
                                        buttonText = text.trim();
                                        // 只关注"聊一聊"按钮
                                        if (text.contains("聊一聊")) {
                                            log.debug("找到目标按钮: '{}'", text);
                                            break;
                                        }
                                    }
                                }
                            } catch (Exception ignore) {
                                log.debug("获取按钮文本失败: {}", ignore.getMessage());
                            }
                        }
                        
                        if (button != null && buttonText.contains("聊一聊")) {
                            break;
                        }
                    } catch (Exception e) {
                        log.debug("选择器 '{}' 查找失败: {}", selector, e.getMessage());
                    }
                }
                
            } catch (Exception e) {
                log.error("查找按钮失败: {}", e.getMessage());
                // 不再保存页面源码
                continue;
            }
            
            // 提取 jobId（用于更新投递状态）
            Long jobIdForUpdate = null;
            if (i < lastApiEntities.size()) {
                jobIdForUpdate = lastApiEntities.get(i).getJobId();
            }
            if (jobIdForUpdate == null) {
                jobIdForUpdate = extractJobIdFromCard(currentJobCard);
            }

            // 检查按钮文本并点击
            if (button != null && buttonText.contains("聊一聊")) {
                try {
                    // 在点击按钮前进行鼠标微调，先向右移动2像素，再向左移动2像素
                    try {
                        var boundingBox = button.boundingBox();
                        if (boundingBox != null) {
                            double centerX = boundingBox.x + boundingBox.width / 2;
                            double centerY = boundingBox.y + boundingBox.height / 2;
                            
                            // 先移动到按钮中心
                            page.mouse().move(centerX, centerY);
                            Thread.sleep(50);
                            
                            // 向右移动2像素
                            page.mouse().move(centerX + 2, centerY);
                            Thread.sleep(50);
                            
                            // 向左移动2像素（回到中心再向左2像素）
                            page.mouse().move(centerX - 2, centerY);
                            Thread.sleep(50);
                            
                            // 回到中心位置
                            page.mouse().move(centerX, centerY);
                            Thread.sleep(50);
                            
                            log.debug("完成鼠标微调，准备点击按钮");
                        }
                    } catch (Exception moveError) {
                        log.warn("鼠标微调失败，直接点击按钮: {}", moveError.getMessage());
                    }
                    
                    dismissBlockingModals();
                    button.click(new Locator.ClickOptions().setTimeout(10_000));
                    // PlaywrightUtil.sleep(1); // 等待点击响应
                    
                    // 猎聘会自动发送固定的App打招呼语，我们在聊天窗口里追加一条AI个性化消息，避免每条都一样
                    try {
                        // 等待聊天界面加载
                        page.waitForSelector(CHAT_HEADER, new Page.WaitForSelectorOptions().setTimeout(3000));

                        sendAiFollowUp(jobName, companyName);

                        // 直接关闭聊天窗口
                        Locator close = page.locator(CHAT_CLOSE);
                        if (close.count() > 0) {
                            PlaywrightUtil.sleep(1);
                            close.click();
                        }
                        
                        resultList.add(sb.append("【").append(companyName).append(" ").append(jobName).append(" ").append(salary).append(" ").append(recruiterName).append(" ").append("】").toString());
                        sb.setLength(0);
                        // 点击成功后标记为已投递
                        if (jobIdForUpdate != null) {
                            liepinService.markDelivered(jobIdForUpdate);
                        }
                        
                    } catch (Exception e) {
                        log.warn("关闭聊天窗口失败，但投递可能已成功: {}", e.getMessage());
                        // 即使关闭失败，也认为投递成功
                        resultList.add(sb.append("【").append(companyName).append(" ").append(jobName).append(" ").append(salary).append(" ").append(recruiterName).append(" ").append("】").toString());
                        sb.setLength(0);
                        if (jobIdForUpdate != null) {
                            liepinService.markDelivered(jobIdForUpdate);
                        }
                    }
                    
                } catch (Exception e) {
                    log.error("点击按钮失败: {}，尝试关闭遮挡弹窗后重试一次", e.getMessage());
                    try {
                        dismissBlockingModals();
                        button.click(new Locator.ClickOptions().setTimeout(10_000));
                        // 猎聘会自动发送打招呼语，点击成功视为已投递
                        resultList.add(sb.append("【").append(companyName).append(" ").append(jobName).append(" ").append(salary).append(" ").append(recruiterName).append(" ").append("】").toString());
                        sb.setLength(0);
                        if (jobIdForUpdate != null) {
                            liepinService.markDelivered(jobIdForUpdate);
                        }
                    } catch (Exception retryError) {
                        log.error("重试点击仍失败: {}", retryError.getMessage());
                    }
                }
            } else {
                // 如果按钮是“继续聊”，视为已投递
                if (button != null && buttonText.contains("继续聊") && jobIdForUpdate != null) {
                    liepinService.markDelivered(jobIdForUpdate);
                }
                if (button != null) {
                    log.debug("跳过岗位（按钮文本不匹配）: 【{}】的【{}·{}】岗位，按钮文本: '{}'", companyName, jobName, salary, buttonText);
                } else {
                    // 不再保存页面源码
                }
            }
        }
    }

    // 从岗位卡片的 data 属性中提取 jobId（兼容 lastApiEntities 缺失场景）
    private Long extractJobIdFromCard(Locator card) {
        try {
            String ext = card.getAttribute("data-tlg-ext");
            if (ext != null && !ext.isEmpty()) {
                try {
                    String decoded = java.net.URLDecoder.decode(ext, java.nio.charset.StandardCharsets.UTF_8);
                    com.fasterxml.jackson.databind.JsonNode node = new com.fasterxml.jackson.databind.ObjectMapper().readTree(decoded);
                    String jobIdStr = node.path("jobId").asText(null);
                    if (jobIdStr != null && !jobIdStr.isEmpty()) {
                        return Long.parseLong(jobIdStr);
                    }
                } catch (Exception ignore) {
                    java.util.regex.Matcher m = java.util.regex.Pattern.compile("\\\\\"jobId\\\\\":\\\\\"(\\d+)\\\\\"").matcher(ext);
                    if (m.find()) {
                        return Long.parseLong(m.group(1));
                    }
                }
            }
            String scm = card.getAttribute("data-tlg-scm");
            if (scm != null) {
                java.util.regex.Matcher m = java.util.regex.Pattern.compile("jobId=(\\d+)").matcher(scm);
                if (m.find()) {
                    return Long.parseLong(m.group(1));
                }
            }
        } catch (Exception ignore) {}
        return null;
    }
}
