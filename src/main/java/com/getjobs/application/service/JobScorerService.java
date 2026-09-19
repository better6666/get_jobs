package com.getjobs.application.service;

import com.getjobs.application.entity.CandidateProfileEntity;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * 10 维度可解释岗位评分引擎 (JobScorer)
 * 遵循 100 分制打分卡，杜绝黑盒打分，精准给出扣分原因、优势与风险清单。
 */
@Slf4j
@Service
public class JobScorerService {

    @Autowired(required = false)
    private CandidateProfileService profileService;

    @Autowired(required = false)
    private JobRiskDetector riskDetector;

    @Data
    public static class ScoreDetail {
        private int score;
        private int maxScore;
        private String reason;

        public ScoreDetail(int score, int maxScore, String reason) {
            this.score = score;
            this.maxScore = maxScore;
            this.reason = reason;
        }
    }

    @Data
    public static class MatchResult {
        private int totalScore;
        private String matchLevel; // Priority / High / Normal / Review / Skip
        private Map<String, ScoreDetail> breakdown = new LinkedHashMap<>();
        private List<String> strengths = new ArrayList<>();
        private List<String> risks = new ArrayList<>();
    }

    @Data
    @lombok.EqualsAndHashCode(callSuper = false)
    public static class ScoreResult extends MatchResult {
        private String riskLevel = "LOW";
    }

    public ScoreResult calculateMatchScore(JobParserService.ParsedJob job) {
        CandidateProfileEntity profile = (profileService != null) ? profileService.getProfile() : null;
        JobRiskDetector.RiskAssessment risk = null;
        if (riskDetector != null) {
            risk = riskDetector.evaluate(job.getJobTitle(), job.getJobDescription(), job.getMinSalaryK(), job.getMaxSalaryK(), job.getCompany());
        }
        MatchResult base = score(profile, job, risk);
        ScoreResult res = new ScoreResult();
        res.setTotalScore(base.getTotalScore());
        res.setMatchLevel(base.getMatchLevel());
        res.setBreakdown(base.getBreakdown());
        res.setStrengths(base.getStrengths());
        res.setRisks(base.getRisks());
        res.setRiskLevel(risk != null ? risk.getLevel().name() : "LOW");
        return res;
    }

