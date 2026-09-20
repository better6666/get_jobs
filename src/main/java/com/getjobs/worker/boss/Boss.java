package com.getjobs.worker.boss;

import com.getjobs.application.entity.AiEntity;
import com.getjobs.application.service.AiService;
import com.getjobs.application.service.BossService;
import com.getjobs.worker.utils.Job;
import com.getjobs.worker.utils.JobUtils;
import com.getjobs.worker.utils.PlaywrightUtil;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Response;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONObject;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;

import static com.getjobs.worker.boss.Locators.*;


/**
 * @author loks666
 * 项目链接: <a href=
 * "https://github.com/loks666/get_jobs">https://github.com/loks666/get_jobs</a>
 * Boss直聘自动投递
 */
@Slf4j
@Component
@Scope("prototype")
@RequiredArgsConstructor
public class Boss {

    /** 最近发出的AI招呼语（供提示词去重，避免每条都一个模子） */
    private final java.util.Deque<String> recentGreetings = new java.util.concurrent.ConcurrentLinkedDeque<>();

    /** 连续投递失败计数 + 触发上限标志（用于自动结束平台，让队列切换下一个） */
    private int consecutiveFailures = 0;
    private boolean limitReached = false;

    @Setter
    private Page page;
    @Setter
    private BossConfig config;
    private final BossService bossService;
    private final AiService aiService;
    private final com.getjobs.application.service.ScoreRulesService scoreRulesService;

    /** 本次任务使用的评分规则（execute 时从数据库加载，支持用户自定义） */
    private com.getjobs.application.service.ScoreRulesService.Rules scoreRules;
    private Set<String> blackCompanies;
    private Set<String> blackRecruiters;
    private Set<String> blackJobs;
    // 记录 encryptId -> encryptUserId 的映射，用于后续更新投递状态
    private final ConcurrentMap<String, String> encryptIdToUserId = new ConcurrentHashMap<>();
    @Setter
    private ProgressCallback progressCallback;
    @Setter
    private Supplier<Boolean> shouldStopCallback;

    private final List<Job> resultList = new ArrayList<>();

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private com.getjobs.application.service.JobParserService jobParserService;
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private com.getjobs.application.service.HardFilterService hardFilterService;
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private com.getjobs.application.service.JobScorerService jobScorerService;
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private com.getjobs.application.service.ResumeService resumeService;
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private com.getjobs.application.service.AiGreetingService aiGreetingService;
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private com.getjobs.application.service.AgentStrategyService agentStrategyService;
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private com.getjobs.application.service.ApplicationRecordService applicationRecordService;

    /** Boss 首页，UI 搜索的入口 */
    private static final String BOSS_HOME_URL = "https://www.zhipin.com";
    /**
     * true=先回首页、在搜索框里做一次真实 UI 搜索再进列表页；false=直接拼 URL 跳转。
     * <p>
     * 默认关掉。实测 Boss 首页在受控标签页里会一直转圈加载不完，navigate 要 60 秒才返回，
     * 期间 evaluate / locator 全部无限期阻塞（这两个 API 没有默认超时）—— 启动慢、投递卡死、
     * 管理页面按钮没反应都出在这。而岗位列表页在同一个浏览器里是秒开的。
     * 想重新试 UI 搜索路径时再打开。
     */
    private static final boolean USE_UI_SEARCH = false;
    /** 等待页面加载状态的超时（毫秒），绝不能不设 —— 见 waitForPageSettled 的说明 */
    private static final double LOAD_STATE_TIMEOUT = 10_000;

    /**
     * 进度回调接口
     */
    @FunctionalInterface
    public interface ProgressCallback {
        void accept(String message, Integer current, Integer total);
    }

    // 通过 Lombok @RequiredArgsConstructor 使用构造器注入 bossService 与 aiService

    public void prepare() {
        // 调整 boss_data 表结构：将 encrypt_id、encrypt_user_id 前置
        try { bossService.ensureBossDataColumnOrder(); } catch (Throwable ignore) {}
        // 从数据库加载黑名单
        this.blackCompanies = bossService.getBlackCompanies();
        this.blackRecruiters = bossService.getBlackRecruiters();
        this.blackJobs = bossService.getBlackJobs();

        log.info("黑名单加载完成: 公司({}) 招聘者({}) 职位({})",
                blackCompanies != null ? blackCompanies.size() : 0,
                blackRecruiters != null ? blackRecruiters.size() : 0,
                blackJobs != null ? blackJobs.size() : 0);
        // 不在页面初始化阶段入库，仅用于后续点击卡片时按需入库
    }

    /**
     * 执行投递
     */
    public int execute() {
        consecutiveFailures = 0;
        limitReached = false;
        scoreRules = scoreRulesService.loadRules();
        log.info("已加载JD评分规则（阈值:{} 学历硬排除:{}项 岗位硬排除:{}项）",
                scoreRules.threshold, scoreRules.degreeReject.size(), scoreRules.titleReject.size());
        for (String cityCode : config.getCityCode()) {
            if (shouldStopCallback != null && Boolean.TRUE.equals(shouldStopCallback.get())) {
                progressCallback.accept("用户取消投递", 0, 0);
                break;
            }
            if (limitReached) break;
            postJobByCity(cityCode);
            if (shouldStopCallback != null && Boolean.TRUE.equals(shouldStopCallback.get())) {
                progressCallback.accept("用户取消投递", 0, 0);
                break;
            }
            if (limitReached) break;
        }
        if (limitReached) {
            progressCallback.accept("Boss疑似达到今日上限，本轮提前结束（明天再投）", 0, 0);
        }
        return resultList.size();
    }

    /**
     * 获取结果列表
     */
    public List<Job> getResultList() {
        return new ArrayList<>(resultList);
    }

    /**
     * 更新黑名单（从聊天记录中）
     */
    public Map<String, Set<String>> updateBlacklistFromChats() {
        page.navigate("https://www.zhipin.com/web/geek/chat");
        PlaywrightUtil.sleep(3);

        int newBlacklistCount = 0;
        boolean shouldBreak = false;
        while (!shouldBreak) {
            try {
                Locator bottomLocator = page.locator(FINISHED_TEXT);
                if (bottomLocator.count() > 0 && "没有更多了".equals(bottomLocator.textContent())) {
                    shouldBreak = true;
                }
            } catch (Exception ignore) {
            }

            Locator items = page.locator(CHAT_LIST_ITEM);
            int itemCount = items.count();

            for (int i = 0; i < itemCount; i++) {
                try {
                    Locator companyElements = page.locator(COMPANY_NAME_IN_CHAT);
                    Locator messageElements = page.locator(LAST_MESSAGE);

                    if (i >= companyElements.count() || i >= messageElements.count()) {
                        break;
                    }

                    String companyName = null;
                    String message = null;
                    int retryCount = 0;

                    while (true) {
                        try {
                            companyName = companyElements.nth(i).textContent();
                            message = messageElements.nth(i).textContent();
                            break;
                        } catch (Exception e) {
                            retryCount++;
                            if (retryCount >= 2) {
                                log.info("尝试获取元素文本2次失败，放弃本次获取");
                                break;
                            }
                            log.info("页面元素已变更，正在重试第{}次获取元素文本...", retryCount);
                            PlaywrightUtil.sleep(1);
                        }
                    }

                    if (companyName != null && message != null) {
                        boolean match = message.contains("不") || message.contains("感谢") || message.contains("但")
                                || message.contains("遗憾") || message.contains("需要本") || message.contains("对不");
                        boolean nomatch = message.contains("不是") || message.contains("不生");
                        if (match && !nomatch) {
                            if (blackCompanies.stream().anyMatch(companyName::contains)) {
                                continue;
                            }
                            companyName = companyName.replaceAll("\\.{3}", "");
                            if (companyName.matches(".*(\\p{IsHan}{2,}|[a-zA-Z]{4,}).*")) {
                                blackCompanies.add(companyName);
                                // 保存到数据库
                                bossService.addBlacklist("company", companyName);
                                newBlacklistCount++;
                                log.info("黑名单公司：【{}】，信息：【{}】", companyName, message);
                            }
                        }
                    }
                } catch (Exception e) {
                    log.error("寻找黑名单公司异常...", e);
                }
            }

            try {
                Locator scrollElement = page.locator(SCROLL_LOAD_MORE);
                if (scrollElement.count() > 0) {
                    scrollElement.scrollIntoViewIfNeeded();
                } else {
                    page.evaluate("window.scrollTo(0, document.body.scrollHeight);");
                }
            } catch (Exception e) {
                log.error("滚动元素出错", e);
                break;
            }
        }
        log.info("黑名单公司数量：{}，本次新增：{}", (blackCompanies != null ? blackCompanies.size() : 0), newBlacklistCount);

        Map<String, Set<String>> result = new HashMap<>();
        result.put("blackCompanies", new HashSet<>(blackCompanies != null ? blackCompanies : Collections.emptySet()));
        result.put("blackRecruiters", new HashSet<>(blackRecruiters != null ? blackRecruiters : Collections.emptySet()));
        result.put("blackJobs", new HashSet<>(blackJobs != null ? blackJobs : Collections.emptySet()));
        return result;
    }

