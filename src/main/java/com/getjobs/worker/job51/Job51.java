package com.getjobs.worker.job51;

import com.getjobs.application.service.Job51Service;
import com.getjobs.worker.utils.JobUtils;
import com.getjobs.worker.utils.PlaywrightUtil;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Route;
import com.microsoft.playwright.options.WaitForSelectorState;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * @author loks666
 * 项目链接: <a href="https://github.com/loks666/get_jobs">https://github.com/loks666/get_jobs</a>
 * 前程无忧自动投递简历 - Playwright版本
 */
@Slf4j
@Component
@Scope("prototype")
@RequiredArgsConstructor
public class Job51 {

    // 显式setter，避免对 Lombok 的依赖导致编译问题
    @Setter
    private Page page;

    @Setter
    private Job51Config config;

    @Setter
    private ProgressCallback progressCallback;

    @Setter
    private Supplier<Boolean> shouldStopCallback;

    private final List<String> resultList = new ArrayList<>();
    private final Job51Service job51Service;
    private boolean networkHooked = false;
    private boolean reachedDailyLimit = false;
    private final java.util.Set<String> processedRequestIds = new java.util.HashSet<>();
    @Getter
    private int currentPageNum = 0;
    // 当前页从JSON拦截到的jobId列表
    private final java.util.List<Long> currentPageJobIds = new java.util.ArrayList<>();
    private String currentSearchUrl = "";

    private static final int DEFAULT_MAX_PAGE = 50;
    private static final String BASE_URL = "https://we.51job.com/pc/search?";

    /**
     * 进度回调接口
     */
    @FunctionalInterface
    public interface ProgressCallback {
        void accept(String message, Integer current, Integer total);
    }

    /**
     * 准备工作：加载配置、初始化数据
     */
    public void prepare() {
        resultList.clear();
    }

    /**
     * 执行投递任务
     * @return 投递数量
     */
    public int execute() {
        long startTime = System.currentTimeMillis();

        try {
            // 检查配置是否有效
            if (config == null) {
                log.error("[51job] 配置为空，无法执行投递任务");
                sendProgress("配置为空，无法执行投递任务", null, null);
                return 0;
            }
            
            if (config.getKeywords() == null || config.getKeywords().isEmpty()) {
                log.warn("[51job] 关键词列表为空，无法执行投递任务");
                sendProgress("关键词列表为空，请先配置搜索关键词", null, null);
                return 0;
            }
            
            // 遍历所有关键词进行投递
            for (String keyword : config.getKeywords()) {
                if (shouldStop()) {
                    sendProgress("用户取消投递", null, null);
                    break;
                }

                String searchUrl = buildSearchUrl(keyword);
                deliverByKeyword(keyword, searchUrl);
            }

            long duration = System.currentTimeMillis() - startTime;
            String message = String.format("51job投递完成，共投递%d个简历，用时%s",
                resultList.size(), formatDuration(duration));
            sendProgress(message, null, null);

        } catch (Exception e) {
            log.error("51job投递过程出现异常", e);
            sendProgress("投递出现异常: " + e.getMessage(), null, null);
        }

        return resultList.size();
    }

    /**
     * 按关键词投递
     */
    private void deliverByKeyword(String keyword, String searchUrl) {
        try {
            // 收敛日志：不输出关键词级日志，仅保留页级摘要

            // 在跳转前监听 51job 搜索接口，抓取 JSON 并保存到数据库 + 打印诊断日志
            if (!networkHooked) {
                try {
                    page.onResponse(r -> {
                        try {
                            String url = r.url();
                            if (url != null && url.contains("/api/job/search-pc") && "GET".equalsIgnoreCase(r.request().method())) {
                                int status = 0;
                                try { status = r.status(); } catch (Throwable ignored) {}
                                String text = null;
                                try { text = r.text(); } catch (Throwable ignored) {}
                                int len = text == null ? 0 : text.length();
                                // 基于 URL 的 requestId 做去重，避免重复解析
                                String requestId = null;
                                try {
                                    java.net.URI u = new java.net.URI(url);
                                    String q = u.getQuery();
                                    if (q != null) {
                                        for (String part : q.split("&")) {
                                            int i = part.indexOf('=');
                                            if (i > 0 && "requestId".equals(part.substring(0, i))) {
                                                requestId = java.net.URLDecoder.decode(part.substring(i + 1), java.nio.charset.StandardCharsets.UTF_8);
                                                break;
                                            }
                                        }
                                    }
                                } catch (Exception ignored) {}
                                if (requestId != null && !requestId.isBlank() && processedRequestIds.contains(requestId)) {
                                    return;
                                }
                                if (text != null) {
                                    // 根据 Content-Type 粗判是否为 JSON
                                    boolean isJson = false;
                                    try {
                                        java.util.Map<String, String> headers = r.headers();
                                        if (headers != null) {
                                            String ct = headers.getOrDefault("content-type", headers.get("Content-Type"));
                                            if (ct != null && ct.toLowerCase().contains("json")) isJson = true;
                                        }
                                    } catch (Throwable ignored) {}
                                    if (isJson) {
                                        // 解析并保存到数据库
                                        job51Service.parseAndPersistJob51SearchJson(text);
                                        // 📋 提取当前页的jobId列表并缓存
                                        List<Long> jobIds = extractJobIdsFromJson(text);
                                        if (jobIds != null && !jobIds.isEmpty()) {
                                            synchronized (currentPageJobIds) {
                                                currentPageJobIds.clear();
                                                currentPageJobIds.addAll(jobIds);
                                            }
                                        }
                                        if (requestId != null && !requestId.isBlank()) processedRequestIds.add(requestId);
                                    } // 非JSON静默跳过
                                }
                            }
                        } catch (Throwable e) {
                            // 静默错误
                        }
                    });
                    networkHooked = true;
                } catch (Throwable e) {
                    // 静默错误
                }
            }

            // 导航到搜索页面
            this.currentSearchUrl = searchUrl;
            page.navigate(searchUrl);
            PlaywrightUtil.sleep(1);

            // 检查是否需要登录
            if (checkNeedLogin()) {
                sendProgress("需要重新登录，跳过关键词: " + keyword, null, null);
                return;
            }

            // 点击排序选项（选择第一个排序方式）
            try {
                Locator sortOptions = page.locator("div.ss");
                if (sortOptions.count() > 0) {
                    sortOptions.first().click();
                    PlaywrightUtil.sleep(1);
                }
            } catch (Exception e) { /* 静默 */ }

            // 遍历页面投递
            for (int pageNum = 1; pageNum <= DEFAULT_MAX_PAGE; pageNum++) {
                if (shouldStop()) {
                    sendProgress("用户取消投递", null, null);
                    return;
                }

                sendProgress(String.format("正在投递第%d页", pageNum), pageNum, DEFAULT_MAX_PAGE);
                currentPageNum = pageNum;

                // 跳转到指定页码
                if (pageNum > 1 && !jumpToPage(pageNum)) {
                    break;
                }

                PlaywrightUtil.sleep(2);

                // 检查是否出现访问验证
                if (checkAccessVerification()) {
                    sendProgress("出现访问验证，停止投递", null, null);
                    return;
                }

                // 检测“无职位”文案，提前结束当前关键词
                try {
                    if (detectNoJobs51job()) {
                        sendProgress("该关键词暂无职位，提前结束", null, null);
                        break;
                    }
                } catch (Exception ignored) {}

                // 投递当前页面的所有职位
                deliverCurrentPage();
                if (reachedDailyLimit) break;

                PlaywrightUtil.sleep(3);
            }

            // 关键词完成不输出日志
        } catch (Exception e) { /* 静默 */ }
    }