    public MatchResult score(CandidateProfileEntity profile, JobParserService.ParsedJob job, JobRiskDetector.RiskAssessment risk) {
        MatchResult result = new MatchResult();
        if (profile == null) {
            result.setTotalScore(75);
            result.setMatchLevel("Normal");
            return result;
        }

        int total = 0;

        // 1. 学历匹配 (15分)
        int degScore = 15;
        String degReason = "学历完全符合";
        String candDeg = profile.getHighestDegree() != null ? profile.getHighestDegree() : "大专";
        if ("本科".equals(job.getPreferredDegree()) && "大专".equals(candDeg)) {
            degScore = 11; // 软性偏好本科，专科给 11/15，绝不大扣分！
            degReason = "岗位偏好本科，大专为良好契合基础";
            result.getRisks().add("JD偏好本科（大专亦在候选范围）");
        } else if ("不限".equals(job.getMustHaveDegree()) || "大专".equals(job.getMustHaveDegree())) {
            degScore = 15;
            degReason = "学历门槛完全吻合";
            result.getStrengths().add("学历完全满足任职资格");
        } else {
            degScore = 12;
            degReason = "学历基本满足";
        }
        result.getBreakdown().put("学历匹配", new ScoreDetail(degScore, 15, degReason));
        total += degScore;

        // 2. 经验年限 (15分)
        int expScore = 15;
        String expReason = "工作年限契合";
        int candYears = profile.getWorkYears() != null ? profile.getWorkYears() : 3;
        int reqYears = job.getMinExpYears();
        if (candYears >= reqYears) {
            expScore = 15;
            expReason = "经验年限充裕达标";
            result.getStrengths().add("具备" + candYears + "年实战经验，满足岗位" + reqYears + "年要求");
        } else {
            int gap = reqYears - candYears;
            if (gap <= 1) {
                expScore = 11;
                expReason = "经验年限略有差距（差1年以内）";
                result.getRisks().add("年限略微低于JD名义年限要求");
            } else {
                expScore = 7;
                expReason = "经验年限明显不足";
                result.getRisks().add("经验年限存在一定差距");
            }
        }
        result.getBreakdown().put("经验年限", new ScoreDetail(expScore, 15, expReason));
        total += expScore;

        // 3. 核心技能匹配 (20分)
        int skillScore = 0;
        List<String> reqSkills = job.getRequiredSkills();
        String candSkillsStr = profile.getSkills() != null ? profile.getSkills().toLowerCase() : "";
        int hitCount = 0;
        if (reqSkills == null || reqSkills.isEmpty()) {
            skillScore = 16;
            result.getStrengths().add("通用技能范围契合");
        } else {
            for (String s : reqSkills) {
                if (candSkillsStr.contains(s.toLowerCase())) {
                    hitCount++;
                }
            }
            double ratio = (double) hitCount / reqSkills.size();
            skillScore = (int) Math.round(ratio * 20);
            if (hitCount >= 2) {
                result.getStrengths().add("核心关键技术栈高度命中: " + hitCount + "项");
            }
        }
        result.getBreakdown().put("核心技能", new ScoreDetail(skillScore, 20, "命中必选技能项: " + hitCount));
        total += skillScore;

        // 4. 加分技能 (10分)
        int prefSkillScore = 0;
        List<String> prefSkills = job.getPreferredSkills();
        int prefHit = 0;
        if (prefSkills != null && !prefSkills.isEmpty()) {
            for (String s : prefSkills) {
                if (candSkillsStr.contains(s.toLowerCase())) {
                    prefHit++;
                }
            }
            prefSkillScore = Math.min(10, prefHit * 4);
            if (prefHit > 0) {
                result.getStrengths().add("具备JD加分技能亮点: " + prefHit + "项");
            }
        } else {
            prefSkillScore = 7; // JD未列加分项，给基础分
        }
        result.getBreakdown().put("加分技能", new ScoreDetail(prefSkillScore, 10, "命中加分/优先技能项: " + prefHit));
        total += prefSkillScore;

        // 5. 岗位方向匹配 (12分)
        int titleScore = 10;
        String title = job.getJobTitle() != null ? job.getJobTitle().toLowerCase() : "";
        String targetJobsStr = profile.getTargetJobs() != null ? profile.getTargetJobs().toLowerCase() : "";
        boolean titleMatch = false;
        for (String target : targetJobsStr.replace("[", "").replace("]", "").replace("\"", "").split(",")) {
            String t = target.trim();
            if (!t.isEmpty() && title.contains(t)) {
                titleMatch = true;
                break;
            }
        }
        if (titleMatch) {
            titleScore = 12;
            result.getStrengths().add("职位名称与目标岗位高度一致");
        } else {
            titleScore = 8;
        }
        result.getBreakdown().put("岗位方向", new ScoreDetail(titleScore, 12, "求职意向契合度"));
        total += titleScore;

        // 6. 薪资契合度 (8分)
        int salScore = 8;
        int expMinSal = profile.getMinSalary() != null ? profile.getMinSalary() : 10000;
        if (job.getMaxSalaryK() != null) {
            int maxOffer = (int) (job.getMaxSalaryK() * 1000);
            if (maxOffer < expMinSal) {
                salScore = 3;
                result.getRisks().add("岗位薪资上限低于期望底线");
            } else {
                salScore = 8;
                result.getStrengths().add("薪资区间符合预期");
            }
        }
        result.getBreakdown().put("薪资契合", new ScoreDetail(salScore, 8, "薪资满足期望"));
        total += salScore;

        // 7. 城市/通勤距离 (8分)
        int cityScore = 8;
        String candCity = profile.getCurrentCity() != null ? profile.getCurrentCity() : "北京";
        String targetCities = profile.getTargetCities() != null ? profile.getTargetCities() : candCity;
        if (job.isRemote()) {
            cityScore = 8;
            result.getStrengths().add("支持远程办公");
        } else if (job.getLocation() != null && (job.getLocation().contains(candCity) || targetCities.contains(job.getLocation()))) {
            cityScore = 8;
            result.getStrengths().add("同城/目标城市岗位");
        } else {
            cityScore = 5;
            result.getRisks().add("非首选城市");
        }
        result.getBreakdown().put("城市通勤", new ScoreDetail(cityScore, 8, "地点匹配"));
        total += cityScore;

        // 8. HR活跃度 (5分)
        int hrScore = job.getHrActivityScore();
        if (hrScore >= 4) {
            result.getStrengths().add("HR近期高度活跃（快速响应概率高）");
        } else if (hrScore <= 1) {
            result.getRisks().add("HR在线活跃频率较低");
        }
        result.getBreakdown().put("HR活跃度", new ScoreDetail(hrScore, 5, "HR活跃指标"));
        total += hrScore;

        // 9. 职位新鲜度 (4分)
        int freshScore = job.getFreshnessScore();
        if (freshScore >= 3) {
            result.getStrengths().add("职位近期新发布，竞争机会大");
        } else if (freshScore == 0) {
            result.getRisks().add("职位发布已超两周");
        }
        result.getBreakdown().put("职位新鲜度", new ScoreDetail(freshScore, 4, "发布时间新鲜度"));
        total += freshScore;

        // 10. 公司质量与规模 (3分)
        int compScore = 2;
        if (job.isOutsourcing()) {
            compScore = 0;
            result.getRisks().add("公司具备外包/劳务派遣属性");
        } else {
            compScore = 3;
        }
        result.getBreakdown().put("公司质量", new ScoreDetail(compScore, 3, "雇主综合质量"));
        total += compScore;

        // 风险减分
        if (risk != null && risk.getLevel() == JobRiskDetector.RiskLevel.MEDIUM) {
            total = Math.max(0, total - 8);
            result.getRisks().addAll(risk.getReasons());
        }

        total = Math.min(100, Math.max(0, total));
        result.setTotalScore(total);

        // 分级
        if (total >= 90) result.setMatchLevel("Priority");
        else if (total >= 80) result.setMatchLevel("High");
        else if (total >= 70) result.setMatchLevel("Normal");
        else if (total >= 60) result.setMatchLevel("Review");
        else result.setMatchLevel("Skip");

        return result;
    }
}
