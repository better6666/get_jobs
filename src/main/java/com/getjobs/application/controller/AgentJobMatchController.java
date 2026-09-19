package com.getjobs.application.controller;

import com.getjobs.application.entity.AgentStrategyEntity;
import com.getjobs.application.entity.ResumeVersionEntity;
import com.getjobs.application.service.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/agent")
@CrossOrigin(origins = "*")
@Slf4j
@RequiredArgsConstructor
public class AgentJobMatchController {

    private final JobParserService parserService;
    private final HardFilterService hardFilterService;
    private final JobScorerService scorerService;
    private final ResumeService resumeService;
    private final AiGreetingService greetingService;
    private final AgentStrategyService strategyService;
    private final ApplicationRecordService recordService;

    /**
     * 智能岗位匹配诊断与实时评测预览
     */
    @PostMapping("/match-preview")
    public ResponseEntity<Map<String, Object>> previewMatch(@RequestBody Map<String, String> body) {
        Map<String, Object> res = new HashMap<>();
        try {
            String title = body.getOrDefault("jobTitle", "未命名岗位");
            String company = body.getOrDefault("company", "测试企业");
            String salary = body.getOrDefault("salary", "15k-25k");
            String degree = body.getOrDefault("degree", "本科");
            String exp = body.getOrDefault("experience", "3-5年");
            String location = body.getOrDefault("location", "北京");
            String jd = body.getOrDefault("jobDescription", "");
            String hrActivity = body.getOrDefault("hrActivity", "今日活跃");
            String publishTime = body.getOrDefault("publishTime", "刚刚发布");

            // 1. 结构化解析
            JobParserService.ParsedJob job = parserService.parseJob("custom", null, title, company, salary, degree, exp, location, jd, hrActivity, publishTime);

            // 2. 硬性过滤
            HardFilterService.FilterResult filterResult = hardFilterService.checkFilter(job);

            // 3. 10 维可解释评分
            JobScorerService.ScoreResult scoreResult = scorerService.calculateMatchScore(job);

            // 4. 简历智能匹配
            ResumeVersionEntity bestResume = resumeService.selectBestResume(job);

            // 5. 零幻觉定制打招呼语
            String greeting = greetingService.generateGreeting(job, bestResume);

            // 6. 策略路由判定
            AgentStrategyEntity strategy = strategyService.getStrategy();
            String routeVerdict;
            if (!filterResult.isPass()) {
                routeVerdict = "硬性过滤拦截 (FILTERED)";
            } else if ("HIGH".equalsIgnoreCase(scoreResult.getRiskLevel())) {
                routeVerdict = "高风险拦截 (BLOCKED)";
            } else if (scoreResult.getTotalScore() >= strategy.getAutoApplyThreshold()) {
                routeVerdict = "自动投递队列 (AUTO_APPLY)";
            } else if (scoreResult.getTotalScore() >= strategy.getReviewMinThreshold()) {
                routeVerdict = "人工复核队列 (REVIEW_QUEUE)";
            } else {
                routeVerdict = "分数过低淘汰 (FILTERED)";
            }

            Map<String, Object> data = new HashMap<>();
            data.put("parsedJob", job);
            data.put("filterResult", filterResult);
            data.put("scoreResult", scoreResult);
            data.put("selectedResume", bestResume);
            data.put("generatedGreeting", greeting);
            data.put("routeVerdict", routeVerdict);
            data.put("currentStrategy", strategy);

            res.put("success", true);
            res.put("data", data);
            return ResponseEntity.ok(res);
        } catch (Exception e) {
            log.error("匹配诊断异常", e);
            res.put("success", false);
            res.put("message", e.getMessage());
            return ResponseEntity.internalServerError().body(res);
        }
    }
}
