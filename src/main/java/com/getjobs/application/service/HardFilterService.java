package com.getjobs.application.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.getjobs.application.entity.CandidateProfileEntity;
import com.getjobs.application.entity.KeywordEntity;
import com.getjobs.application.mapper.KeywordMapper;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 岗位硬性过滤器 (HardFilter)
 * 执行强制淘汰校验。严格遵守准则：
 * “优先”、“更佳”、“有经验者优先”、“本科优先” 全部不得作为硬过滤，绝不误杀！
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HardFilterService {

    private final KeywordMapper keywordMapper;
    private final JobRiskDetector riskDetector;
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private CandidateProfileService profileService;

    @Data
    public static class FilterResult {
        private boolean passed = true;
        private String rejectReason = "";
        private JobRiskDetector.RiskAssessment riskAssessment;

        public boolean isPass() {
            return passed;
        }

        public List<String> getFailReasons() {
            return (rejectReason != null && !rejectReason.isBlank()) ? List.of(rejectReason) : List.of();
        }

        public static FilterResult pass(JobRiskDetector.RiskAssessment risk) {
            FilterResult r = new FilterResult();
            r.setPassed(true);
            r.setRiskAssessment(risk);
            return r;
        }

        public static FilterResult reject(String reason) {
            FilterResult r = new FilterResult();
            r.setPassed(false);
            r.setRejectReason(reason);
            return r;
        }
    }

    public FilterResult checkFilter(JobParserService.ParsedJob job) {
        CandidateProfileEntity profile = (profileService != null) ? profileService.getProfile() : null;
        return evaluate(profile, job);
    }

    public FilterResult evaluate(CandidateProfileEntity profile, JobParserService.ParsedJob job) {
        if (profile == null) {
            return FilterResult.pass(new JobRiskDetector.RiskAssessment());
        }

        // 1. 高危岗位与招聘欺诈拦截
        JobRiskDetector.RiskAssessment risk = riskDetector.evaluate(
                job.getJobTitle(), job.getJobDescription(), job.getSalaryMinK(), job.getSalaryMaxK(), job.getCompanyName()
        );
        if (risk.isHighRisk()) {
            return FilterResult.reject("命中高危招聘风险: " + String.join("; ", risk.getReasons()));
        }

        // 2. 学历硬性不符过滤 (注意：若为“本科优先”但mustHave为大专或不限，则绝对不拦截！)
        String candidateDeg = profile.getHighestDegree() != null ? profile.getHighestDegree() : "大专";
        int candidateDegLevel = degreeLevel(candidateDeg);
        int jobMustHaveDegLevel = degreeLevel(job.getMustHaveDegree());

        if (candidateDegLevel < jobMustHaveDegLevel) {
            return FilterResult.reject("学历硬性不符：岗位必须要求【" + job.getMustHaveDegree() + "及以上】，候选人为【" + candidateDeg + "】");
        }

        // 3. 工作年限硬性不符且差距过大
        int candidateYears = profile.getWorkYears() != null ? profile.getWorkYears() : 0;
        if (!job.isAcceptFreshman() && job.getMustHaveExpYears() > 0) {
            if (job.getMustHaveExpYears() >= 3 && candidateYears == 0) {
                return FilterResult.reject("工作经验硬性不符：岗位必须要求【" + job.getMustHaveExpYears() + "年以上】，候选人为应届/0年经验");
            }
        }

        // 4. 薪资底线硬性不符（若岗位给出的最高薪资仍低于求职者最低接受底线，则不投）
        if (profile.getMinSalary() != null && profile.getMinSalary() > 0 && job.getSalaryMaxK() != null) {
            double jobMaxSalary = job.getSalaryMaxK() * 1000.0;
            if (jobMaxSalary < profile.getMinSalary() * 0.85) { // 留15%弹性缓冲空间
                return FilterResult.reject("薪资低于底线：岗位最高提供 " + (int) jobMaxSalary + "元，求职者最低要求 " + profile.getMinSalary() + "元");
            }
        }

        // 5. 目标城市与异地意愿判断
        if (Integer.valueOf(0).equals(profile.getAcceptRemote()) && Integer.valueOf(0).equals(profile.getAcceptRelocation())) {
            String targetCities = profile.getTargetCities() != null ? profile.getTargetCities() : "";
            if (!job.getLocation().isEmpty() && !targetCities.contains(job.getLocation())) {
                return FilterResult.reject("城市不符且不接受异地：岗位位于【" + job.getLocation() + "】，不在目标城市列表");
            }
        }

        // 6. 偏好排斥过滤 (如明确排斥外包、电销)
        if (Integer.valueOf(0).equals(profile.getAcceptOutsourcing()) && job.isOutsourcing()) {
            return FilterResult.reject("排斥外包：岗位属于外包/派遣性质");
        }
        if (Integer.valueOf(0).equals(profile.getAcceptTeleSales()) && job.isSalesOrTeleSales()) {
            return FilterResult.reject("排斥销售：岗位属于电话销售/地推性质");
        }

        // 7. 排除词语义匹配（防上下文误伤，如“本岗位不是电话销售”）
        try {
            List<KeywordEntity> negativeKws = keywordMapper.selectList(
                    new QueryWrapper<KeywordEntity>().eq("category", "NEGATIVE").eq("is_active", 1)
            );
            String fullDesc = (job.getJobTitle() + " " + job.getJobDescription()).toLowerCase();
            for (KeywordEntity kw : negativeKws) {
                String word = kw.getWord().toLowerCase();
                if (fullDesc.contains(word)) {
                    // 检查是否是否定上下文（如“不是电话销售”、“不招保险”、“无需地推”）
                    if (fullDesc.contains("不是" + word) || fullDesc.contains("不招" + word) || fullDesc.contains("无需" + word)
                            || fullDesc.contains("非" + word) || fullDesc.contains("不用" + word)) {
                        // 属于否定表述，不误杀！放行！
                        continue;
                    }
                    return FilterResult.reject("命中排除词【" + kw.getWord() + "】");
                }
            }
        } catch (Exception ignored) {}

        return FilterResult.pass(risk);
    }

    private int degreeLevel(String deg) {
        if (deg == null) return 0;
        if (deg.contains("博士")) return 4;
        if (deg.contains("硕士") || deg.contains("研究生")) return 3;
        if (deg.contains("本科")) return 2;
        if (deg.contains("大专") || deg.contains("专科")) return 1;
        return 0; // 不限
    }
}