    /**
     * 逐个投递当前页面的所有职位（避开51job平台的批量投递限制、遮挡弹窗，彻底杜绝应届生网站外链弹窗）
     */
    private void deliverCurrentPage() {
        try {
            PlaywrightUtil.sleep(1);

            // 0. 清除可能残留的“不能批量投递”或遮罩弹窗
            closeAnyModalOverlays();

            // 1. 网络层封锁：阻止应届生与外部校招站点的网络请求
            try {
                page.route("**/*yingjiesheng*/**", Route::abort);
                page.route("**/*xyz.51job*/**", Route::abort);
            } catch (Exception ignored) {}

            // 2. 页面脚本级加固：覆写 window.open 并标记/清理所有应届生与校招外链
            try {
                page.evaluate("() => {"
                    + "  if (!window.__yjshPopupGuarded) {"
                    + "    window.__yjshPopupGuarded = true;"
                    + "    const _origOpen = window.open;"
                    + "    window.open = function(url, ...args) {"
                    + "      if (typeof url === 'string' && (url.includes('yingjiesheng') || url.includes('xyz.51job') || url.includes('partner=51wspcjoblist'))) {"
                    + "        console.warn('[51job拦截] 阻止弹窗:', url);"
                    + "        return null;"
                    + "      }"
                    + "      return _origOpen ? _origOpen.apply(this, [url, ...args]) : null;"
                    + "    };"
                    + "  }"
                    + "  document.querySelectorAll('a[href*=\"yingjiesheng\"], a[href*=\"xyz.51job\"], a[href*=\"partner=51wspcjoblist\"]').forEach(a => {"
                    + "    a.removeAttribute('target');"
                    + "    a.setAttribute('href', 'javascript:void(0)');"
                    + "    a.onclick = function(e) { e.preventDefault(); e.stopPropagation(); return false; };"
                    + "    const card = a.closest('.joblist-item, [class*=\"joblist-item\"], [class*=\"sensors-position-click\"], .j_joblist > div, tr');"
                    + "    if (card) {"
                    + "      card.setAttribute('data-skip-external', 'true');"
                    + "      card.querySelectorAll('button').forEach(b => b.setAttribute('data-skip-external', 'true'));"
                    + "    }"
                    + "  });"
                    + "  document.querySelectorAll('.el-overlay, [class*=\"modal\"], [class*=\"mask\"], [class*=\"backdrop\"]').forEach(el => {"
                    + "    if (el.innerText && (el.innerText.includes('不能批量') || el.innerText.includes('下载') || el.innerText.includes('成功'))) el.remove();"
                    + "  });"
                    + "}");
            } catch (Exception ignored) {}

            // 3. 页面级 onPopup 监听：一旦触发 popup（例如 target=_blank 点击），0ms 瞬间关闭
            java.util.function.Consumer<Page> popupCloser = (Page popup) -> {
                try {
                    String pUrl = "";
                    try { pUrl = popup.url(); } catch (Exception ignored) {}
                    log.warn("[51job] 立即拦截并闪电关闭外部弹出窗口: {}", pUrl);
                    try {
                        popup.close(new Page.CloseOptions().setRunBeforeUnload(false));
                    } catch (Exception ex) {
                        try { popup.close(); } catch (Exception ignored) {}
                    }
                } catch (Exception ignored) {}
            };
            page.onPopup(popupCloser);

            // 4. 上下文级 onPage 监听：只要有非主页面的新标签页出现，立即关闭，绝不等待 loadState
            java.util.function.Consumer<Page> tabCloser = (Page newPage) -> {
                try {
                    if (newPage != page) {
                        String newUrl = "";
                        try { newUrl = newPage.url(); } catch (Exception ignored) {}
                        log.warn("[51job] 立即拦截并关闭弹出新标签页: {}", newUrl);
                        try {
                            newPage.close(new Page.CloseOptions().setRunBeforeUnload(false));
                        } catch (Exception ex) {
                            try { newPage.close(); } catch (Exception ignored) {}
                        }
                    }
                } catch (Exception ignored) {}
            };
            page.context().onPage(tabCloser);

            try {
                // 仅定位职位列表区域（.j_joblist / .j_result）内的真实投递按钮，绝不匹配顶部导航栏（如“投递记录”/“校园招聘”）
                Locator applyButtons = page.locator(".j_joblist [class*='sensors-position-click'] button:has-text('投递'), .j_joblist button:has-text('投递'):not(:has-text('一键投递')), .j_result button:has-text('投递'):not(:has-text('一键投递'))");
                int totalOnPage = applyButtons.count();
                if (totalOnPage == 0) {
                    applyButtons = page.locator(".j_joblist .btn:has-text('投递'), .sensors-position-click .btn:has-text('投递')");
                    totalOnPage = applyButtons.count();
                }

                if (totalOnPage == 0) {
                    log.warn("[51job] 当前页列表区域未找到任何岗位的投递按钮");
                    return;
                }

                Locator titles = page.locator(".j_joblist [class*='jname text-cut'], [class*='jname']");
                Locator companies = page.locator(".j_joblist [class*='cname text-cut'], [class*='cname']");

                log.info("[51job] 当前页共发现 {} 个岗位的投递按钮，开始逐个稳健投递...", totalOnPage);

                for (int i = 0; i < totalOnPage; i++) {
                    if (shouldStop() || reachedDailyLimit) {
                        return;
                    }

                    // 检查主页面是否被意外重定向到了应届生网站或外部页面，若是则自动拉回
                    try {
                        String currentUrl = page.url();
                        if (currentUrl != null && (currentUrl.contains("yingjiesheng") || currentUrl.contains("xyz.51job") || !currentUrl.contains("51job.com/pc/search"))) {
                            log.warn("[51job] 检测到页面跳离搜索列表（当前: {}），正在自动导航回搜索页...", currentUrl);
                            if (currentSearchUrl != null && !currentSearchUrl.isEmpty()) {
                                page.navigate(currentSearchUrl);
                                PlaywrightUtil.sleep(2);
                            }
                        }
                    } catch (Exception ignored) {}

                    // 每次点击前清除可能遮挡操作的任何弹窗和遮罩
                    closeAnyModalOverlays();

                    try {
                        // 重新获取按钮引用，避免DOM刷新失效
                        Locator currentBtns = page.locator(".j_joblist [class*='sensors-position-click'] button:has-text('投递'), .j_joblist button:has-text('投递'):not(:has-text('一键投递')), .j_result button:has-text('投递'):not(:has-text('一键投递'))");
                        if (currentBtns.count() == 0) {
                            currentBtns = page.locator(".j_joblist .btn:has-text('投递'), .sensors-position-click .btn:has-text('投递')");
                        }
                        if (i >= currentBtns.count()) {
                            break;
                        }
                        Locator btn = currentBtns.nth(i);

                        String btnText = "";
                        try { btnText = btn.innerText().trim(); } catch (Exception ignored) {}

                        // 如果已投递或文字包含“记录”等无关文字则跳过
                        if (btnText.contains("已投递") || btnText.contains("已申请") || btnText.contains("记录")) {
                            continue;
                        }

                        // 5. 深入检查当前按钮与所在卡片：严格过滤应届生与外部校招跳转外链
                        boolean isExternalJob = false;
                        String externalReason = "";
                        try {
                            Object checkRes = btn.evaluate("el => {"
                                + "  if (el.getAttribute('data-skip-external') === 'true') return { skip: true, reason: 'marked-btn' };"
                                + "  const txt = (el.innerText || '').trim();"
                                + "  if (txt.includes('网申') || txt.includes('跳转') || txt.includes('去申请')) return { skip: true, reason: 'btn-text:' + txt };"
                                + "  const a = el.closest('a');"
                                + "  if (a && a.href && (a.href.includes('yingjiesheng') || a.href.includes('xyz.51job') || a.href.includes('partner=51wspcjoblist'))) {"
                                + "    return { skip: true, reason: 'btn-anchor:' + a.href };"
                                + "  }"
                                + "  const card = el.closest('.joblist-item, [class*=\"joblist-item\"], [class*=\"sensors-position-click\"], .j_joblist > div, tr');"
                                + "  if (card) {"
                                + "    if (card.getAttribute('data-skip-external') === 'true') return { skip: true, reason: 'marked-card' };"
                                + "    const links = card.querySelectorAll('a[href]');"
                                + "    for (let j = 0; j < links.length; j++) {"
                                + "      const h = links[j].href || '';"
                                + "      if (h.includes('yingjiesheng') || h.includes('xyz.51job') || h.includes('partner=51wspcjoblist')) {"
                                + "        return { skip: true, reason: 'card-link:' + h };"
                                + "      }"
                                + "    }"
                                + "    const cText = card.innerText || '';"
                                + "    if (cText.includes('应届生网') || cText.includes('应届生求职网')) return { skip: true, reason: 'card-text' };"
                                + "  }"
                                + "  return { skip: false };"
                                + "}");
                            if (checkRes instanceof Map) {
                                @SuppressWarnings("unchecked")
                                Map<String, Object> map = (Map<String, Object>) checkRes;
                                Boolean skip = (Boolean) map.get("skip");
                                if (Boolean.TRUE.equals(skip)) {
                                    isExternalJob = true;
                                    externalReason = String.valueOf(map.get("reason"));
                                }
                            }
                        } catch (Exception ignored) {}

                        String title = i < titles.count() ? titles.nth(i).textContent().trim() : "岗位 " + (i + 1);
                        String company = i < companies.count() ? companies.nth(i).textContent().trim() : "企业";
                        String jobInfo = company + " | " + title;

                        if (isExternalJob) {
                            log.info("[51job] 岗位属于外部应届生校招外链（{}），直接跳过以避免跳出平台: {}", externalReason, jobInfo);
                            continue;
                        }

                        // 滚动到视野中并点击
                        try { btn.scrollIntoViewIfNeeded(); } catch (Exception ignored) {}
                        PlaywrightUtil.sleep(1);

                        btn.click(new Locator.ClickOptions().setTimeout(2500).setForce(true));
                        log.info("[51job] 已点击单岗投递: {}", jobInfo);
                        PlaywrightUtil.sleep(1);

                        // 关闭投递后可能出现的弹窗（扫码下载App、投递成功确认、单独申请等）
                        handlePostDeliveryModals();

                        resultList.add(jobInfo);
                        sendProgress(String.format("正在投递: %s", jobInfo), resultList.size(), null);

                        // 标记数据库已投递
                        synchronized (currentPageJobIds) {
                            if (i < currentPageJobIds.size()) {
                                Long jid = currentPageJobIds.get(i);
                                try {
                                    job51Service.markDelivered(jid);
                                } catch (Exception ignored) {}
                            }
                        }

                        // 检查日投递上限
                        if (detectDailyLimitToast51job()) {
                            reachedDailyLimit = true;
                            log.warn("检测到 51job 日投递上限提示，停止投递");
                            sendProgress("检测到日投递上限，任务已停止", null, null);
                            return;
                        }

                        // 真人间隔 1.5 秒
                        PlaywrightUtil.sleep(1);

                    } catch (Exception itemEx) {
                        log.warn("[51job] 投递第 {} 个岗位时异常: {}", i + 1, itemEx.getMessage());
                    }
                }
            } finally {
                try { page.offPopup(popupCloser); } catch (Exception ignored) {}
                try { page.context().offPage(tabCloser); } catch (Exception ignored) {}
            }

        } catch (Exception e) {
            log.error("逐个投递当前页面失败", e);
        }
    }