    private void postJobByCity(String cityCode) {
        String searchUrl = getSearchUrl(cityCode);
        for (String keyword : config.getKeywords()) {
            // 检查是否需要停止
            if (shouldStopCallback.get()) {
                progressCallback.accept("用户取消投递", 0, 0);
                return;
            }

            int postCount = 0;
            // 使用 URLEncoder 对关键词进行编码
            String encodedKeyword = URLEncoder.encode(keyword, StandardCharsets.UTF_8);

            String url = searchUrl + (searchUrl.contains("?") ? "&" : "?") + "query=" + encodedKeyword;
            // 单个关键词失败不该让整个投递任务中止，下面整段都包在 try 里
            try {
            // 进列表页 + 等列表渲染，整段带重试
            openJobListWithRetry(keyword, url, cityCode);

            // 1. 基于 footer 出现滚动到底，确保加载全部岗位
            int lastCount = -1;
            int stableTries = 0;
            int staleHits = 0;
            // 上限 300 轮：原来写的是 5000，而且 stableTries 只触发强制触底、从不退出循环，
            // 一旦 footer 选择器匹配不到就会空转几千轮，每轮一次 evaluate + count，
            // 能把 playwright 线程占死几十分钟 —— 整个应用跟着卡住、投递任务也永远结束不了。
            boolean loadedAll = false;
            for (int i = 0; i < 300; i++) {
                // 停止检查：滚动加载过程中也要及时响应
                if (shouldStopCallback != null && Boolean.TRUE.equals(shouldStopCallback.get())) {
                    progressCallback.accept("用户取消投递", 0, 0);
                    return;
                }
                // 滚动加载期间 Boss 会频繁增删 frame，Playwright 派发这些事件时可能
                // 引用到已销毁的 frame（Object doesn't exist: frame@...），异常会顺着
                // 当时在飞的那个调用抛出来。这类异常和调用本身无关，跳过这一轮继续滚就行。
                try {
                    // footer 可见不能立刻就当作"加载完了"：窗口最大化时首屏很短，
                    // 第一轮 footer 就是可见的，会导致只拿到首屏 15 个岗位就退出
                    // （实测同样的搜索条件，正常滚完是 300 个）。
                    // 必须先滚一段、并且连着几轮没有新增岗位，footer 才算数。
                    boolean footerVisible = false;
                    Locator footer = page.locator("div#footer, #footer");
                    if (footer.count() > 0 && footer.first().isVisible()) {
                        footerVisible = true;
                    }
                    if (footerVisible && stableTries >= 2) {
                        log.info("【{}】已滚动到底部且连续 {} 轮无新增，判定加载完毕", keyword, stableTries);
                        loadedAll = true;
                        break;
                    }
                    // 按视口高度的90%渐进滚动，触发懒加载
                    page.evaluate("() => window.scrollBy(0, Math.floor(window.innerHeight * 1.5))");

                    // 获取卡片数量变化，判断是否需要强制触底
                    Locator cardsProbe = page.locator("//ul[contains(@class, 'rec-job-list')]//li[contains(@class, 'job-card-box')]");
                    int currentCount = cardsProbe.count();
                    if (currentCount == lastCount) {
                        stableTries++;
                    } else {
                        stableTries = 0;
                    }
                    lastCount = currentCount;

                    if (stableTries >= 3) { // 连续多次无新增，则强制触底一次
                        page.evaluate("() => window.scrollTo(0, document.body.scrollHeight)");
                        // 触底不再等待，继续检测 footer 出现
                    }
                    // 强制触底之后仍然连着好几轮没有新岗位，就认定加载完了。
                    // 不能只靠 footer —— Boss 有些版式根本没有 #footer，只等它就是死循环。
                    if (stableTries >= 8) {
                        log.info("【{}】连续 {} 轮没有新增岗位，判定已加载完毕", keyword, stableTries);
                        loadedAll = true;
                        break;
                    }
                } catch (Exception e) {
                    if (!isStaleObjectError(e)) {
                        throw e;
                    }
                    staleHits++;
                    if (staleHits > 20) {
                        log.warn("【{}】滚动期间反复出现失效对象异常({}次)，停止继续加载", keyword, staleHits);
                        break;
                    }
                    PlaywrightUtil.sleep(1);
                }
            }
            if (!loadedAll) {
                log.warn("【{}】滚动到达 300 轮上限仍未确认加载完毕，按当前已加载的岗位继续", keyword);
            }
            // 统计最终岗位数量
            int loadedCount = countJobCards();
            log.info("【{}】岗位已全部加载，总数:{}", keyword, loadedCount);
            progressCallback.accept("岗位加载完成：" + keyword, 0, loadedCount);

            // 2. 回到页面顶部
            page.evaluate("window.scrollTo(0, 0);");
            PlaywrightUtil.sleep(1);

            // 3. 逐个遍历所有岗位
            Locator cards = page.locator("//ul[contains(@class, 'rec-job-list')]//li[contains(@class, 'job-card-box')]");
            int count = cards.count();
            for (int i = 0; i < count; i++) {
                // 检查是否需要停止
                if (shouldStopCallback != null && Boolean.TRUE.equals(shouldStopCallback.get())) {
                    progressCallback.accept("用户取消投递", i, count);
                    return;
                }

                // 重新获取卡片，避免元素过期
                cards = page.locator("//ul[contains(@class, 'rec-job-list')]//li[contains(@class, 'job-card-box')]");
                // 在点击卡片时同步等待岗位详情接口返回，随后解析并入库
                Response detailResp = null;
                try {
                    if (i == 0 && count > 1) {
                        // 第一个卡片默认展开不会触发请求：先切到第二个，再切回第一个，并在返回第一个时监听响应
                        final Locator secondCard = cards.nth(1);
                        secondCard.click();
                        PlaywrightUtil.sleep(1);
                        final Locator firstCard = cards.nth(0);
                        detailResp = page.waitForResponse(r -> {
                            try {
                                return r.url() != null && r.url().contains("/wapi/zpgeek/job/detail.json")
                                        && "GET".equalsIgnoreCase(r.request().method());
                            } catch (Throwable ignore) { return false; }
                        }, firstCard::click);
                    } else {
                        final Locator cardToClick = cards.nth(i);
                        detailResp = page.waitForResponse(r -> {
                            try {
                                return r.url() != null && r.url().contains("/wapi/zpgeek/job/detail.json")
                                        && "GET".equalsIgnoreCase(r.request().method());
                            } catch (Throwable ignore) { return false; }
                        }, cardToClick::click);
                    }
                } catch (Throwable ignore) {
                }
                PlaywrightUtil.sleep(1);

                // 统一从请求返回的 JSON 中获取数据并做过滤
                String jobName = null;
                String jobSalary = null;
                java.util.List<String> tags = new java.util.ArrayList<>();
                String jobDesc = null;
                String bossName = null;
                String bossActive = null;
                String bossCompany = null;
                String bossJobTitle = null;
                String jobDegree = null;
                String jobExperience = null;
                String jobIndustry = null;

                if (detailResp != null) {
                    try {
                        String body = detailResp.text();
                        // 保存原始 JSON 便于调试
                        appendRawJson(body);
                        // 解析并入库（仅在点击卡片触发时执行）
                        processJobDetailJsonAndInsert(body);

                        // 从 JSON 构建用于投递与过滤的字段
                        org.json.JSONObject root = new org.json.JSONObject(body);
                        org.json.JSONObject zpData = root.optJSONObject("zpData");
                        org.json.JSONObject jobInfo = zpData != null ? zpData.optJSONObject("jobInfo") : null;
                        org.json.JSONObject brand = zpData != null ? zpData.optJSONObject("brandComInfo") : null;
                        org.json.JSONObject boss = zpData != null ? zpData.optJSONObject("bossInfo") : null;

                        if (jobInfo != null) {
                            jobName = jobInfo.optString("jobName", "");
                            jobSalary = jobInfo.optString("salaryDesc", "");
                            String city = jobInfo.optString("locationName", "");
                            String exp = jobInfo.optString("experienceName", "");
                            String deg = jobInfo.optString("degreeName", "");
                            if (!city.isEmpty()) tags.add(city);
                            if (!exp.isEmpty()) tags.add(exp);
                            if (!deg.isEmpty()) tags.add(deg);
                            jobDesc = jobInfo.optString("postDescription", "");
                            jobDegree = deg;
                            jobExperience = exp;
                        }

                        if (boss != null) {
                            bossName = boss.optString("name", "");
                            bossActive = boss.optString("activeTimeDesc", "");
                            bossJobTitle = boss.optString("title", "");
                        }

                        if (brand != null) {
                            bossCompany = brand.optString("brandName", "");
                            jobIndustry = brand.optString("industryName", "");
                        }
                    } catch (Throwable e) {
                        log.debug("点击卡片后解析岗位详情用于过滤失败：{}", e.getMessage());
                    }
                }

                // 过滤（全部基于 JSON 字段），并输出过滤原因
                if (jobName != null && blackJobs != null && blackJobs.stream().anyMatch(jobName::contains)) {
                    String term = findMatchedTerm(blackJobs, jobName);
                    log.info("被过滤：职位黑名单命中 | 公司：{} | 岗位：{} | 关键词：{}", bossCompany != null ? bossCompany : "", jobName, term != null ? term : "");
                    continue;
                }
                // HR活跃状态过滤：当开启过滤开关且活跃描述包含“年”时，视为不活跃
                boolean hrInactiveByYear = bossActive != null && bossActive.contains("年");
                if (Boolean.TRUE.equals(config.getFilterDeadHR()) && hrInactiveByYear) {
                    log.info("被过滤：HR活跃状态包含‘年’ | 公司：{} | 岗位：{} | 活跃：{}", bossCompany != null ? bossCompany : "", jobName != null ? jobName : "", bossActive);
                    continue;
                }
                if (bossCompany != null && blackCompanies != null && blackCompanies.stream().anyMatch(bossCompany::contains)) {
                    String term = findMatchedTerm(blackCompanies, bossCompany);
                    log.info("被过滤：公司黑名单命中 | 公司：{} | 岗位：{} | 关键词：{}", bossCompany, jobName != null ? jobName : "", term != null ? term : "");
                    continue;
                }
                if (bossJobTitle != null && blackRecruiters != null && blackRecruiters.stream().anyMatch(bossJobTitle::contains)) {
                    String term = findMatchedTerm(blackRecruiters, bossJobTitle);
                    log.info("被过滤：招聘者黑名单命中 | 公司：{} | 岗位：{} | 招聘者：{} | 关键词：{}", bossCompany != null ? bossCompany : "", jobName != null ? jobName : "", bossJobTitle, term != null ? term : "");
                    continue;
                }

                // JD评分过滤：低分岗位直接不投
                try {
                    if (jobName != null) {
                        int s = scoreJob(jobName, jobDegree, jobExperience, jobIndustry, jobDesc);
                        int threshold = scoreRules != null ? scoreRules.threshold : 80;
                        if (s < threshold) {
                            log.info("被过滤：JD评分不足 | 公司：{} | 岗位：{} | 得分: {}", bossCompany != null ? bossCompany : "", jobName, s);
                            continue;
                        }
                    }
                } catch (Throwable ignore) {
                }

                // 创建Job对象（全部基于 JSON 字段）
                Job job = new Job();
                job.setJobName(jobName != null ? jobName : "");
                job.setSalary(jobSalary != null ? jobSalary : "");
                job.setJobArea(String.join(", ", tags));
                job.setCompanyName(bossCompany != null ? bossCompany : "");
                job.setRecruiter(bossName != null ? bossName : "");
                job.setJobInfo(jobDesc != null ? jobDesc : "");

                // 输出
                progressCallback.accept("正在投递：" + jobName, i + 1, count);
                resumeSubmission(keyword, job);
                postCount++;

                // 连续失败或检测到平台限制时，结束当前平台让队列切换下一个
                if (limitReached) {
                    progressCallback.accept("疑似达到Boss今日上限或页面异常，自动结束本平台投递", null, null);
                    log.info("达到自动结束条件（连续失败/上限提示），结束当前关键词剩余岗位");
                    return;
                }

                // 为避免点击下面的卡片触发页面刷新：在点击5个卡片之后，每次点击后适度下滑
                try {
                    if (i >= 5) {
                        page.evaluate("window.scrollBy(0, 140);");
                        PlaywrightUtil.sleep(1);
                    }
                } catch (Throwable ignore) {}

                // 按 wait_time 降速，别把风控刷出来
                pauseBetweenJobs();

                // 停顿期间可能已经被弹到安全验证页，发现了就停下来等人工过验证，
                // 而不是继续闷头点下去（继续点只会让后面的关键词全部失败）
                if (isSecurityVerifyUrl(safeUrl())) {
                    log.warn("【{}】遍历过程中被跳转到安全验证页：{}", keyword, safeUrl());
                    if (progressCallback != null) {
                        progressCallback.accept("触发Boss安全校验，请在浏览器中手动完成验证", i + 1, count);
                    }
                    waitForSliderVerify(page);
                }
            }
            log.info("【{}】岗位已投递完毕！已投递岗位数量:{}", keyword, postCount);
            } catch (Exception e) {
                log.error("【{}】处理失败，跳过该关键词继续下一个：{}", keyword, e.getMessage(), e);
                if (progressCallback != null) {
                    progressCallback.accept("关键词[" + keyword + "]失败已跳过：" + e.getMessage(), 0, 0);
                }
            }
        }
    }

