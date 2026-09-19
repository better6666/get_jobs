package com.getjobs.application.service;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 岗位风险识别器 (JobRiskDetector)
 * 检测培训贷、押金、刷单、纯提成无底薪、高薪杀猪盘等高危招聘风险
 */
@Slf4j
@Service
public class JobRiskDetector {

    public enum RiskLevel {
        LOW,
        MEDIUM,
        HIGH
    }

    @Data
    public static class RiskAssessment {
        private RiskLevel level = RiskLevel.LOW;
        private int riskScore = 0; // 0-100 风险分
        private List<String> reasons = new ArrayList<>();

        public boolean isHighRisk() {
            return level == RiskLevel.HIGH;
        }
    }

    public RiskAssessment evaluate(String title, String desc, Double salaryMinK, Double salaryMaxK, String compName) {
        RiskAssessment assessment = new RiskAssessment();
        String full = (title != null ? title : "") + " " + (desc != null ? desc : "") + " " + (compName != null ? compName : "");
        String lower = full.toLowerCase();

        // 1. 高危黑名单拦截词（致命诈骗/培训贷/收费） -> 判定为 HIGH
        String[] highRiskKeywords = new String[]{
                "培训费", "交纳押金", "入职押金", "自费体检", "贷款培训", "包就业实训", "分期付款培训",
                "兼职刷单", "兼职点赞", "跑分", "租用微信", "租用微信号", "高佣陪聊", "语音主播在家",
                "纯提成无底薪", "不设底薪只拿提成", "加盟费", "收保证金"
        };
        for (String kw : highRiskKeywords) {
            if (lower.contains(kw)) {
                assessment.setLevel(RiskLevel.HIGH);
                assessment.getReasons().add("命中高危招聘欺诈/收费特征: " + kw);
                assessment.setRiskScore(90);
                return assessment;
            }
        }

        // 2. 薪资异常逻辑判断
        if (salaryMinK != null && salaryMaxK != null) {
            // 薪资跨度离谱，例如 3K-50K（多为诱饵）
            if (salaryMinK <= 4.0 && salaryMaxK >= 30.0) {
                assessment.getReasons().add("薪资范围跨度过大（" + salaryMinK + "K-" + salaryMaxK + "K），疑似夸大宣传或提成浮动诱饵");
                assessment.setLevel(RiskLevel.MEDIUM);
                assessment.setRiskScore(50);
            }
        }

        // 3. 擦边或高压力销售特征
        String[] mediumRiskKeywords = new String[]{
                "高频出差", "无休", "单休不包吃住", "电话狂轰", "全天电销", "抗压能力极强接受每天10小时", "高额绩效无责任底薪低"
        };
        for (String kw : mediumRiskKeywords) {
            if (lower.contains(kw)) {
                if (assessment.getLevel() == RiskLevel.LOW) assessment.setLevel(RiskLevel.MEDIUM);
                assessment.getReasons().add("存在潜在工作强度与福利风险: " + kw);
                assessment.setRiskScore(Math.max(assessment.getRiskScore(), 40));
            }
        }

        return assessment;
    }
}