    /**
     * 单个投递后清理所有可能遮挡的弹窗
     */
    private void handlePostDeliveryModals() {
        try {
            // 1. 关闭扫码下载APP弹窗
            try {
                Locator appClose = page.locator("[class*='van-popup__close-icon'], [class*='van-icon-cross']");
                if (appClose.count() > 0 && appClose.first().isVisible()) {
                    appClose.first().click(new Locator.ClickOptions().setTimeout(1000).setForce(true));
                }
            } catch (Exception ignored) {}

            // 2. 确认提示弹窗
            try {
                Locator okBtn = page.locator(".el-dialog__footer button, .el-message-box__btns button, button:has-text('确定'), button:has-text('知道了')");
                if (okBtn.count() > 0 && okBtn.first().isVisible()) {
                    okBtn.first().click(new Locator.ClickOptions().setTimeout(1000).setForce(true));
                }
            } catch (Exception ignored) {}

            // 3. 关闭单独申请或Header关闭
            try {
                Locator dialogClose = page.locator("button.el-dialog__headerbtn, button[aria-label='Close'], i.el-icon-close");
                if (dialogClose.count() > 0 && dialogClose.first().isVisible()) {
                    dialogClose.first().click(new Locator.ClickOptions().setTimeout(1000).setForce(true));
                }
            } catch (Exception ignored) {}

            // 4. 清除任何残留全屏遮罩
            try {
                page.evaluate("() => {"
                    + "  document.querySelectorAll('.el-overlay, [class*=\"modal-mask\"], [class*=\"backdrop\"]').forEach(el => el.remove());"
                    + "}");
            } catch (Exception ignored) {}
        } catch (Exception ignored) {}
    }