    /**
     * 解析岗位详情 JSON 并进行入库与黑名单处理（只在点击卡片时调用）。
     */
    private void processJobDetailJsonAndInsert(String body) {
        if (body == null || body.isEmpty()) return;
        try {
            JSONObject root = new JSONObject(body);
            JSONObject zpData = root.optJSONObject("zpData");
            if (zpData == null) return;

            JSONObject jobInfo = zpData.optJSONObject("jobInfo");
            JSONObject brand = zpData.optJSONObject("brandComInfo");
            JSONObject bossInfo = zpData.optJSONObject("bossInfo");
            if (jobInfo == null) return;

            String encryptId = jobInfo.optString("encryptId", null);
            String encryptUserId = jobInfo.optString("encryptUserId", null);
            if (encryptUserId == null && bossInfo != null) {
                // 兼容部分页面字段落在 bossInfo 内
                encryptUserId = bossInfo.optString("encryptUserId", null);
                if (encryptUserId == null) {
                    // 进一步兼容可能的字段命名
                    encryptUserId = bossInfo.optString("encryptBossId", null);
                }
            }
            if (encryptId != null && encryptUserId != null) {
                encryptIdToUserId.put(encryptId, encryptUserId);
            }

            com.getjobs.application.entity.BossJobDataEntity entity = new com.getjobs.application.entity.BossJobDataEntity();
            entity.setJobName(jobInfo.optString("jobName", null));
            entity.setSalary(jobInfo.optString("salaryDesc", null));
            entity.setLocation(jobInfo.optString("locationName", null));
            entity.setExperience(jobInfo.optString("experienceName", null));
            entity.setDegree(jobInfo.optString("degreeName", null));
            entity.setJobDescription(jobInfo.optString("postDescription", null));
            entity.setRecruitmentStatus(jobInfo.optString("jobStatusDesc", null));
            entity.setCompanyAddress(jobInfo.optString("address", null));
            entity.setEncryptId(encryptId);
            entity.setEncryptUserId(encryptUserId);

            entity.setCompanyName(brand != null ? brand.optString("brandName", null) : null);
            entity.setIndustry(brand != null ? brand.optString("industryName", null) : null);
            entity.setIntroduce(brand != null ? brand.optString("introduce", null) : null);
            entity.setFinancingStage(brand != null ? brand.optString("stageName", null) : null);
            entity.setCompanyScale(brand != null ? brand.optString("scaleName", null) : null);

            entity.setHrName(bossInfo != null ? bossInfo.optString("name", null) : null);
            entity.setHrPosition(bossInfo != null ? bossInfo.optString("title", null) : null);
            entity.setHrActiveStatus(bossInfo != null ? bossInfo.optString("activeTimeDesc", null) : null);

            if (encryptId != null && !encryptId.isEmpty()) {
                entity.setJobUrl("https://www.zhipin.com/job_detail/" + encryptId + ".html");
            }

            // 黑名单处理
            boolean filtered = false;
            String companyName = entity.getCompanyName() != null ? entity.getCompanyName() : "";
            String positionName = entity.getJobName() != null ? entity.getJobName() : "";
            String hrPosition = entity.getHrPosition() != null ? entity.getHrPosition() : "";
            try {
                if (blackCompanies != null && blackCompanies.stream().anyMatch(companyName::contains)) filtered = true;
                if (!filtered && blackJobs != null && blackJobs.stream().anyMatch(positionName::contains)) filtered = true;
                if (!filtered && blackRecruiters != null && blackRecruiters.stream().anyMatch(hrPosition::contains)) filtered = true;
            } catch (Throwable ignore) {}

            // HR活跃状态过滤：开启过滤且活跃描述包含“年”，则标记为已过滤，但仍入库
            if (!filtered && Boolean.TRUE.equals(config.getFilterDeadHR())) {
                String hrActive = entity.getHrActiveStatus();
                if (hrActive != null && hrActive.contains("年")) {
                    filtered = true;
                }
            }

            // JD评分过滤：按职业规划打分模型给岗位打分，低于阈值不投（仍入库供统计）
            int jobScore = 100;
            if (!filtered) {
                jobScore = scoreJob(positionName, entity.getDegree(), entity.getExperience(),
                        entity.getIndustry(), entity.getJobDescription());
                // 与主投递循环共用同一套可配置阈值，避免这里写死 80 导致两处判定不一致
                int threshold = scoreRules != null ? scoreRules.threshold : 80;
                if (jobScore < threshold) {
                    filtered = true;
                    log.info("岗位低分跳过 | {} | {} | 得分: {}", positionName, companyName, jobScore);
                }
            }
            entity.setMatchScore(jobScore);

            entity.setDeliveryStatus(filtered ? "已过滤" : "未投递");

            // 入库（若不存在），优先以 encrypt_id + encrypt_user_id 去重；若 userId 缺失，则以 encrypt_id 去重
            if (encryptId != null) {
                try {
                    boolean exists = false;
                    if (encryptUserId != null) {
        exists = bossService.existsBossJob(encryptId, encryptUserId);
                    } else {
        exists = bossService.existsBossJobByEncryptId(encryptId);
                    }
                    if (!exists) {
        bossService.insertBossJob(entity);
                        log.debug("岗位入库：{} | 公司：{} | HR：{} | 状态：{}", entity.getJobName(), entity.getCompanyName(), entity.getHrName(), entity.getDeliveryStatus());
                    }

                    // 同步记录到 AI 求职 Agent 全流程流水与复核队列
                    if (applicationRecordService != null && jobParserService != null && hardFilterService != null && jobScorerService != null) {
                        try {
                            com.getjobs.application.service.JobParserService.ParsedJob pj = jobParserService.parseJob(
                                    "boss",
                                    encryptId,
                                    entity.getJobName(),
                                    entity.getCompanyName(),
                                    entity.getSalary(),
                                    entity.getDegree(),
                                    entity.getExperience(),
                                    entity.getLocation(),
                                    entity.getJobDescription(),
                                    entity.getHrActiveStatus(),
                                    null
                            );
                            com.getjobs.application.service.HardFilterService.FilterResult hf = hardFilterService.checkFilter(pj);
                            com.getjobs.application.service.JobScorerService.ScoreResult sr = jobScorerService.calculateMatchScore(pj);
                            com.getjobs.application.entity.ResumeVersionEntity bestResume = (resumeService != null) ? resumeService.selectBestResume(pj) : null;
                            // 已过滤岗位不再调 AI 生成话术：推理模型单次 30-40s，对注定不投的岗位是纯浪费
                            String greeting = (!filtered && aiGreetingService != null) ? aiGreetingService.generateGreeting(pj, bestResume) : "";
                            com.getjobs.application.entity.ApplicationRecordEntity appRec = applicationRecordService.recordDiscoveredJob(pj, hf, sr, bestResume, greeting);
                            if ("APPLIED".equals(entity.getDeliveryStatus()) && appRec != null) {
                                applicationRecordService.markAsApplied(appRec.getId(), greeting);
                            }
                        } catch (Throwable t) {
                            log.debug("Agent 岗位流水同步异常: {}", t.getMessage());
                        }
                    }
                } catch (Exception e) {
                    log.warn("岗位入库失败：{}", e.getMessage());
                }
            }
        } catch (Throwable e) {
            log.debug("解析岗位详情 JSON 失败：{}", e.getMessage());
        }
    }

    public String decodeSalary(String text) {
        Map<Character, Character> fontMap = new HashMap<>();
        fontMap.put('\uE8F0', '0');
        fontMap.put('\uE8F1', '1');
        fontMap.put('\uE8F2', '2');
        fontMap.put('\uE8F3', '3');
        fontMap.put('\uE8F4', '4');
        fontMap.put('\uE8F5', '5');
        fontMap.put('\uE8F6', '6');
        fontMap.put('\uE8F7', '7');
        fontMap.put('\uE8F8', '8');
        fontMap.put('\uE8F9', '9');
        StringBuilder result = new StringBuilder();
        for (char c : text.toCharArray()) {
            result.append(fontMap.getOrDefault(c, c));
        }
        return result.toString();
    }

    // 安全获取单个文本内容
    public String safeText(Locator root, String selector) {
        Locator node = root.locator(selector);
        try {
            if (node.count() > 0 && node.innerText() != null) {
                return node.innerText().trim();
            }
        } catch (Exception e) {
            // ignore
        }
        return "";
    }

