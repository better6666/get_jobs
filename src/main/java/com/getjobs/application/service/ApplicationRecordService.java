package com.getjobs.application.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.getjobs.application.entity.AgentStrategyEntity;
import com.getjobs.application.entity.ApplicationRecordEntity;
import com.getjobs.application.entity.KeywordEntity;
import com.getjobs.application.entity.ResumeVersionEntity;
import com.getjobs.application.mapper.ApplicationRecordMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class ApplicationRecordService {

    private final ApplicationRecordMapper recordMapper;
    private final AgentStrategyService strategyService;
    private final KeywordService keywordService;

    /**
     * 记录或更新岗位流水
     */
    @Transactional
    public ApplicationRecordEntity recordDiscoveredJob(
            JobParserService.ParsedJob job,
            HardFilterService.FilterResult filterResult,
            JobScorerService.ScoreResult scoreResult,
            ResumeVersionEntity resume,
            String greeting
    ) {
        AgentStrategyEntity strategy = strategyService.getStrategy();
        int dedupDays = strategy.getDedupDays() != null ? strategy.getDedupDays() : 30;
        LocalDateTime dedupCutoff = LocalDateTime.now().minusDays(dedupDays);

        // 查找查重记录：同平台同jobId，或同平台同公司同岗位
        QueryWrapper<ApplicationRecordEntity> query = new QueryWrapper<>();
        query.eq("platform", job.getPlatform());
        if (job.getJobId() != null && !job.getJobId().isBlank()) {
            query.and(q -> q.eq("job_id", job.getJobId())
                    .or(w -> w.eq("company", job.getCompany()).eq("job_title", job.getJobTitle())));
        } else {
            query.eq("company", job.getCompany()).eq("job_title", job.getJobTitle());
        }
        query.ge("created_at", dedupCutoff).orderByDesc("id").last("LIMIT 1");

        ApplicationRecordEntity record = recordMapper.selectOne(query);
        boolean isNew = (record == null);
        if (isNew) {
            record = new ApplicationRecordEntity();
            record.setApplicationId("APP-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
            record.setPlatform(job.getPlatform());
            record.setJobId(job.getJobId());
            record.setJobTitle(job.getJobTitle());
            record.setCompany(job.getCompany());
            record.setCompanyScale(job.getCompanyScale());
            record.setIndustry(job.getIndustry());
            record.setSalary(job.getSalaryRange());
            record.setLocation(job.getLocation());
            record.setJobUrl(job.getJobUrl());
            record.setCreatedAt(LocalDateTime.now());
        }

        // 填充评分与解析详情
        record.setMatchScore(scoreResult.getTotalScore());
        record.setScoreBreakdown(new JSONObject(scoreResult.getBreakdown()).toString());
        record.setStrengths(new JSONArray(scoreResult.getStrengths()).toString());
        record.setRisks(new JSONArray(scoreResult.getRisks()).toString());
        record.setRiskLevel(scoreResult.getRiskLevel());
        record.setMatchLevel(scoreResult.getMatchLevel());

        if (resume != null) {
            record.setResumeId(resume.getId());
            record.setResumeName(resume.getVersionName());
        }
        if (greeting != null && !greeting.isBlank()) {
            record.setGreetingUsed(greeting);
        }

        // 判定生命周期状态
        if (!filterResult.isPass()) {
            record.setStatus("FILTERED");
            record.setNotes("硬性过滤未通过: " + String.join(", ", filterResult.getFailReasons()));
        } else if (Integer.valueOf(1).equals(strategy.getEnableRiskBlock()) && "HIGH".equalsIgnoreCase(scoreResult.getRiskLevel())) {
            record.setStatus("FILTERED");
            record.setNotes("识别为高风险岗位拦截: " + String.join(", ", scoreResult.getRisks()));
        } else {
            // 检查同公司今日投递频次上限
            if (isCompanyOverLimit(job.getPlatform(), job.getCompany(), strategy.getMaxJobsPerCompanyPerDay())) {
                record.setStatus("REVIEW_PENDING");
                record.setNotes("该企业今日投递已达上限(" + strategy.getMaxJobsPerCompanyPerDay() + "次/日)，转入待复核队列");
            } else if (scoreResult.getTotalScore() >= strategy.getAutoApplyThreshold()) {
                // 分数达标，符合自动投递
                if (record.getStatus() == null || "DISCOVERED".equals(record.getStatus())) {
                    record.setStatus("READY_TO_APPLY");
                }
            } else if (scoreResult.getTotalScore() >= strategy.getReviewMinThreshold()) {
                record.setStatus("REVIEW_PENDING");
                record.setNotes("处于复核分区间(" + strategy.getReviewMinThreshold() + "-" + (strategy.getAutoApplyThreshold() - 1) + "分)");
            } else {
                record.setStatus("FILTERED");
                record.setNotes("评分低于复核阈值(" + scoreResult.getTotalScore() + " < " + strategy.getReviewMinThreshold() + ")");
            }
        }

        record.setUpdatedAt(LocalDateTime.now());
        if (isNew) {
            recordMapper.insert(record);
        } else {
            recordMapper.updateById(record);
        }
        return record;
    }

    /**
     * 检查企业当天投递是否超限
     */
    public boolean isCompanyOverLimit(String platform, String company, Integer maxDaily) {
        if (company == null || company.isBlank() || maxDaily == null || maxDaily <= 0) return false;
        LocalDateTime startOfDay = LocalDateTime.of(LocalDate.now(), LocalTime.MIN);
        LocalDateTime endOfDay = LocalDateTime.of(LocalDate.now(), LocalTime.MAX);

        QueryWrapper<ApplicationRecordEntity> query = new QueryWrapper<>();
        query.eq("platform", platform)
                .eq("company", company)
                .in("status", Arrays.asList("APPLIED", "HR_READ", "HR_REPLIED", "INTERVIEW_INVITED", "INTERVIEWED", "OFFER"))
                .ge("apply_time", startOfDay)
                .le("apply_time", endOfDay);

        Long count = recordMapper.selectCount(query);
        return count >= maxDaily;
    }

    /**
     * 标记已成功投递
     */
    @Transactional
    public void markAsApplied(Long id, String greetingUsed) {
        ApplicationRecordEntity record = recordMapper.selectById(id);
        if (record != null) {
            record.setStatus("APPLIED");
            record.setApplyTime(LocalDateTime.now());
            if (greetingUsed != null && !greetingUsed.isBlank()) {
                record.setGreetingUsed(greetingUsed);
            }
            record.setUpdatedAt(LocalDateTime.now());
            recordMapper.updateById(record);
        }
    }

    /**
     * 人工复核队列列表
     */
    public Page<ApplicationRecordEntity> getReviewQueue(int page, int size) {
        Page<ApplicationRecordEntity> pageParam = new Page<>(page, size);
        QueryWrapper<ApplicationRecordEntity> query = new QueryWrapper<>();
        query.eq("status", "REVIEW_PENDING")
                .orderByDesc("match_score")
                .orderByDesc("id");
        return recordMapper.selectPage(pageParam, query);
    }

    /**
     * 复核操作：立即投递 / 跳过 / 加入黑名单
     */
    @Transactional
    public boolean handleReviewAction(Long id, String action, String customGreeting) {
        ApplicationRecordEntity record = recordMapper.selectById(id);
        if (record == null) return false;

        if ("APPROVE_APPLY".equalsIgnoreCase(action)) {
            record.setStatus("APPLIED");
            record.setApplyTime(LocalDateTime.now());
            if (customGreeting != null && !customGreeting.isBlank()) {
                record.setGreetingUsed(customGreeting);
            }
            record.setNotes("人工复核通过并确认投递");
        } else if ("SKIP".equalsIgnoreCase(action)) {
            record.setStatus("CLOSED");
            record.setNotes("人工复核跳过");
        } else if ("BLACKLIST_COMPANY".equalsIgnoreCase(action)) {
            record.setStatus("CLOSED");
            record.setNotes("用户拉黑该企业");
            // 自动将该企业加入负向关键词
            if (record.getCompany() != null && !record.getCompany().isBlank()) {
                KeywordEntity kw = new KeywordEntity();
                kw.setCategory("NEGATIVE");
                kw.setGroupName("企业黑名单");
                kw.setWord(record.getCompany());
                kw.setWeight(100);
                kw.setIsActive(1);
                kw.setContextRule("企业全称黑名单排除");
                keywordService.saveOrUpdate(kw);
            }
        }
        record.setUpdatedAt(LocalDateTime.now());
        return recordMapper.updateById(record) > 0;
    }

    /**
     * 多条件分页查询投递列表
     */
    public Page<ApplicationRecordEntity> listApplications(String status, String platform, String search, int page, int size) {
        Page<ApplicationRecordEntity> pageParam = new Page<>(page, size);
        QueryWrapper<ApplicationRecordEntity> query = new QueryWrapper<>();

        if (status != null && !status.isBlank() && !"ALL".equalsIgnoreCase(status)) {
            query.eq("status", status.toUpperCase());
        }
        if (platform != null && !platform.isBlank() && !"ALL".equalsIgnoreCase(platform)) {
            query.eq("platform", platform);
        }
        if (search != null && !search.isBlank()) {
            query.and(q -> q.like("job_title", search.trim())
                    .or().like("company", search.trim())
                    .or().like("notes", search.trim()));
        }

        query.orderByDesc("id");
        return recordMapper.selectPage(pageParam, query);
    }

    /**
     * 更新后续流转状态（已读、回复、面试、Offer）
     */
    @Transactional
    public boolean updateStatus(Long id, String newStatus, String hrReply, String interviewStatus, String notes) {
        ApplicationRecordEntity record = recordMapper.selectById(id);
        if (record == null) return false;

        if (newStatus != null && !newStatus.isBlank()) {
            record.setStatus(newStatus.toUpperCase());
        }
        if (hrReply != null && !hrReply.isBlank()) {
            record.setHrReply(hrReply);
            record.setHrReplyTime(LocalDateTime.now());
        }
        if (interviewStatus != null && !interviewStatus.isBlank()) {
            record.setInterviewStatus(interviewStatus);
            record.setInterviewTime(LocalDateTime.now());
        }
        if (notes != null && !notes.isBlank()) {
            record.setNotes(notes);
        }
        record.setUpdatedAt(LocalDateTime.now());
        return recordMapper.updateById(record) > 0;
    }

    /**
     * 获取全流程转化漏斗统计与KPI
     */
    public Map<String, Object> getFunnelStats() {
        Map<String, Object> result = new LinkedHashMap<>();

        long totalDiscovered = recordMapper.selectCount(null);
        long totalFiltered = recordMapper.selectCount(new QueryWrapper<ApplicationRecordEntity>().eq("status", "FILTERED"));
        long totalReviewPending = recordMapper.selectCount(new QueryWrapper<ApplicationRecordEntity>().eq("status", "REVIEW_PENDING"));
        long totalApplied = recordMapper.selectCount(new QueryWrapper<ApplicationRecordEntity>().in("status", Arrays.asList("APPLIED", "HR_READ", "HR_REPLIED", "INTERVIEW_INVITED", "INTERVIEWED", "OFFER", "REJECTED")));
        long totalHrRead = recordMapper.selectCount(new QueryWrapper<ApplicationRecordEntity>().in("status", Arrays.asList("HR_READ", "HR_REPLIED", "INTERVIEW_INVITED", "INTERVIEWED", "OFFER")));
        long totalHrReplied = recordMapper.selectCount(new QueryWrapper<ApplicationRecordEntity>().in("status", Arrays.asList("HR_REPLIED", "INTERVIEW_INVITED", "INTERVIEWED", "OFFER")));
        long totalInterview = recordMapper.selectCount(new QueryWrapper<ApplicationRecordEntity>().in("status", Arrays.asList("INTERVIEW_INVITED", "INTERVIEWED", "OFFER")));
        long totalOffer = recordMapper.selectCount(new QueryWrapper<ApplicationRecordEntity>().eq("status", "OFFER"));

        result.put("totalDiscovered", totalDiscovered);
        result.put("totalFiltered", totalFiltered);
        result.put("totalReviewPending", totalReviewPending);
        result.put("totalApplied", totalApplied);
        result.put("totalHrRead", totalHrRead);
        result.put("totalHrReplied", totalHrReplied);
        result.put("totalInterview", totalInterview);
        result.put("totalOffer", totalOffer);

        // 转化率计算
        double applyRate = totalDiscovered > 0 ? ((double) totalApplied / totalDiscovered) * 100.0 : 0.0;
        double hrReplyRate = totalApplied > 0 ? ((double) totalHrReplied / totalApplied) * 100.0 : 0.0;
        double interviewRate = totalApplied > 0 ? ((double) totalInterview / totalApplied) * 100.0 : 0.0;
        double offerRate = totalInterview > 0 ? ((double) totalOffer / totalInterview) * 100.0 : 0.0;

        Map<String, Object> rates = new HashMap<>();
        rates.put("applyRate", Math.round(applyRate * 10.0) / 10.0);
        rates.put("hrReplyRate", Math.round(hrReplyRate * 10.0) / 10.0);
        rates.put("interviewRate", Math.round(interviewRate * 10.0) / 10.0);
        rates.put("offerRate", Math.round(offerRate * 10.0) / 10.0);
        result.put("rates", rates);

        // 平台分布
        List<String> platforms = Arrays.asList("boss", "liepin", "job51", "zhilian");
        Map<String, Long> platformApplied = new HashMap<>();
        for (String p : platforms) {
            long count = recordMapper.selectCount(new QueryWrapper<ApplicationRecordEntity>()
                    .eq("platform", p)
                    .in("status", Arrays.asList("APPLIED", "HR_READ", "HR_REPLIED", "INTERVIEW_INVITED", "INTERVIEWED", "OFFER")));
            platformApplied.put(p, count);
        }
        result.put("platformApplied", platformApplied);

        return result;
    }
}