    /**
     * 点击批量投递按钮
     */
    private boolean clickBatchDeliverButton() {
        int retryCount = 0;
        boolean success = false;

        try {
            page.screenshot(new Page.ScreenshotOptions().setPath(java.nio.file.Paths.get("target/job51_search.png")));
            Object btnDump = page.evaluate("() => {"
                + "  return Array.from(document.querySelectorAll('button, a, div[role=\"button\"], [class*=\"btn\"], [class*=\"apply\"], [class*=\"deliver\"], [class*=\"tab\"]'))"
                + "    .filter(el => el.offsetParent !== null && (el.innerText || '').trim().length > 0)"
                + "    .map(el => el.tagName + '.' + el.className.toString().replace(/\\s+/g, '.') + ' : ' + el.innerText.trim().replace(/\\n/g, ' '))"
                + "    .slice(0, 30)"
                + "    .join('\\n');"
                + "}");
            log.info("[51job排查] 当前页面可见按钮与操作栏列表:\n{}", btnDump);
        } catch (Exception ex) {
            log.warn("[51job排查] 截屏与元素探测失败: {}", ex.getMessage());
        }

        while (!success && retryCount < 5) {
            try {
                if (shouldStop()) {
                    return false;
                }

                // 1. 优先使用文本匹配现代 51job 投递按钮（申请职位、投递简历、批量申请、立即投递等）
                Locator textBtns = page.locator("button:has-text('申请职位'), button:has-text('投递简历'), button:has-text('批量申请'), button:has-text('立即投递'), a:has-text('申请职位'), div[role='button']:has-text('申请职位')");
                if (textBtns.count() > 0 && textBtns.first().isVisible()) {
                    log.info("[51job] 匹配到投递按钮（文本匹配）: {}", textBtns.first().innerText());
                    textBtns.first().click(new Locator.ClickOptions().setTimeout(3000).setForce(true));
                    success = true;
                } else {
                    // 2. 备选：查找 tabs_in 下的按钮
                    Locator parent = page.locator("div.tabs_in, div[class*='tabs_in'], [class*='bottom_bar'], [class*='batch']");
                    Locator buttons = parent.locator("button.p_but, button[class*='p_but'], button");
                    if (buttons.count() > 1) {
                        PlaywrightUtil.sleep(1);
                        log.info("[51job] 匹配到旧版 tabs_in 投递按钮");
                        buttons.nth(1).click(new Locator.ClickOptions().setTimeout(3000).setForce(true));
                        success = true;
                    } else if (buttons.count() == 1) {
                        PlaywrightUtil.sleep(1);
                        log.info("[51job] 匹配到 tabs_in 单个按钮: {}", buttons.first().innerText());
                        buttons.first().click(new Locator.ClickOptions().setTimeout(3000).setForce(true));
                        success = true;
                    } else {
                        // 3. 兜底：通过 JS 在全文档查找包含“申请”或“投递”的按钮
                        Object jsClick = page.evaluate("() => {"
                            + "  const btns = Array.from(document.querySelectorAll('button, a, div'))"
                            + "    .filter(el => el.offsetParent !== null && (el.innerText.trim() === '申请职位' || el.innerText.trim() === '投递简历' || el.innerText.trim() === '批量申请'));"
                            + "  if (btns.length > 0) {"
                            + "    btns[0].click();"
                            + "    return 'clicked: ' + btns[0].innerText;"
                            + "  }"
                            + "  return 'none';"
                            + "}");
                        log.info("[51job] JS兜底点击投递按钮结果: {}", jsClick);
                        if (jsClick != null && jsClick.toString().startsWith("clicked")) {
                            success = true;
                        } else {
                            log.warn("[51job] 未能找到任何投递按钮 (尝试第 {} 次)", retryCount + 1);
                            retryCount++;
                            PlaywrightUtil.sleep(1);
                            continue;
                        }
                    }
                }

                if (success) {
                    // 🚨 点击后立即检测“日投递上限”提示（短暂出现，需快速多次检测）
                    for (int i = 0; i < 10; i++) {
                        try { Thread.sleep(200); } catch (InterruptedException ignored) {}
                        if (detectDailyLimitToast51job()) {
                            reachedDailyLimit = true;
                            log.warn("点击投递按钮后，检测到 51job 日投递上限提示，停止投递");
                            sendProgress("检测到日投递上限，任务已停止", null, null);
                            return true;
                        }
                    }
                    return true;
                }
            } catch (Exception e) {
                log.warn("[51job] 点击投递按钮异常: {}", e.getMessage());
                retryCount++;
                PlaywrightUtil.sleep(1);
            }
        }
        return success;
    }