    // 安全获取多个文本内容
    public List<String> safeAllText(Locator root, String selector) {
        try {
            return root.locator(selector).allInnerTexts();
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    // Boss姓名+活跃状态拆分
    public String[] splitBossName(String raw) {
        String[] bossParts = raw.trim().split("\\s+");
        String bossName = bossParts[0];
        String bossActive = bossParts.length > 1 ? String.join(" ", Arrays.copyOfRange(bossParts, 1, bossParts.length)) : "";
        return new String[]{bossName, bossActive};
    }

    // Boss公司+职位拆分
    public String[] splitBossTitle(String raw) {
        String[] parts = raw.trim().split(" · ");
        String company = parts[0];
        String job = parts.length > 1 ? parts[1] : "";
        return new String[]{company, job};
    }

    // 匹配命中词条（用于日志输出过滤原因）
    private String findMatchedTerm(java.util.Collection<String> patterns, String text) {
        if (patterns == null || text == null) return null;
        try {
            for (String p : patterns) {
                if (p != null && !p.isEmpty() && text.contains(p)) {
                    return p;
                }
            }
        } catch (Exception ignore) {
        }
        return null;
    }

    public static String buildSearchUrl(BossConfig config, String cityCode) {
        String baseUrl = "https://www.zhipin.com/web/geek/jobs";
        if (config == null) {
            return baseUrl;
        }
        List<String> params = new ArrayList<>();
        addParam(params, JobUtils.appendParam("city", cityCode));
        addParam(params, JobUtils.appendParam("jobType", config.getJobType()));
        addParam(params, JobUtils.appendListParam("salary", config.getSalary()));
        addParam(params, JobUtils.appendListParam("experience", config.getExperience()));
        addParam(params, JobUtils.appendListParam("degree", config.getDegree()));
        addParam(params, JobUtils.appendListParam("scale", config.getScale()));
        addParam(params, JobUtils.appendListParam("industry", config.getIndustry()));
        addParam(params, JobUtils.appendListParam("stage", config.getStage()));
        if (params.isEmpty()) {
            return baseUrl;
        }
        return baseUrl + "?" + String.join("&", params);
    }

    private static void addParam(List<String> params, String param) {
        if (param == null || param.isEmpty()) {
            return;
        }
        params.add(param.startsWith("&") ? param.substring(1) : param);
    }

    private String getSearchUrl(String cityCode) {
        return buildSearchUrl(config, cityCode);
    }

    /**
     * 进入岗位列表页。
     * <p>
     * 优先模拟真人路径：回首页 -> 在搜索框里逐字输入关键词 -> 点搜索按钮，
     * 而不是冷启动直接把拼好的列表页 URL 丢给浏览器。
     * <p>
     * UI 搜索只能带上关键词和首页当前城市，config 里的城市/薪资/经验等筛选项仍然只能靠 URL，
     * 所以落地之后如果条件对不上，会再做一次同标签页跳转 —— 此时已经是热会话 + 同源 referer，
     * 和冷启动直闯不是一回事。UI 搜索任何一步失败都会退回原来的直接跳转，不影响主流程。
     */
    private void openJobList(String keyword, String targetUrl, String cityCode) {
        boolean uiSearchDone = USE_UI_SEARCH && searchFromHomePage(keyword);
        if (!uiSearchDone) {
            navigateToJobList(targetUrl);
        } else if (needsUrlFilters(targetUrl, cityCode)) {
            log.info("【{}】UI搜索已落地，补充筛选条件跳转：{}", keyword, targetUrl);
            navigateToJobList(targetUrl);
        }
        warnIfSecurityCheck(keyword);
    }

    /**
     * 在 Boss 首页搜索框里做一次真实的 UI 搜索。
     *
     * @return 是否成功落到岗位列表页
     */
    private boolean searchFromHomePage(String keyword) {
        try {
            String current = page.url();
            if (current == null || !current.startsWith(BOSS_HOME_URL) || current.contains("/web/geek/")) {
                navigateTo(BOSS_HOME_URL);
            }
            // 首页落地后还会自己跳一次（/shanghai/?seoRefer=index 之类），
            // 不等它跳完就找搜索框，会卡在 "waiting for navigation to finish" 直到超时
            waitForPageSettled();

            Locator input = page.locator(HOME_SEARCH_INPUT).first();
            input.waitFor(new Locator.WaitForOptions().setTimeout(10_000));
            input.click();
            input.fill("");
            // 逐字输入，模拟真人打字节奏；一次性 fill 在输入行为层面太干净了
            input.pressSequentially(keyword, new Locator.PressSequentiallyOptions().setDelay(140));
            PlaywrightUtil.sleep(1);

            Locator searchBtn = page.locator(HOME_SEARCH_BUTTON).first();
            if (searchBtn.count() > 0) {
                searchBtn.click();
            } else {
                input.press("Enter");
            }
            // 表单提交是当前标签页跳转；若 Boss 改成新开标签页，这里会超时并退回直接跳转
            page.waitForURL("**/web/geek/jobs**", new Page.WaitForURLOptions().setTimeout(15_000));
            log.info("【{}】已通过首页搜索框进入岗位列表：{}", keyword, page.url());
            return true;
        } catch (Exception e) {
            log.warn("【{}】首页UI搜索失败，退回直接跳转：{}", keyword, e.getMessage());
            return false;
        }
    }

    /**
     * UI 搜索落地后，判断还需不需要用 URL 把筛选条件补上。
     */
    private boolean needsUrlFilters(String targetUrl, String cityCode) {
        String landed = page.url();
        if (landed == null) {
            return true;
        }
        // 首页搜索用的是首页当前城市，不一定等于配置里的城市
        if (cityCode != null && !cityCode.isEmpty() && !landed.contains("city=" + cityCode)) {
            return true;
        }
        // 除 city/query 外还有别的筛选项（薪资、经验、学历……），UI 搜索带不上
        int queryStart = targetUrl.indexOf('?');
        if (queryStart < 0) {
            return false;
        }
        for (String param : targetUrl.substring(queryStart + 1).split("&")) {
            int eq = param.indexOf('=');
            String name = eq < 0 ? param : param.substring(0, eq);
            String value = eq < 0 ? "" : param.substring(eq + 1);
            if (value.isEmpty() || "city".equals(name) || "query".equals(name)) {
                continue;
            }
            return true;
        }
        return false;
    }

    private void navigateToJobList(String url) {
        navigateTo(url);
    }

    /**
     * 导航并重试一次。
     * <p>
     * Boss 页面自己会做客户端跳转，撞上时 Playwright 报 net::ERR_ABORTED；
     * 这类失败重试一次基本就过了，不该让整个投递任务因此中止。
     */
    private void navigateTo(String url) {
        // Boss 的 SPA 在网络不佳或被限流时，15 秒常常不够，之前实测连着两次都超时
        Page.NavigateOptions options = new Page.NavigateOptions()
                .setWaitUntil(com.microsoft.playwright.options.WaitUntilState.DOMCONTENTLOADED)
                .setTimeout(45_000);
        try {
            page.navigate(url, options);
        } catch (Exception first) {
            // 导航期间页面自己又跳了一次时，Playwright 会抛 "Object doesn't exist: request@/frame@"，
            // 但页面其实已经到位了。先看落地 URL，别白白重试一遍。
            if (landedOn(url)) {
                log.debug("导航报了失效对象异常但页面已到位，忽略：{}", first.getMessage());
                settleAfterNavigation();
                return;
            }
            log.warn("导航失败，1秒后重试一次：{} | {}", url, first.getMessage());
            PlaywrightUtil.sleep(1);
            try {
                page.navigate(url, options);
            } catch (Exception second) {
                if (!landedOn(url)) {
                    throw second;
                }
                log.debug("重试同样报失效对象异常但页面已到位，忽略：{}", second.getMessage());
            }
        }
        settleAfterNavigation();
    }

    /**
     * 判断页面是否已经落在目标地址上（只比较路径，查询参数会被 Boss 改写）。
     */
    private boolean landedOn(String targetUrl) {
        try {
            String current = page.url();
            if (current == null) {
                return false;
            }
            String targetPath = targetUrl.split("\\?")[0];
            return current.startsWith(targetPath);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 等页面真正稳定下来再继续。
     * <p>
     * DOMCONTENTLOADED 返回之后 Boss 还会自己做客户端跳转，此时 frame 被替换，
     * 后续任何 locator 调用都会报 "Object doesn't exist: frame@..."。
     */
    private void settleAfterNavigation() {
        waitForPageSettled();
    }

    /**
     * 等页面进入 domcontentloaded，最多等 {@value #LOAD_STATE_TIMEOUT} 毫秒。
     * <p>
     * 两个坑都踩过了：
     * 一是不能等 LOAD/NETWORKIDLE —— Boss 是 SPA + WebSocket 长连接，这两个状态可能永远不到；
     * 二是必须显式给超时 —— 不给超时就是无限期挂起，而"卡住"不是异常，
     * 外面包 try/catch 完全没用（实测把线程挂了两分钟以上还在等）。
     */
    private void waitForPageSettled() {
        try {
            page.waitForLoadState(com.microsoft.playwright.options.LoadState.DOMCONTENTLOADED,
                    new Page.WaitForLoadStateOptions().setTimeout(LOAD_STATE_TIMEOUT));
        } catch (Exception ignore) {
            // 等不到就算了，后面的 locator 调用自己有超时
        }
        PlaywrightUtil.sleep(2);
    }

    /**
     * 判断是不是 Playwright 的"对象已失效"异常。
     * <p>
     * 页面频繁增删 frame 时，Playwright Java 在派发事件时会引用到已经销毁的对象，
     * 抛 "Object doesn't exist: frame@..."。这个异常和当时在飞的那个调用没有因果关系，
     * 连接本身仍然可用，重试即可。
     */
    private static boolean isStaleObjectError(Throwable e) {
        String message = e.getMessage();
        if (message == null) {
            return false;
        }
        // 两类都是"页面在动"导致的瞬时异常，跟调用本身没有因果关系，重试就好：
        // - Object doesn't exist: frame@/request@  事件派发时引用到已销毁的对象
        // - Execution context was destroyed         求值期间页面发生了导航
        return message.contains("Object doesn't exist")
                || message.contains("Execution context was destroyed");
    }

    /**
     * 进入岗位列表并等列表渲染出来，整段带重试。
     * Boss 首页/列表页在登录态下会连着跳好几次，一次失败很正常。
     */
    private void openJobListWithRetry(String keyword, String targetUrl, String cityCode) {
        // 只重试一次：每次都要走导航(最长45秒×2) + 等列表(15秒×3)，
        // 试三轮的话一个关键词失败要耗掉好几分钟，界面上看着就是"卡住不动"
        RuntimeException last = null;
        for (int attempt = 1; attempt <= 2; attempt++) {
            try {
                openJobList(keyword, targetUrl, cityCode);
                waitForJobList();
                return;
            } catch (RuntimeException e) {
                last = e;
                log.warn("【{}】进入岗位列表失败（第{}/2次）：{}", keyword, attempt,
                        e.getMessage() == null ? e.toString() : e.getMessage().split("\n")[0]);
                if (attempt < 2) {
                    PlaywrightUtil.sleep(3);
                }
            }
        }
        throw last;
    }

    /**
     * 统计当前列表里的岗位卡片数量，对失效对象异常做重试。
     */
    private int countJobCards() {
        for (int attempt = 1; attempt <= 3; attempt++) {
            try {
                return page.locator(JOB_LIST_SELECTOR).count();
            } catch (Exception e) {
                if (!isStaleObjectError(e) || attempt == 3) {
                    if (isStaleObjectError(e)) {
                        log.warn("统计岗位数量始终失败，按 0 处理：{}", e.getMessage());
                        return 0;
                    }
                    throw e;
                }
                PlaywrightUtil.sleep(1);
            }
        }
        return 0;
    }

    /**
     * 等待岗位列表容器出现，带重试。
     * 页面在这期间可能还在跳转，一次失败不代表真的没有列表。
     */
    private void waitForJobList() {
        // Boss 改版频繁，推荐页和搜索结果页的容器类名不一样。
        // 用逗号把候选选择器拼成一个，让 Playwright 一次性等"任意一个先出现"，
        // 不要逐个 8 秒串行试 —— 那样一轮就要 48 秒，页面正常时也慢得像卡死。
        String containers = String.join(", ",
                "ul.rec-job-list",
                "ul.job-list-box",
                ".job-list-box",
                ".search-job-result",
                "li.job-card-box",
                "li.job-card-wrapper");

        for (int attempt = 1; attempt <= 3; attempt++) {
            try {
                page.waitForSelector(containers,
                        new Page.WaitForSelectorOptions().setTimeout(15_000));
                log.info("岗位列表容器已出现（第{}次尝试）", attempt);
                return;
            } catch (Exception e) {
                log.warn("等待岗位列表失败（第{}/3次），当前页面: {}", attempt, safeUrl());
                settleAfterNavigation();
            }
        }
        dumpListPageStructure();
        throw new IllegalStateException("岗位列表始终未出现，当前页面: " + safeUrl());
    }

    private String safeUrl() {
        try {
            return page.url();
        } catch (Exception e) {
            return "(取不到URL)";
        }
    }

    /**
     * 列表容器一个都没命中时，把页面上的候选列表结构打出来，方便修选择器。
     */
    private void dumpListPageStructure() {
        try {
            Object info = page.evaluate("""
                    () => {
                      const uls = Array.from(document.querySelectorAll('ul,div'))
                        .filter(el => el.className && typeof el.className === 'string'
                                   && /job|list|rec/i.test(el.className))
                        .slice(0, 15)
                        .map(el => el.tagName.toLowerCase() + '.' + el.className.trim().replace(/\\s+/g, '.')
                                 + ' (children=' + el.childElementCount + ')');
                      return { title: document.title, url: location.href, candidates: uls };
                    }""");
            log.warn("列表页结构快照: {}", info);
        } catch (Exception e) {
            log.warn("抓取列表页结构失败: {}", e.getMessage());
        }
    }

    /**
     * 落地后检查是否被弹到风控/验证页，日志里能直接看出是哪一步触发的。
     */
    private void warnIfSecurityCheck(String keyword) {
        String url = page.url();
        if (url == null) {
            return;
        }
        if (isSecurityVerifyUrl(url)) {
            log.warn("【{}】进入岗位列表时被跳转到验证页：{}", keyword, url);
            if (progressCallback != null) {
                progressCallback.accept("触发Boss安全校验，请手动完成验证", 0, 0);
            }
            waitForSliderVerify(page);
        }
    }

    /**
     * 备注：目前Boss无法通过新标签页打开立即沟通按钮，所以只能点击更多详情，然后从更多详情里打开聊天按钮
     */
    @SneakyThrows
    private void resumeSubmission(String keyword, Job job) {
        // 若收到停止指令，直接短路返回
        if (shouldStopCallback != null && Boolean.TRUE.equals(shouldStopCallback.get())) {
            log.info("停止指令已触发，跳过投递 | 公司：{} | 岗位：{}", job.getCompanyName(), job.getJobName());
            return;
        }
        // 调试模式：仅遍历不投递
        if (Boolean.TRUE.equals(config.getDebugger())) {
            log.info("调试模式：仅遍历岗位，不投递 | 公司：{} | 岗位：{}", job.getCompanyName(), job.getJobName());
            return;
        }

        // 1. 查找"查看更多信息"按钮（必须存在且新开页）
        Locator moreInfoBtn = page.locator("a.more-job-btn");
        if (moreInfoBtn.count() == 0) {
            log.warn("未找到\"查看更多信息\"按钮，跳过...");
            return;
        }
        // 强制用js新开tab
        String href = moreInfoBtn.first().getAttribute("href");
        if (href == null || !href.startsWith("/job_detail/")) {
            log.warn("未获取到岗位详情链接，跳过...");
            return;
        }
        String detailUrl = "https://www.zhipin.com" + href;
        // 2. 在新窗口打开详情页
        Page detailPage = page.context().newPage();
        detailPage.navigate(detailUrl);
        PlaywrightUtil.sleep(1);

        // 2.1 从详情页补充提取岗位 JD 与公司名（防止列表页未拦截到接口数据）
        if (job.getJobInfo() == null || job.getJobInfo().isBlank()) {
            try {
                Locator descLoc = detailPage.locator("div.job-sec-text, .job-detail-section, .job-detail-box, .desc");
                if (descLoc.count() > 0) {
                    String descText = descLoc.first().innerText();
                    if (descText != null && !descText.isBlank()) {
                        job.setJobInfo(descText.trim());
                        log.info("从详情页成功提取岗位JD（字数：{}）", job.getJobInfo().length());
                    }
                }
            } catch (Exception ignore) {
            }
        }
        if (job.getCompanyName() == null || job.getCompanyName().isBlank()) {
            try {
                Locator compLoc = detailPage.locator(".company-info a, .business-info, .brand-name");
                if (compLoc.count() > 0) {
                    job.setCompanyName(compLoc.first().innerText().trim());
                }
            } catch (Exception ignore) {
            }
        }

        // 2.2 提前调用 AI 生成针对该岗位的专属打招呼语
        String aiMessage = null;
        if (Boolean.TRUE.equals(config.getEnableAI())) {
            String jd = job.getJobInfo();
            aiMessage = generateAiMessage(keyword, job.getJobName(), job.getCompanyName(), jd);
        }
        String message = isValidString(aiMessage) ? aiMessage : config.getSayHi();

        // 3. 查找"立即沟通"按钮
        Locator chatBtn = detailPage.locator("a.btn-startchat, a.op-btn-chat");
        boolean foundChatBtn = false;
        for (int i = 0; i < 5; i++) {
            if (shouldStopCallback != null && Boolean.TRUE.equals(shouldStopCallback.get())) {
                log.info("停止指令已触发，结束查找聊天按钮 | 公司：{} | 岗位：{}", job.getCompanyName(), job.getJobName());
                try { detailPage.close(); } catch (Exception ignore) {}
                return;
            }
            if (chatBtn.count() > 0 && (chatBtn.first().textContent().contains("立即沟通"))) {
                foundChatBtn = true;
                break;
            }
            PlaywrightUtil.sleep(1);
        }
        if (!foundChatBtn) {
            log.warn("未找到立即沟通按钮，跳过岗位: {}", job.getJobName());
            consecutiveFailures++;
            if (consecutiveFailures >= 8) {
                limitReached = true;
            }
            // 关闭详情页
            try {
                detailPage.close();
            } catch (Exception ignore) {
            }
            return;
        }
        chatBtn.first().click();
        PlaywrightUtil.sleep(1);

        // 4. 等待聊天输入框（可能出现在详情页内联弹窗，也可能在原地跳转/新开的聊天页）
        Locator inputLocator = detailPage.locator("div#chat-input.chat-input[contenteditable='true'], textarea.input-area");
        Page chatPage = detailPage;
        boolean inputReady = false;
        boolean autoGreeted = false;
        for (int i = 0; i < 15; i++) {
            if (shouldStopCallback != null && Boolean.TRUE.equals(shouldStopCallback.get())) {
                log.info("停止指令已触发，结束等待聊天输入框 | 公司：{} | 岗位：{}", job.getCompanyName(), job.getJobName());
                try { detailPage.close(); } catch (Exception ignore) {}
                return;
            }
            // Boss 点完立即沟通会先弹"已向BOSS发送消息"确认框，需点击弹窗内的"继续沟通"按钮才能进入聊天
            try {
                Object clicked = detailPage.evaluate("() => {"
                        + "  const candidates = [...document.querySelectorAll('a, button, span, div')]"
                        + "      .filter(el => el.children.length === 0 && el.textContent && el.textContent.trim() === '继续沟通');"
                        + "  for (const c of candidates) {"
                        + "    let p = c.parentElement;"
                        + "    for (let j = 0; j < 8 && p; j++) {"
                        + "      if (p.textContent && (p.textContent.includes('已向BOSS') || p.textContent.includes('留在此页') || p.textContent.includes('发送消息'))) {"
                        + "        c.click();"
                        + "        return 'clicked_modal_btn';"
                        + "      }"
                        + "      p = p.parentElement;"
                        + "    }"
                        + "  }"
                        + "  if (candidates.length > 0) {"
                        + "    candidates[candidates.length - 1].click();"
                        + "    return 'clicked_last_candidate';"
                        + "  }"
                        + "  const hasDialog = [...document.querySelectorAll('div, p, span')].some(e => e.textContent && e.textContent.includes('已向BOSS发送消息'));"
                        + "  return hasDialog ? 'has_dialog_no_btn' : 'no_dialog';"
                        + "}");
                String clickResult = String.valueOf(clicked);
                if (clickResult.contains("clicked")) {
                    log.info("检测到'已向BOSS发送消息'弹窗，已点击弹窗内【继续沟通】按钮 (结果: {})", clickResult);
                    autoGreeted = true;
                    PlaywrightUtil.sleep(2);
                } else if ("has_dialog_no_btn".equals(clickResult)) {
                    log.info("检测到'已向BOSS发送消息'弹窗，但未直接匹配到'继续沟通'按钮，尝试直接查找并点击按钮");
                    Locator directBtn = detailPage.locator("a:has-text('继续沟通'), button:has-text('继续沟通'), div:has-text('继续沟通')");
                    if (directBtn.count() > 0) {
                        try { directBtn.last().click(new Locator.ClickOptions().setForce(true)); } catch (Exception ignore) {}
                    }
                    autoGreeted = true;
                }
            } catch (Exception ignore) {
            }

            // 检查当前页面及所有可能打开的聊天页
            Locator candInput = detailPage.locator("div#chat-input.chat-input[contenteditable='true'], div.chat-input[contenteditable='true'], div#chat-input, div[contenteditable='true'], textarea.input-area, textarea");
            if (candInput.count() > 0 && candInput.first().isVisible()) {
                inputLocator = candInput;
                chatPage = detailPage;
                inputReady = true;
                break;
            }

            // Boss 可能把聊天页在新的标签页打开：检查上下文里所有页面
            for (Page candidate : page.context().pages()) {
                if (candidate == detailPage) continue;
                try {
                    String candUrl = candidate.url();
                    if (candUrl != null && candUrl.contains("geek/chat")) {
                        chatPage = candidate;
                    }
                    Locator cInput = candidate.locator("div#chat-input.chat-input[contenteditable='true'], div.chat-input[contenteditable='true'], div#chat-input, div[contenteditable='true'], textarea.input-area, textarea");
                    if (cInput.count() > 0 && cInput.first().isVisible()) {
                        chatPage = candidate;
                        inputLocator = cInput;
                        inputReady = true;
                        break;
                    }
                } catch (Exception ignore) {
                }
            }
            if (inputReady) break;
            PlaywrightUtil.sleep(1);
        }

        boolean sendSuccess = false;

        if (inputReady) {
            // 5. 输入打招呼语
            Locator input = inputLocator.first();
            input.click();
            Object tagObj = input.evaluate("el => el.tagName.toLowerCase()");
            if (tagObj instanceof String && ((String) tagObj).equals("textarea")) {
                input.fill(message);
            } else {
                // 对 contenteditable 节点写入文本并派发完整的输入与状态变更事件
                input.evaluate("(el, msg) => {"
                        + "  el.focus();"
                        + "  el.innerText = msg;"
                        + "  el.dispatchEvent(new Event('input', { bubbles: true }));"
                        + "  el.dispatchEvent(new Event('change', { bubbles: true }));"
                        + "  el.dispatchEvent(new CompositionEvent('compositionend', { bubbles: true, data: msg }));"
                        + "}", message);
                PlaywrightUtil.sleep(1);
            }

                        // 6. 强化版发送消息逻辑：按回车 -> 按Ctrl+Enter -> 找“发送”按钮点击
            try {
                input.press("Enter");
                PlaywrightUtil.sleep(1);
                
                // 检查输入框内容是否还在（长度>5说明没发出去）
                String val = (String) input.evaluate("el => el.value || el.innerText || ''");
                if (val != null && val.trim().length() > 5) {
                    log.info("回车键未能发送(输入框未清空)，尝试使用 Control+Enter...");
                    input.press("Control+Enter");
                    input.press("Meta+Enter");
                    PlaywrightUtil.sleep(1);
                }
                
                val = (String) input.evaluate("el => el.value || el.innerText || ''");
                if (val != null && val.trim().length() > 5) {
                    log.info("快捷键未能发送，尝试强制点击【发送】按钮...");
                    try {
                        Locator sendBtn = chatPage.locator("button:has-text('发送'), div.btn-send, div.send-message, span:has-text('发送')");
                        if (sendBtn.count() > 0) {
                            sendBtn.last().click(new Locator.ClickOptions().setForce(true).setTimeout(3000));
                        } else {
                            chatPage.evaluate("() => { const b = Array.from(document.querySelectorAll('button, div, span')).find(e => e.textContent && e.textContent.trim() === '发送'); if(b) b.click(); }");
                        }
                    } catch (Exception btnEx) {
                        log.warn("点击发送按钮异常: {}", btnEx.getMessage());
                    }
                    PlaywrightUtil.sleep(1);
                }
                
                // 尝试关闭可能存在的弹窗
                try {
                    chatPage.locator("i.icon-close").first().click(new Locator.ClickOptions().setTimeout(1000));
                } catch (Exception ignore) {}

                sendSuccess = true;
                log.info("完成打招呼语发送流");
            } catch (Exception ex) {
                log.warn("发送消息过程发生异常: {}", ex.getMessage());
                sendSuccess = true;
            }

            // 7. 发送图片简历（可选）
            boolean imgResume = false;
            if (Boolean.TRUE.equals(config.getSendImgResume())) {
                imgResume = sendImageResume(chatPage);
            }

            log.info("投递完成 | 公司：{} | 岗位：{} | 薪资：{} | 招呼语：{} | 图片简历：{}",
                    job.getCompanyName(), job.getJobName(), job.getSalary(), message, imgResume ? "已发送" : "未发送");
        } else if (autoGreeted) {
            // 虽然未打开完整输入框，但 Boss 已成功向 HR 发起初次打招呼
            log.info("Boss已向HR发起初次沟通（系统招呼语已送达） | 公司：{} | 岗位：{}", job.getCompanyName(), job.getJobName());
            sendSuccess = true;
        } else {
            log.warn("聊天输入框未出现，跳过: {}", job.getJobName());
            try {
                java.nio.file.Path debugDir = java.nio.file.Paths.get("./target/logs/debug");
                java.nio.file.Files.createDirectories(debugDir);
                String ts = java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
                String shotPath = debugDir.resolve("chat-input-missing-" + ts + ".png").toString();
                detailPage.screenshot(new Page.ScreenshotOptions().setPath(java.nio.file.Paths.get(shotPath)).setFullPage(true));
            } catch (Exception ignore) {
            }
        }

        // 连续失败计数：连续多次打不开聊天框，疑似达到今日上限或被风控，自动结束本平台
        if (sendSuccess) {
            consecutiveFailures = 0;
        } else {
            consecutiveFailures++;
            try {
                Object hitLimit = detailPage.evaluate("() => { const t = document.body ? document.body.innerText : ''; return t.includes('上限') && (t.includes('今日') || t.includes('今天')); }");
                if (Boolean.TRUE.equals(hitLimit)) {
                    log.warn("检测到Boss今日投递上限提示，触发自动结束");
                    limitReached = true;
                }
            } catch (Exception ignore) {
            }
            if (consecutiveFailures >= 8) {
                log.warn("连续{}个岗位投递失败，疑似达到上限或页面异常，自动结束Boss投递", consecutiveFailures);
                limitReached = true;
            }
        }

        // 9. 关闭打开的详情页/聊天页
        try {
            chatPage.close();
        } catch (Exception ignore) {
        }
        if (chatPage != detailPage) {
            try {
                detailPage.close();
            } catch (Exception ignore) {
            }
        }
        PlaywrightUtil.sleep(1);

        // 10. 更新数据库投递状态 & 成功投递加入结果
        if (sendSuccess) {
            // 从详情链接提取 encrypt_id，并映射到 encrypt_user_id
            String encryptId = extractEncryptId(detailUrl);
            String encryptUserId = encryptId != null ? encryptIdToUserId.get(encryptId) : null;
            if (encryptId != null && encryptUserId != null) {
                try {
        bossService.updateDeliveryStatus(encryptId, encryptUserId, "已投递");
                    log.info("投递成功 | 公司：{} | 岗位：{} | encryptId：{} | encryptUserId：{}", job.getCompanyName(), job.getJobName(), encryptId, encryptUserId);
                    if (applicationRecordService != null) {
                        try {
                            var pageRecords = applicationRecordService.listApplications(null, "boss", job.getJobName(), 1, 1);
                            if (pageRecords != null && !pageRecords.getRecords().isEmpty()) {
                                applicationRecordService.markAsApplied(pageRecords.getRecords().get(0).getId(), message);
                            }
                        } catch (Throwable ignore) {}
                    }
                } catch (Exception e) {
                    log.warn("更新投递状态为已投递失败：{}", e.getMessage());
                }
            } else {
                log.debug("未能找到 encryptId/encryptUserId 用于更新投递状态，detailUrl: {}", detailUrl);
            }
            resultList.add(job);
        } else {
            // 若发生发送失败，也进行状态更新
            String encryptId = extractEncryptId(detailUrl);
            String encryptUserId = encryptId != null ? encryptIdToUserId.get(encryptId) : null;
            if (encryptId != null && encryptUserId != null) {
                try {
        bossService.updateDeliveryStatus(encryptId, encryptUserId, "投递失败");
                    log.warn("投递失败 | 公司：{} | 岗位：{} | encryptId：{} | encryptUserId：{}", job.getCompanyName(), job.getJobName(), encryptId, encryptUserId);
                } catch (Exception e) {
                    log.warn("更新投递状态为投递失败异常：{}", e.getMessage());
                }
            }
        }
    }

    /**
     * 注册页面响应监听：拦截 /wapi/zpgeek/job/detail.json 请求并解析写库
     */
    /**
     * 注册页面响应监听：拦截 /wapi/zpgeek/job/detail.json 请求并解析写库
     */
    private void attachJobDetailResponseListener() {
        if (page == null) return;
        page.onResponse(resp -> {
            try {
                String url = resp.url();
                if (url == null) return;
                // 仅处理 Boss 岗位详情接口（GET）
                if (url.contains("/wapi/zpgeek/job/detail.json") &&
                        "GET".equalsIgnoreCase(resp.request().method())) {
                    String body = null;
                    try {
                        body = resp.text();
                    } catch (Throwable ignore) {
                        // 某些情况下可能拿不到文本，忽略
                    }
                    if (body == null || body.isEmpty()) return;

                    // 保存原始 JSON 到 target/job.txt
                    appendRawJson(body);

                    // 仅记录映射与原始 JSON；入库逻辑已移动到点击卡片时
                    JSONObject root = new JSONObject(body);
                    JSONObject zpData = root.optJSONObject("zpData");
                    if (zpData == null) return;
                    JSONObject jobInfo = zpData.optJSONObject("jobInfo");
                    if (jobInfo == null) return;
                    String encryptId = jobInfo.optString("encryptId", null);
                    String encryptUserId = jobInfo.optString("encryptUserId", null);
                    if (encryptId != null && encryptUserId != null) {
                        encryptIdToUserId.put(encryptId, encryptUserId);
                    }
                }
            } catch (Throwable e) {
                log.debug("监听岗位详情响应处理异常：{}", e.getMessage());
            }
        });
    }


    /**
     * 追加保存原始 JSON 到 target/job.txt
     */
    private void appendRawJson(String body) {
        try {
            java.io.File dir = new java.io.File("target");
            if (!dir.exists()) dir.mkdirs();
            java.io.File file = new java.io.File(dir, "job.txt");
            try (java.io.FileWriter fw = new java.io.FileWriter(file, true)) {
                fw.write(body);
                fw.write(System.lineSeparator());
                fw.write("\n");
            }
        } catch (Exception e) {
            log.debug("写入 target/job.txt 失败：{}", e.getMessage());
        }
    }

    /**
     * 从详情页 URL 中提取 encrypt_id
     */
    private String extractEncryptId(String detailUrl) {
        try {
            if (detailUrl == null) return null;
            String key = "/job_detail/";
            int idx = detailUrl.indexOf(key);
            if (idx < 0) return null;
            int start = idx + key.length();
            int end = detailUrl.indexOf(".html", start);
            if (end < 0) end = detailUrl.length();
            return detailUrl.substring(start, end);
        } catch (Exception e) {
            return null;
        }
    }

    public boolean isValidString(String str) {
        return str != null && !str.isEmpty();
    }

    private boolean sendImageResume(Page page) {
        try {
            // 0) 资源存在性校验，避免后续无效操作
            URL resourceUrlCheck = Boss.class.getResource("/resume.jpg");
            if (resourceUrlCheck == null) {
                log.error("资源文件 resume.jpg 不存在，跳过发送图片简历");
                return false;
            }

            // 进入聊天页
            if (!page.url().contains("/web/geek/chat")) {
                Locator chatBtn = page.locator("a.btn-startchat, a.op-btn-chat");
                if (chatBtn.count() == 0) {
                    log.warn("未找到【继续沟通/立即沟通】按钮，跳过发送图片简历");
                    return false;
                }
                chatBtn.first().click();
                page.waitForURL("**/web/geek/chat**", new Page.WaitForURLOptions().setTimeout(15_000));
            }

            // 1) 解析图片路径（在可能触发文件选择器前就准备好）
            java.nio.file.Path imagePath = resolveResumeImage();

            // 2) 定位图片上传入口：聊天工具栏的"发送图片"按钮及其内部的 file input
            //    注意：file input 通常是 display:none 的，只能等 attached，不能等 visible
            Locator imgBtn = page.locator("div.btn-sendimg, [class*='btn-sendimg']");
            Locator imageInput = page.locator("input[type='file'][accept*='image']").first();

            if (imageInput.count() == 0) {
                // 3) input 未渲染时点图片按钮触发；优先拦截文件选择器，避免弹系统窗口卡死
                if (imgBtn.count() > 0) {
                    try {
                        com.microsoft.playwright.FileChooser chooser = page.waitForFileChooser(() -> {
                            imgBtn.first().click();
                        });
                        chooser.setFiles(imagePath);
                        log.info("已通过文件选择器提交图片简历");
                        PlaywrightUtil.sleep(2);
                        clickChatSendIfPresent(page);
                        return true;
                    } catch (com.microsoft.playwright.PlaywrightException ignore) {
                        // 未弹出系统文件选择器，继续常规流程
                    }
                    PlaywrightUtil.sleep(1);
                    imageInput = page.locator("input[type='file'][accept*='image']").first();
                }
            }

            // 4) 兜底：accept 属性不含 image 字样的也接受
            if (imageInput.count() == 0) {
                imageInput = page.locator("input[type='file']").first();
            }

            if (imageInput.count() == 0) {
                log.warn("未找到图片上传入口 | 发送图片按钮数量: {} | 当前URL: {} | 工具栏HTML: {}",
                        imgBtn.count(), page.url(),
                        page.evaluate("() => { const b = document.querySelector(\"div[class*='chat-input'], .chat-footer, [class*='toolbar']\"); return b ? b.outerHTML.slice(0, 500) : '(未找到聊天工具栏容器)'; }"));
                return false;
            }

            // 上传图片（attached 即可，不要求可见）
            imageInput.setInputFiles(imagePath);
            PlaywrightUtil.sleep(2);
            clickChatSendIfPresent(page);
            return true;
        } catch (Throwable e) {
            log.error("发送图片简历失败：{}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * 图片上传后 Boss 可能把图片放进输入框等待确认发送，点一下发送按钮确保发出
     */
    private void clickChatSendIfPresent(Page page) {
        try {
            Locator send = page.locator("div.send-message, button[type='send'].btn-send, button.btn-send");
            if (send.count() > 0 && send.first().isVisible()) {
                send.first().click();
                PlaywrightUtil.sleep(1);
            }
        } catch (Exception ignore) {
        }
    }

    private java.nio.file.Path resolveResumeImage() throws Exception {
        URL resourceUrl = Boss.class.getResource("/resume.jpg");
        if (resourceUrl == null) {
            throw new IllegalStateException("资源文件 /resume.jpg 未找到，请将图片放置到 src/main/resources 目录下");
        }
        if ("file".equalsIgnoreCase(resourceUrl.getProtocol())) {
            return java.nio.file.Paths.get(resourceUrl.toURI());
        }
        java.nio.file.Path temp = java.nio.file.Files.createTempFile("resume-", ".jpg");
        try (java.io.InputStream in = Boss.class.getResourceAsStream("/resume.jpg")) {
            if (in == null) {
                throw new IllegalStateException("无法从类路径读取 /resume.jpg 资源");
            }
            java.nio.file.Files.copy(in, temp, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }
        return temp;
    }

    /**
     * 检查岗位薪资是否符合预期
     *
     * @return boolean
     * true 不符合预期
     * false 符合预期
     * 期望的最低薪资如果比岗位最高薪资还小，则不符合（薪资给的太少）
     * 期望的最高薪资如果比岗位最低薪资还小，则不符合(要求太高满足不了)
     */
    private boolean isSalaryNotExpected(String salary) {
        try {
            // 1. 如果没有期望薪资范围，直接返回 false，表示"薪资并非不符合预期"
            List<Integer> expectedSalary = config.getExpectedSalary();
            if (!hasExpectedSalary(expectedSalary)) {
                return false;
            }

            // 2. 清理薪资文本（比如去掉 "·15薪"）
            salary = removeYearBonusText(salary);

            // 3. 如果薪资格式不符合预期（如缺少 "K" / "k"），直接返回 true，表示"薪资不符合预期"
            if (!isSalaryInExpectedFormat(salary)) {
                return true;
            }

            // 4. 进一步清理薪资文本，比如去除 "K"、"k"、"·" 等
            salary = cleanSalaryText(salary);

            // 5. 判断是 "月薪" 还是 "日薪"
            String jobType = detectJobType(salary);
            salary = removeDayUnitIfNeeded(salary); // 如果是按天，则去除 "元/天"

            // 6. 解析薪资范围并检查是否超出预期
            Integer[] jobSalaryRange = parseSalaryRange(salary);
            return isSalaryOutOfRange(jobSalaryRange,
                    getMinimumSalary(expectedSalary),
                    getMaximumSalary(expectedSalary),
                    jobType);

        } catch (Exception e) {
            log.error("岗位薪资获取异常！薪资文本【{}】,异常信息【{}】", salary, e.getMessage(), e);
            // 出错时，您可根据业务需求决定返回 true 或 false
            // 这里假设出错时无法判断，视为不满足预期 => 返回 true
            return true;
        }
    }

    /**
     * 是否存在有效的期望薪资范围
     */
    private boolean hasExpectedSalary(List<Integer> expectedSalary) {
        return expectedSalary != null && !expectedSalary.isEmpty();
    }

    /**
     * 去掉年终奖信息，如 "·15薪"、"·13薪"。
     */
    private String removeYearBonusText(String salary) {
        if (salary.contains("薪")) {
            // 使用正则去除 "·任意数字薪"
            return salary.replaceAll("·\\d+薪", "");
        }
        return salary;
    }

    /**
     * 判断是否是按天计薪，如发现 "元/天" 则认为是日薪
     */
    private String detectJobType(String salary) {
        if (salary.contains("元/天")) {
            return "day";
        }
        return "mouth";
    }

    /**
     * 如果是日薪，则去除 "元/天"
     */
    private String removeDayUnitIfNeeded(String salary) {
        if (salary.contains("元/天")) {
            return salary.replaceAll("元/天", "");
        }
        return salary;
    }

    private Integer getMinimumSalary(List<Integer> expectedSalary) {
        return expectedSalary != null && !expectedSalary.isEmpty() ? expectedSalary.get(0) : null;
    }

    private Integer getMaximumSalary(List<Integer> expectedSalary) {
        return expectedSalary != null && expectedSalary.size() > 1 ? expectedSalary.get(1) : null;
    }

    private boolean isSalaryInExpectedFormat(String salaryText) {
        return salaryText.contains("K") || salaryText.contains("k") || salaryText.contains("元/天");
    }

    private String cleanSalaryText(String salaryText) {
        salaryText = salaryText.replace("K", "").replace("k", "");
        int dotIndex = salaryText.indexOf('·');
        if (dotIndex != -1) {
            salaryText = salaryText.substring(0, dotIndex);
        }
        return salaryText;
    }

    private boolean isSalaryOutOfRange(Integer[] jobSalary, Integer miniSalary, Integer maxSalary,
                                       String jobType) {
        if (jobSalary == null) {
            return true;
        }
        if (miniSalary == null) {
            return false;
        }
        if (Objects.equals("day", jobType)) {
            // 期望薪资转为平均每日的工资
            maxSalary = BigDecimal.valueOf(maxSalary).multiply(BigDecimal.valueOf(1000))
                    .divide(BigDecimal.valueOf(21.75), 0, RoundingMode.HALF_UP).intValue();
            miniSalary = BigDecimal.valueOf(miniSalary).multiply(BigDecimal.valueOf(1000))
                    .divide(BigDecimal.valueOf(21.75), 0, RoundingMode.HALF_UP).intValue();
        }
        // 如果职位薪资下限低于期望的最低薪资，返回不符合
        if (jobSalary[1] < miniSalary) {
            return true;
        }
        // 如果职位薪资上限高于期望的最高薪资，返回不符合
        return maxSalary != null && jobSalary[0] > maxSalary;
    }

    public boolean containsDeadStatus(String activeTimeText, List<String> deadStatus) {
        for (String status : deadStatus) {
            if (activeTimeText.contains(status)) {
                return true;// 一旦找到包含的值，立即返回 true
            }
        }
        return false;// 如果没有找到，返回 false
    }

    private String generateAiMessage(String keyword, String jobName, String companyName, String jd) {
        AiEntity aiConfig = aiService.getAiConfig();
        String introduce = (aiConfig != null && aiConfig.getIntroduce() != null) ? aiConfig.getIntroduce() : "";
        String prompt = (aiConfig != null) ? aiConfig.getPrompt() : null;

        String requestMessage;
        if (prompt != null && !prompt.isBlank()) {
            if (prompt.contains("{introduce}") || prompt.contains("{jobName}") || prompt.contains("{jd}")) {
                // 优先支持命名占位符，支持用户在前端自由编写模板
                requestMessage = prompt
                        .replace("{introduce}", introduce != null ? introduce : "")
                        .replace("{keyword}", keyword != null ? keyword : "")
                        .replace("{jobName}", jobName != null ? jobName : "")
                        .replace("{companyName}", companyName != null ? companyName : "")
                        .replace("{jd}", jd != null && !jd.isBlank() ? jd : "无明确描述")
                        .replace("{sayHi}", config.getSayHi() != null ? config.getSayHi() : "");
            } else {
                // 兼容 %s 占位符：根据出现的 %s 数量安全截取参数，防止参数越界或参数不足异常
                int count = 0;
                int idx = 0;
                while ((idx = prompt.indexOf("%s", idx)) != -1) {
                    count++;
                    idx += 2;
                }
                Object[] allArgs = new Object[]{introduce, keyword, jobName, companyName != null ? companyName : "", jd != null ? jd : "", config.getSayHi()};
                Object[] safeArgs = java.util.Arrays.copyOf(allArgs, Math.min(count, allArgs.length));
                try {
                    requestMessage = String.format(prompt, safeArgs);
                } catch (Exception fmtEx) {
                    log.warn("Prompt 格式化异常，降级为默认提示词: {}", fmtEx.getMessage());
                    requestMessage = buildDefaultPrompt(introduce, keyword, jobName, companyName, jd);
                }
            }
        } else {
            requestMessage = buildDefaultPrompt(introduce, keyword, jobName, companyName, jd);
        }

        // 强制风格多样化：追加油最近已发话术，禁止重复套路，避免每条都长一个样
        requestMessage = requestMessage + "\n【风格要求（必须遵守）】\n" +
                "1. 每条打招呼语的结构、开头、句式必须与之前发出的明显不同，禁止套用固定模板；\n" +
                "2. 禁止总是以「您好，我是…」开头：可以从岗位/公司亮点切入、从JD里的具体要求切入、用一个展示理解力的问题切入等，随机选择一种；\n" +
                "3. 「可尽快到岗」「作品集已备好」这类话每次最多出现一个，不要条条都带上；\n" +
                "4. 灵活使用口语化的真实语气，长短可以在45到90字之间浮动；\n" +
                "5. 直接输出招呼语正文本身，禁止输出任何分析、说明、引号或前后缀。\n";
        if (!recentGreetings.isEmpty()) {
            requestMessage += "【最近已经发出过的话术（开头与句式必须避开，禁止雷同）】\n";
            int shown = 0;
            for (String g : recentGreetings) {
                requestMessage += "- " + g + "\n";
                if (++shown >= 8) break;
            }
        }

        try {
            String result = aiService.sendRequest(requestMessage);
            if (result == null || result.isBlank()) {
                return config.getSayHi();
            }
            if (result.toLowerCase().contains("false")) {
                return config.getSayHi();
            }
            result = sanitizeAiGreeting(result);
            // 质量校验：招呼语必须是第一人称对HR说的话，像分析报告的一律丢弃
            boolean looksLikeGreeting = result != null && result.contains("我")
                    && !result.contains("求职者") && !result.contains("招聘关键词")
                    && !result.contains("差异化") && result.length() >= 15 && result.length() <= 250;
            if (!looksLikeGreeting) {
                log.warn("AI输出不像招呼语（疑似分析过程），使用默认话术 | 原始输出: {}", result);
                return config.getSayHi();
            }
            log.info("AI成功生成打招呼语 | 岗位: {} | 公司: {} | 内容: {}", jobName, companyName, result);
            // 记录最近话术供后续去重
            recentGreetings.addFirst(result.trim());
            while (recentGreetings.size() > 8) {
                recentGreetings.removeLast();
            }
            return result;
        } catch (Exception e) {
            log.warn("AI请求失败，使用原有打招呼语: {}", e.getMessage());
            return config.getSayHi();
        }
    }

    /**
     * JD匹配评分（基础100分，规则由用户在数据库 SCORE_RULES 中自定义，未配置时用通用默认规则）。
     * 返回值 < 阈值(默认80) 视为低分岗位不投递；返回 -100 表示硬排除。
     */
    private int scoreJob(String jobName, String degree, String experience, String industry, String jd) {
        com.getjobs.application.service.ScoreRulesService.Rules rules =
                scoreRules != null ? scoreRules : scoreRulesService.loadRules();
        String deg = safe(degree);
        String exp = safe(experience);
        String ind = safe(industry);
        String name = safe(jobName);
        String desc = safe(jd);

        // 硬排除：学历/岗位名命中直接判负
        for (String reject : rules.degreeReject) {
            if (deg.contains(reject)) return -100;
        }
        for (String reject : rules.titleReject) {
            if (name.contains(reject)) return -100;
        }

        int score = 100;
        score += firstMatch(rules.degree, deg);        // 学历：首个命中
        score += firstMatch(rules.experience, exp);    // 经验：首个命中
        score += firstMatch(rules.industry, ind);      // 行业：首个命中
        score += firstMatch(rules.jobBoost, name);     // 岗位名加分：首个命中
        score += sumAll(rules.jobPenalty, name);       // 岗位名减分：全部累加
        score += sumAll(rules.jdBoost, desc);          // JD加分：全部累加
        score += sumAll(rules.jdPenalty, desc);        // JD减分：全部累加
        return score;
    }

    private int firstMatch(java.util.List<com.getjobs.application.service.ScoreRulesService.Rule> ruleList, String text) {
        for (com.getjobs.application.service.ScoreRulesService.Rule r : ruleList) {
            if (text.contains(r.match)) return r.score;
        }
        return 0;
    }

    private int sumAll(java.util.List<com.getjobs.application.service.ScoreRulesService.Rule> ruleList, String text) {
        int total = 0;
        for (com.getjobs.application.service.ScoreRulesService.Rule r : ruleList) {
            if (text.contains(r.match)) total += r.score;
        }
        return total;
    }

    private String safe(String s) {
        return s == null ? "" : s;
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

    private String buildDefaultPrompt(String introduce, String keyword, String jobName, String companyName, String jd) {
        return "你是一名求职顾问。请根据求职者的真实个人背景和应聘岗位信息，为求职者向该岗位招聘HR写一段定制化、真诚且专业的求职打招呼语。\n" +
                "【求职者背景】\n" + introduce + "\n" +
                "【应聘岗位信息】\n" +
                "- 关键词：" + keyword + "\n" +
                "- 岗位名称：" + jobName + "\n" +
                "- 公司名称：" + (companyName != null ? companyName : "") + "\n" +
                "- 岗位要求/JD：\n" + (jd != null && !jd.isBlank() ? jd : "无") + "\n" +
                "【生成要求】\n" +
                "1. 根据岗位JD自由发挥，突出求职者背景中与该岗位最契合的技能与经历（背景里没有的不要编造）；\n" +
                "2. 如背景中提及到岗时间、作品集、项目成果等加分项，可自然地带上一项，不要条条都带；\n" +
                "3. 字数严格控制在 60 到 90 字之间，只输出打招呼语正文，严禁分析过程和多余废话。";
    }

    private Integer[] parseSalaryRange(String salaryText) {
        try {
            return Arrays.stream(salaryText.split("-")).map(s -> s.replaceAll("[^0-9]", "")) // 去除非数字字符
                    .map(Integer::parseInt) // 转换为Integer
                    .toArray(Integer[]::new); // 转换为Integer数组
        } catch (Exception e) {
            log.error("薪资解析异常！{}", e.getMessage(), e);
        }
        return null;
    }

    /** wait_time 没配或配得不合法时用的秒数 */
    private static final int DEFAULT_WAIT_TIME_SECONDS = 10;

    /**
     * 取配置里的 wait_time（秒），非法值一律退回默认值。
     */
    private int resolveWaitTimeSeconds() {
        try {
            String raw = config == null ? null : config.getWaitTime();
            if (raw != null && !raw.isBlank()) {
                int parsed = Integer.parseInt(raw.trim());
                if (parsed > 0) {
                    return parsed;
                }
            }
        } catch (NumberFormatException ignored) {
            // 配置里塞了非数字，用默认值
        }
        return DEFAULT_WAIT_TIME_SECONDS;
    }

    /**
     * 每处理完一个岗位后的停顿。
     * <p>
     * 上一轮实测 7 分钟连刷 297 个岗位详情，直接把 Boss 风控触发了，
     * 后续关键词全部被弹到安全验证页。这里按 wait_time 降速：
     * 正常模式在 [wait_time/2, wait_time] 之间随机，避免固定节奏本身成为特征；
     * 调试模式固定用最大值 wait_time，方便观察。
     */
    private void pauseBetweenJobs() {
        int waitTime = resolveWaitTimeSeconds();
        int seconds;
        if (Boolean.TRUE.equals(config.getDebugger())) {
            seconds = waitTime;
        } else {
            int min = Math.max(1, waitTime / 2);
            seconds = min >= waitTime ? waitTime
                    : ThreadLocalRandom.current().nextInt(min, waitTime + 1);
        }
        log.debug("岗位间停顿 {} 秒（wait_time={}，debugger={}）", seconds, waitTime, config.getDebugger());
        PlaywrightUtil.sleep(seconds);
    }

    /**
     * 判断是不是 Boss 的安全验证页。
     * <p>
     * 实测密集遍历后会被弹到 /web/passport/zp/verify.html（极验滑块，页面上是
     * div.geetest_success_correct 那一套），而原来的判断只认 verify-slider，
     * 导致真正遇到验证时程序把关键词当失败跳过，人也不知道要去过验证。
     */
    private static boolean isSecurityVerifyUrl(String url) {
        if (url == null) {
            return false;
        }
        return url.contains("/web/user/safe/verify-slider")
                || url.contains("/web/passport/zp/verify")
                || url.contains("/web/passport/zp/security")
                || url.contains("security-check");
    }

    private void waitForSliderVerify(Page page) {
        // 最多等待5分钟（防呆，防止死循环）
        long start = System.currentTimeMillis();
        while (true) {
            String url = page.url();
            if (isSecurityVerifyUrl(url)) {
                progressCallback.accept("请手动完成Boss直聘滑块验证，通过后在控制台回车继续...", 0, 0);
                System.out.println("\n【滑块验证】请手动完成Boss直聘滑块验证，通过后在控制台回车继续…");
                try {
                    System.in.read();
                } catch (Exception e) {
                    log.error("等待滑块验证输入异常: {}", e.getMessage());
                }
                PlaywrightUtil.sleep(1);
                // 验证通过后页面url会变，循环再检测一次
                continue;
            }
            if ((System.currentTimeMillis() - start) > 5 * 60 * 1000) {
                throw new RuntimeException("滑块验证超时！");
            }
            break;
        }
    }


    private boolean isLoginRequired() {
        try {
            Locator buttonLocator = page.locator(LOGIN_BTNS);
            if (buttonLocator.count() > 0 && buttonLocator.textContent().contains("登录")) {
                return true;
            }
        } catch (Exception e) {
            try {
                page.locator(PAGE_HEADER).waitFor();
                Locator errorLoginLocator = page.locator(ERROR_PAGE_LOGIN);
                if (errorLoginLocator.count() > 0) {
                    errorLoginLocator.click();
                }
                return true;
            } catch (Exception ex) {
                log.info("没有出现403访问异常");
            }
            log.info("cookie有效，已登录...");
            return false;
        }
        return false;
    }

}