    /**
     * 处理投递成功弹窗
     */
    private void handleDeliverySuccessDialog() {
        try {
            PlaywrightUtil.sleep(2);

            Locator successContent = page.locator("//div[@class='successContent']");
            if (successContent.count() > 0) {
                String text = successContent.textContent();
                if (text != null && text.contains("快来扫码下载")) {
                    log.info("检测到下载App弹窗，关闭中...");
                    // 关闭弹窗
                    Locator closeButton = page.locator("[class*='van-icon van-icon-cross van-popup__close-icon van-popup__close-icon--top-right']");
                    if (closeButton.count() > 0) {
                        closeButton.click();
                        log.info("成功关闭下载App弹窗");
                    }
                }
            }

            // 兼容提示弹框：投递成功N个，未投递M个（更稳健选择器）
            Locator elDialogBody = page.locator(".el-dialog__body, .el-message-box__message, [class*='dialog'] [class*='content'], [class*='dialog'] [class*='body']");
            if (elDialogBody.count() > 0) {
                String dialogText = elDialogBody.first().innerText();
                log.info("[51job] 弹窗原始内容: {}", dialogText);
                if (dialogText != null && (dialogText.contains("投递成功") || dialogText.contains("成功") || dialogText.contains("已申请"))) {
                    Integer successNum = null;
                    Integer failNum = null;
                    try {
                        java.util.regex.Matcher m1 = java.util.regex.Pattern.compile("投递成功\\D*(\\d+)|成功\\D*(\\d+)|(\\d+)\\D*个").matcher(dialogText);
                        if (m1.find()) {
                            for (int g = 1; g <= m1.groupCount(); g++) {
                                if (m1.group(g) != null) {
                                    successNum = Integer.parseInt(m1.group(g));
                                    break;
                                }
                            }
                        }
                        java.util.regex.Matcher m2 = java.util.regex.Pattern.compile("未投递\\D*(\\d+)|失败\\D*(\\d+)").matcher(dialogText);
                        if (m2.find()) {
                            for (int g = 1; g <= m2.groupCount(); g++) {
                                if (m2.group(g) != null) {
                                    failNum = Integer.parseInt(m2.group(g));
                                    break;
                                }
                            }
                        }
                    } catch (Exception ignored) {}
                    if (successNum == null) {
                        successNum = 20; // 弹窗提示成功但未带具体数字时，保底认为当前页20个成功
                    }
                    log.info("[51job] 投递结果：成功 {} 个，未投递 {} 个", successNum, failNum);
                    sendProgress(String.format("投递结果：成功 %s 个，未投递 %s 个", successNum, failNum == null ? 0 : failNum), null, null);

                    // ✅ 投递成功后，标记数据库中的岗位为已投递
                    if (successNum > 0) {
                        try {
                            List<Long> deliveredIds = new ArrayList<>();
                            synchronized (currentPageJobIds) {
                                deliveredIds.addAll(currentPageJobIds);
                            }
                            if (!deliveredIds.isEmpty()) {
                                int markCount = Math.min(successNum, deliveredIds.size());
                                List<Long> toMark = deliveredIds.subList(0, markCount);
                                job51Service.markDeliveredBatch(toMark);
                                log.info("[51job] 标记已投递 {} 个职位", toMark.size());
                            } else {
                                log.warn("[51job] 当前页没有缓存的jobId，无法标记投递状态");
                            }
                        } catch (Exception e) {
                            log.warn("[51job] 标记投递状态失败: {}", e.getMessage());
                        }
                    }

                    // 优先点击“确定/关闭”按钮，其次点右上角关闭，再次退格键
                    try {
                        Locator okBtn = page.locator(".el-dialog__footer button:has-text('确定'), .el-message-box__btns button:has-text('确定'), button:has-text('知道了')");
                        if (okBtn.count() > 0) {
                            okBtn.first().click();
                        } else {
                            // 1) 点击关闭图标的父按钮
                            Locator iconClose = page.locator("i.el-dialog__close.el-icon.el-icon-close");
                            boolean closed = false;
                            if (iconClose.count() > 0 && iconClose.first().isVisible()) {
                                try {
                                    iconClose.first().evaluate("el => el.parentElement && el.parentElement.click()");
                                    closed = true;
                                } catch (Exception ignored) {}
                            }
                            // 2) 直接点击 header 关闭按钮（带 aria-label="Close"）
                            if (!closed) {
                                Locator headerBtn = page.locator("button.el-dialog__headerbtn, .el-dialog__header button.el-dialog__headerbtn, button[aria-label='Close']");
                                if (headerBtn.count() > 0 && headerBtn.first().isVisible()) {
                                    try {
                                        headerBtn.first().click(new Locator.ClickOptions().setForce(true).setTimeout(2000));
                                        closed = true;
                                    } catch (Exception ignored) {}
                                }
                            }
                            // 3) JS 兜底点击
                            if (!closed) {
                                try {
                                    page.evaluate("document.querySelector('button.el-dialog__headerbtn')?.click() || document.querySelector('button[aria-label=\\'Close\\']')?.click() ");
                                    closed = true;
                                } catch (Exception ignored) {}
                            }
                            // 4) 最终兜底：按 ESC
                            if (!closed) {
                                page.keyboard().press("Escape");
                            }
                        }
                        PlaywrightUtil.sleep(1);
                    } catch (Exception ignored) {}
                }
            }

            // 统一尝试关闭任何残留的弹框覆盖层
            closeAnyModalOverlays();
            // 弹窗处理后再次检测是否出现“日投递上限”提示
            try {
                if (detectDailyLimitToast51job()) {
                    reachedDailyLimit = true;
                    log.warn("处理成功弹窗后，检测到 51job 日投递上限提示，停止当前页");
                }
            } catch (Exception ignored) {}
        } catch (Exception e) {
            log.debug("未找到投递成功弹窗或处理失败: {}", e.getMessage());
        }
    }

    /**
     * 处理单独投递申请弹窗
     */
    private void handleSeparateDeliveryDialog() {
        try {
            Locator dialogContent = page.locator("//div[@class='el-dialog__body']/span");
            if (dialogContent.count() > 0) {
                String text = dialogContent.textContent();
                if (text != null && text.contains("需要到企业招聘平台单独申请")) {
                    log.info("检测到单独投递申请弹窗，关闭中...");
                    // 关闭弹窗
                    Locator closeButton = page.locator("#app > div > div.post > div > div > div.j_result > div > div:nth-child(2) > div > div:nth-child(2) > div:nth-child(2) > div > div.el-dialog__header > button > i");
                    if (closeButton.count() > 0) {
                        closeButton.click();
                        log.info("成功关闭单独投递申请弹窗");
                    }
                }
            }
        } catch (Exception e) {
            log.debug("未找到单独投递申请弹窗或处理失败: {}", e.getMessage());
        }
    }

    /**
     * 跳转到指定页码
     */
    private boolean jumpToPage(int pageNum) {
        for (int retry = 0; retry < 3; retry++) {
            try {
                if (shouldStop()) {
                    return false;
                }

                // 跳页前先尝试关闭可能遮挡操作的弹框
                closeAnyModalOverlays();

                // 1. 优先尝试 Element Plus 标准【下一页】按钮
                Locator nextBtn = page.locator("button.btn-next, .el-pagination button.btn-next, [aria-label='Next page'], [class*='next']");
                if (nextBtn.count() > 0 && nextBtn.first().isEnabled()) {
                    nextBtn.first().click(new Locator.ClickOptions().setTimeout(3000).setForce(true));
                    PlaywrightUtil.sleep(2);
                    page.evaluate("window.scrollTo(0, 0)");
                    log.info("[51job] 成功点击【下一页】进入第{}页", pageNum);
                    return true;
                }

                // 2. 尝试点击对应的页码数字
                Locator numBtn = page.locator("ul.el-pager li.number:has-text('" + pageNum + "'), [class*='pagination'] li:has-text('" + pageNum + "')");
                if (numBtn.count() > 0) {
                    numBtn.first().click(new Locator.ClickOptions().setTimeout(3000).setForce(true));
                    PlaywrightUtil.sleep(2);
                    page.evaluate("window.scrollTo(0, 0)");
                    log.info("[51job] 成功点击页码【{}】", pageNum);
                    return true;
                }

                // 3. 备选：输入框跳转
                Locator pageInput = page.locator(".el-pagination__jump input, #jump_page, input[aria-label*='页']");
                if (pageInput.count() > 0) {
                    pageInput.first().click();
                    pageInput.first().fill("");
                    pageInput.first().fill(String.valueOf(pageNum));
                    pageInput.first().press("Enter");
                    page.evaluate("window.scrollTo(0, 0)");
                    PlaywrightUtil.sleep(2);
                    log.info("[51job] 成功输入页码【{}】并跳转", pageNum);
                    return true;
                }
            } catch (Exception e) {
                log.warn("跳转到第{}页失败，重试第{}次: {}", pageNum, retry + 1, e.getMessage());
                PlaywrightUtil.sleep(1);
            }
        }
        return false;
    }

    /**
     * 统一关闭可能出现的弹框覆盖层（ElementUI/VanPopup 等）。
     */
    private void closeAnyModalOverlays() {
        try {
            boolean closedOnce = false;
            for (int t = 0; t < 3; t++) {
                boolean closedThisRound = false;
                Locator headerClose = page.locator("button.el-dialog__headerbtn, button[aria-label='Close']");
                if (headerClose.count() > 0 && headerClose.first().isVisible()) {
                    try {
                        headerClose.first().click(new Locator.ClickOptions().setForce(true).setTimeout(2000));
                        closedThisRound = true;
                    } catch (Exception ignored) {}
                }
                // 直接点击关闭图标或其父按钮
                Locator iconClose = page.locator("i.el-dialog__close.el-icon.el-icon-close");
                if (iconClose.count() > 0 && iconClose.first().isVisible()) {
                    try {
                        iconClose.first().evaluate("el => el.parentElement && el.parentElement.click()");
                        closedThisRound = true;
                    } catch (Exception ignored) {}
                }
                Locator okBtn = page.locator(".el-dialog__footer button:has-text('确定'), .el-message-box__btns button:has-text('确定')");
                if (okBtn.count() > 0 && okBtn.first().isVisible()) {
                    okBtn.first().click();
                    closedThisRound = true;
                }
                // JS 一次性点击所有可能的关闭按钮，作为强兜底
                if (!closedThisRound) {
                    try {
                        page.evaluate("document.querySelectorAll('button.el-dialog__headerbtn, button[aria-label=\\'Close\\']').forEach(b=>b.click())");
                    } catch (Exception ignored) {}
                }
                Locator popupClose = page.locator(".van-popup__close-icon, .van-icon-cross");
                if (popupClose.count() > 0 && popupClose.first().isVisible()) {
                    popupClose.first().click();
                    closedThisRound = true;
                }
                if (!closedThisRound) break;
                closedOnce = true;
                PlaywrightUtil.sleep(1);
            }
            if (closedOnce) {
                // 等待弹层移除
                try { page.waitForSelector(".el-dialog__wrapper, .van-popup", new Page.WaitForSelectorOptions().setState(WaitForSelectorState.DETACHED).setTimeout(2000)); } catch (Exception ignored) {}
            }
        } catch (Exception e) {
            log.debug("关闭弹框覆盖层失败: {}", e.getMessage());
        }
    }

    /**
     * 检查是否需要登录
     */
    private boolean checkNeedLogin() {
        try {
            Locator loginElement = page.locator("//a[contains(@class, 'uname')]");
            if (loginElement.count() > 0) {
                String text = loginElement.textContent();
                return text != null && text.contains("登录");
            }
            return false;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 检测 51job 页面是否出现“日投递上限”提示的浮框（短暂存在，需及时检查）。
     */
    private boolean detectDailyLimitToast51job() {
        try {
            String[] kws = new String[]{
                    "今日投递太多", "您今日投递太多", "休息一下明天再来", "达到上限", "次数过多"
            };
            for (String kw : kws) {
                Locator textToast = page.locator("text=" + kw);
                if (textToast.count() > 0 && textToast.first().isVisible()) {
                    return true;
                }
            }
            Locator msg = page.locator(".el-message, .el-message--info, .toast, .message, div[role='alert'], .el-notification__content");
            if (msg.count() > 0) {
                java.util.List<String> texts = new java.util.ArrayList<>();
                try { texts = msg.allInnerTexts(); } catch (Exception ignored) {}
                for (String t : texts) {
                    if (t == null) continue;
                    String tt = t.replace('\n', ' ').trim();
                    for (String kw : kws) {
                        if (tt.contains(kw)) return true;
                    }
                }
            }
            Object foundObj = page.evaluate("() => { const kws = ['今日投递太多','您今日投递太多','休息一下明天再来','达到上限','次数过多']; const bodyText = document.body ? (document.body.innerText || '') : ''; return kws.some(k=>bodyText.includes(k)); }");
            if (foundObj instanceof Boolean) {
                return (Boolean) foundObj;
            }
            return false;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 检查是否出现访问验证
     */
    private boolean checkAccessVerification() {
        try {
            Locator wafTitle = page.locator("//p[@class='waf-nc-title']");
            Locator wafScript = page.locator("script[name^='aliyunwaf_']");
            Locator verifyText = page.locator("text=访问验证, text=请按住滑块");
            if ((wafTitle.count() > 0 && wafTitle.first().isVisible()) || wafScript.count() > 0 || verifyText.count() > 0) {
                log.error("出现访问验证，需要手动处理");
                return true;
            }
            return false;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 检测 51job 页面是否显示“无职位/没有符合条件的职位”等提示。
     */
    private boolean detectNoJobs51job() {
        try {
            String[] kws = new String[]{
                    "暂无职位", "没有符合条件的职位", "暂无符合条件职位", "暂无符合职位", "暂无相关职位"
            };
            for (String kw : kws) {
                Locator t = page.locator("text=" + kw);
                if (t.count() > 0 && t.first().isVisible()) {
                    return true;
                }
            }
            // 常见空态容器（若存在则进一步通过文本确认）
            Locator empty = page.locator(".el-empty, .empty, .no-result, .no_res");
            if (empty.count() > 0) {
                java.util.List<String> texts = new java.util.ArrayList<>();
                try { texts = empty.allInnerTexts(); } catch (Exception ignored) {}
                for (String t : texts) {
                    if (t == null) continue;
                    String tt = t.replace('\n', ' ').trim();
                    for (String kw : kws) {
                        if (tt.contains(kw)) return true;
                    }
                }
            }
            return false;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 构建搜索URL
     */
    private String buildSearchUrl(String keyword) {
        StringBuilder url = new StringBuilder(BASE_URL);
        url.append(JobUtils.appendListParam("jobArea", config.getJobArea()));
        url.append(JobUtils.appendListParam("salary", config.getSalary()));
        url.append("&keyword=").append(keyword);
        return url.toString();
    }

    /**
     * 采集当前页所有岗位的 jobId（解析 jobdetail 链接/数据属性）
     */
    private List<Long> collectJobIdsOnPage() {
        List<Long> ids = new ArrayList<>();
        try {
            // 1) 解析常见 jobdetail 链接形态
            Locator anchors = page.locator(
                    "a[href*='/pc/jobdetail?jobId='], " +
                    "a[href*='/pc/jobdetail'], " +
                    "a[href*='jobs.51job.com/'], " +
                    "a.jname[href]"
            );
            int count = anchors.count();
            for (int i = 0; i < count; i++) {
                try {
                    String href = anchors.nth(i).getAttribute("href");
                    Long id = parseJobIdFromHref(href);
                    if (id != null) ids.add(id);
                } catch (Exception ignored) {}
            }

            // 2) 解析卡片上的数据属性（部分页面存在）
            try {
                Locator cards = page.locator("[data-jobid], [data-analysis-jobid], [data-job-id]");
                int c = cards.count();
                for (int i = 0; i < c; i++) {
                    try {
                        String v = null;
                        Locator card = cards.nth(i);
                        v = v == null ? card.getAttribute("data-jobid") : v;
                        v = v == null ? card.getAttribute("data-analysis-jobid") : v;
                        v = v == null ? card.getAttribute("data-job-id") : v;
                        if (v != null) {
                            try {
                                Long id = Long.parseLong(v.replaceAll("[^0-9]", ""));
                                if (id != null) ids.add(id);
                            } catch (Exception ignored) {}
                        }
                    } catch (Exception ignored) {}
                }
            } catch (Exception ignored) {}

            // 去重
            java.util.Set<Long> uniq = new java.util.LinkedHashSet<>(ids);
            ids = new java.util.ArrayList<>(uniq);

            // 记录采集到的数量与部分样例，便于诊断
            try {
                if (!ids.isEmpty()) {
                    String sample = ids.stream().limit(5).map(String::valueOf).collect(java.util.stream.Collectors.joining(", "));
                    log.info("[51job] 当前页采集到 {} 个 jobId, 示例: {}", ids.size(), sample);
                } else {
                    // 采集为空：按用户约定视为达到投递上限/页面结构变化，立即通知并停止
                    log.warn("[51job] 当前页未采集到任何 jobId，可能页面结构变化或选择器不匹配");
                    // 向前端推送警告，便于按钮重置
                    sendProgress("[51job] 当前页未采集到任何 jobId，可能页面结构变化或选择器不匹配", null, null);
                    // 设置达上限标记，外层循环将终止
                    reachedDailyLimit = true;
                }
            } catch (Exception ignored) {}
        } catch (Exception e) {
            log.debug("采集当前页 jobId 失败: {}", e.getMessage());
        }
        return ids;
    }

    private Long parseJobIdFromHref(String href) {
        if (href == null || href.isEmpty()) return null;
        try {
            java.util.regex.Matcher m = java.util.regex.Pattern.compile("[?&]jobId=(\\d+)").matcher(href);
            if (m.find()) {
                return Long.parseLong(m.group(1));
            }
            java.util.regex.Matcher m2 = java.util.regex.Pattern.compile("/(\\d+)\\.html").matcher(href);
            if (m2.find()) {
                return Long.parseLong(m2.group(1));
            }
            // 兜底：从路径段中找较长数字片段
            java.util.regex.Matcher m3 = java.util.regex.Pattern.compile("(\\d{5,})").matcher(href);
            if (m3.find()) {
                return Long.parseLong(m3.group(1));
            }
        } catch (Exception ignored) {}
        return null;
    }

    /**
     * 格式化时长
     */
    private String formatDuration(long millis) {
        long seconds = millis / 1000;
        long minutes = seconds / 60;
        long hours = minutes / 60;

        if (hours > 0) {
            return String.format("%d小时%d分钟", hours, minutes % 60);
        } else if (minutes > 0) {
            return String.format("%d分钟%d秒", minutes, seconds % 60);
        } else {
            return String.format("%d秒", seconds);
        }
    }

    /**
     * 发送进度消息
     */
    private void sendProgress(String message, Integer current, Integer total) {
        if (progressCallback != null) {
            progressCallback.accept(message, current, total);
        }
    }

    /**
     * 检查是否应该停止
     */
    private boolean shouldStop() {
        return shouldStopCallback != null && shouldStopCallback.get();
    }

    /**
     * 从JSON文本中提取jobId列表
     */
    private List<Long> extractJobIdsFromJson(String json) {
        List<Long> jobIds = new ArrayList<>();
        if (json == null || json.trim().isEmpty()) {
            return jobIds;
        }
        
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            com.fasterxml.jackson.databind.JsonNode root = mapper.readTree(json);
            
            // 兼容多种列表命名
            com.fasterxml.jackson.databind.JsonNode list = root.path("data").path("items");
            if (!list.isArray()) list = root.path("data").path("jobList");
            if (!list.isArray()) list = root.path("data").path("list");
            if (!list.isArray()) list = root.path("data").path("jobs");
            if (!list.isArray()) list = root.path("resultbody").path("job").path("items");
            if (!list.isArray()) list = root.path("job").path("items");
            if (!list.isArray()) list = root.path("resultbody").path("items");
            
            if (!list.isArray()) {
                return jobIds;
            }
            
            // 提取每个jobId
            for (com.fasterxml.jackson.databind.JsonNode item : list) {
                com.fasterxml.jackson.databind.JsonNode jobIdNode = item.path("jobId");
                if (!jobIdNode.isMissingNode() && !jobIdNode.isNull()) {
                    try {
                        Long jobId = jobIdNode.asLong();
                        if (jobId != null && jobId > 0) {
                            jobIds.add(jobId);
                        }
                    } catch (Exception e) {
                        // 忽略单个解析失败
                    }
                }
            }
            
        } catch (Exception e) {
            log.warn("[51job] 解析JSON提取jobId失败: {}", e.getMessage());
        }
        
        return jobIds;
    }
}
