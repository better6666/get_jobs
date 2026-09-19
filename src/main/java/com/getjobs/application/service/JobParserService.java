package com.getjobs.application.service;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 岗位结构化解析服务 (JobParser)
 * 精准区分：
 *  - must_have (硬性必须条件)
 *  - preferred (软性优先条件)
 *  - optional (描述性/加分项)
 * 彻底避免“本科优先”被误杀成“硬性必须本科”。
 */
@Slf4j
@Service
public class JobParserService {

    @Data
    public static class ParsedJob {
        private String platform = "custom";
        private String jobId;
        private String jobTitle;
        private String companyName;
        private String companyScale;
        private String industry;
        private String location;
        private String jobUrl;
        private String salaryRange;
        private String jobDescription;

        // 薪资数值化 (单位: 千元/月 K)
        private Double salaryMinK;
        private Double salaryMaxK;
        private Integer salaryMonths = 12;

        // 学历维度：区分硬性要求与软性优先
        private String mustHaveDegree = "不限";    // 硬性底线：大专 / 本科 / 硕士 / 博士 / 不限
        private String preferredDegree = null;    // 优先建议：例如“本科优先”时，mustHave=大专/不限，preferred=本科

        // 经验维度：区分硬性年限与软性优先
        private Integer mustHaveExpYears = 0;     // 硬性要求工作年限：0为应届/不限
        private Integer preferredExpYears = null;
        private boolean acceptFreshman = true;    // 是否接受应届生/实习生

        // 技能标签提取
        private List<String> requiredSkills = new ArrayList<>();
        private List<String> preferredSkills = new ArrayList<>();

        // 岗位属性标签
        private boolean outsourcing = false;      // 疑似外包
        private boolean salesOrTeleSales = false; // 销售/电话销售
        private boolean highRiskJob = false;      // 疑似高危/诈骗
        private String riskReason = "";

        // 岗位新鲜度与活跃度打分
        private int freshnessScore = 3;           // 0-4 分
        private int hrActiveScore = 3;            // 0-5 分

        public String getCompany() {
            return companyName != null ? companyName : "";
        }

        public int getMinExpYears() {
            return mustHaveExpYears != null ? mustHaveExpYears : 0;
        }

        public Double getMinSalaryK() {
            return salaryMinK;
        }

        public Double getMaxSalaryK() {
            return salaryMaxK;
        }

        public int getHrActivityScore() {
            return hrActiveScore;
        }

        public boolean isRemote() {
            String all = (jobTitle + " " + location + " " + jobDescription).toLowerCase();
            return all.contains("远程") || all.contains("在家办公") || all.contains("remote");
        }
    }

    public ParsedJob parseJob(String platform, String jobId, String title, String compName, String salaryText,
                             String degreeText, String expText, String location, String desc, String hrActive, String publishTime) {
        ParsedJob job = parse(title, salaryText, degreeText, expText, desc, compName, "", hrActive, publishTime);
        if (platform != null) job.setPlatform(platform);
        if (jobId != null) job.setJobId(jobId);
        if (location != null && !location.isBlank()) job.setLocation(location.trim());
        if (salaryText != null) job.setSalaryRange(salaryText.trim());
        return job;
    }

    /**
     * 核心解析方法
     */
    public ParsedJob parse(String title, String salaryText, String degreeText, String expText,
                           String desc, String compName, String industry, String hrActive, String publishTime) {
        ParsedJob job = new ParsedJob();
        job.setJobTitle(title != null ? title.trim() : "");
        job.setCompanyName(compName != null ? compName.trim() : "");
        job.setIndustry(industry != null ? industry.trim() : "");
        job.setLocation(degreeText != null && degreeText.contains("市") ? degreeText : "");
        job.setJobDescription(desc != null ? desc.trim() : "");

        String fullText = (job.getJobTitle() + " " + (desc != null ? desc : "")).toLowerCase();

        // 1. 薪资解析 (例如 "8-15K·14薪", "100-150元/天", "6000-8000")
        parseSalary(salaryText, job);

        // 2. 学历解析：精准区分 must_have 与 preferred
        parseDegree(degreeText, desc, job);

        // 3. 经验年限解析
        parseExperience(expText, desc, job);

        // 4. 技能词抽取
        extractSkills(fullText, job);

        // 5. 属性判断：外包、销售、电销
        checkAttributes(fullText, compName, job);

        // 6. 新鲜度与HR活跃度量化
        parseFreshnessAndActive(publishTime, hrActive, job);

        return job;
    }

    private void parseSalary(String text, ParsedJob job) {
        if (text == null || text.isBlank()) return;
        try {
            // 匹配 "8-15k", "8k-15k", "8-15千"
            Matcher m = Pattern.compile("(\\d+(\\.\\d+)?)\\s*[-~至]\\s*(\\d+(\\.\\d+)?)\\s*([kK千万])?").matcher(text);
            if (m.find()) {
                double min = Double.parseDouble(m.group(1));
                double max = Double.parseDouble(m.group(3));
                String unit = m.group(5);
                if (unit != null && unit.contains("万")) {
                    min *= 10;
                    max *= 10;
                }
                job.setSalaryMinK(min);
                job.setSalaryMaxK(max);
            } else {
                // 纯数字 "6000-8000"
                Matcher m2 = Pattern.compile("(\\d{4,6})\\s*[-~至]\\s*(\\d{4,6})").matcher(text);
                if (m2.find()) {
                    job.setSalaryMinK(Double.parseDouble(m2.group(1)) / 1000.0);
                    job.setSalaryMaxK(Double.parseDouble(m2.group(2)) / 1000.0);
                }
            }
            // 薪资月数 "14薪", "16薪"
            Matcher mMonths = Pattern.compile("(\\d{2})\\s*薪").matcher(text);
            if (mMonths.find()) {
                job.setSalaryMonths(Integer.parseInt(mMonths.group(1)));
            }
        } catch (Exception ignored) {}
    }

    private void parseDegree(String degreeText, String desc, ParsedJob job) {
        String dt = (degreeText != null ? degreeText : "") + " " + (desc != null ? desc : "");
        String lower = dt.toLowerCase();

        // 优先判断是否出现"软性优先"
        boolean hasBachelorPreferred = lower.contains("本科优先") || lower.contains("本科学历优先") || lower.contains("全日制统招本科优先")
                || lower.contains("本科及以上学历优先") || lower.contains("优先本科");
        boolean hasMasterPreferred = lower.contains("硕士优先") || lower.contains("研究生优先");

        if (hasMasterPreferred) {
            job.setPreferredDegree("硕士");
        } else if (hasBachelorPreferred) {
            job.setPreferredDegree("本科");
        }

        // 确定硬性底线 must_have
        if (lower.contains("博士")) {
            job.setMustHaveDegree("博士");
        } else if (lower.contains("硕士及以上") || (lower.contains("硕士") && !hasMasterPreferred)) {
            job.setMustHaveDegree("硕士");
        } else if (lower.contains("本科及以上") || lower.contains("仅限本科") || lower.contains("统招全日制本科") || (degreeText != null && degreeText.contains("本科") && !hasBachelorPreferred)) {
            job.setMustHaveDegree("本科");
        } else if (lower.contains("大专及以上") || lower.contains("大专") || (degreeText != null && degreeText.contains("大专"))) {
            job.setMustHaveDegree("大专");
        } else {
            job.setMustHaveDegree("不限");
        }
    }

    private void parseExperience(String expText, String desc, ParsedJob job) {
        String text = (expText != null ? expText : "") + " " + (desc != null ? desc : "");
        String lower = text.toLowerCase();

        if (lower.contains("在读") || lower.contains("实习生") || lower.contains("应届") || lower.contains("毕业生") || lower.contains("校招")
                || (expText != null && (expText.contains("应届") || expText.contains("在校")))) {
            job.setAcceptFreshman(true);
            job.setMustHaveExpYears(0);
            return;
        }

        if (lower.contains("经验不限") || lower.contains("不限经验") || lower.contains("无需经验") || (expText != null && expText.contains("不限"))) {
            job.setAcceptFreshman(true);
            job.setMustHaveExpYears(0);
            return;
        }

        // 识别是否有“经验优先”软性词
        boolean hasExpPreferred = lower.contains("经验优先") || lower.contains("有经验者优先") || lower.contains("相关工作经验优先");
        if (hasExpPreferred) {
            job.setAcceptFreshman(true);
            job.setPreferredExpYears(1);
        }

        // 提取具体年限要求（如 "3-5年", "1-3年", "5年以上", "至少3年"）
        Matcher m = Pattern.compile("(?:至少|具有|具备)?\\s*(\\d+)\\s*[-~至]\\s*(\\d+)?\\s*年").matcher(text);
        if (m.find()) {
            int y = Integer.parseInt(m.group(1));
            if (!hasExpPreferred) {
                job.setMustHaveExpYears(y);
                job.setAcceptFreshman(y == 0);
            } else {
                job.setPreferredExpYears(y);
                job.setMustHaveExpYears(0);
            }
        } else {
            if (expText != null) {
                if (expText.contains("1-3年") || expText.contains("1年以内")) job.setMustHaveExpYears(1);
                else if (expText.contains("3-5年")) job.setMustHaveExpYears(3);
                else if (expText.contains("5-10年")) job.setMustHaveExpYears(5);
            }
        }
    }

    private void extractSkills(String text, ParsedJob job) {
        String[] candidateSkills = new String[]{
                "python", "javascript", "vue", "react", "java", "sql", "matlab", "simulink", "simscape",
                "sketchup", "autocad", "cad", "photoshop", "ps", "illustrator", "ai", "indesign", "office", "excel",
                "chatgpt", "claude", "prompt", "aigc", "大模型", "智能体", "agent", "playwright", "selenium",
                "自动化", "数据分析", "陈列设计", "视觉陈列", "空间设计", "美工", "动线规划", "橱窗设计", "内容运营", "短视频"
        };
        for (String skill : candidateSkills) {
            if (text.contains(skill)) {
                job.getRequiredSkills().add(skill);
            }
        }
    }

    private void checkAttributes(String text, String compName, ParsedJob job) {
        String full = text + " " + (compName != null ? compName.toLowerCase() : "");
        if (full.contains("外包") || full.contains("驻场") || full.contains("派遣") || full.contains("人力资源外包")) {
            job.setOutsourcing(true);
        }
        if (full.contains("电话销售") || full.contains("电销") || full.contains("网销") || full.contains("打电销") || full.contains("地推销售")) {
            job.setSalesOrTeleSales(true);
        }
    }

    private void parseFreshnessAndActive(String publishTime, String hrActive, ParsedJob job) {
        // 新鲜度
        if (publishTime != null) {
            String pt = publishTime.trim();
            if (pt.contains("刚刚") || pt.contains("分钟") || pt.contains("小时") || pt.contains("今日") || pt.contains("今天")) {
                job.setFreshnessScore(4);
            } else if (pt.contains("昨天") || pt.contains("1天") || pt.contains("2天") || pt.contains("3天")) {
                job.setFreshnessScore(3);
            } else if (pt.contains("周内") || pt.contains("4天") || pt.contains("5天") || pt.contains("6天") || pt.contains("7天")) {
                job.setFreshnessScore(2);
            } else if (pt.contains("月内") || pt.contains("14天") || pt.contains("两周")) {
                job.setFreshnessScore(1);
            } else {
                job.setFreshnessScore(0);
            }
        }

        // HR 活跃度
        if (hrActive != null) {
            String act = hrActive.trim();
            if (act.contains("刚刚活跃") || act.contains("在线") || act.contains("分钟前") || act.contains("当前在线")) {
                job.setHrActiveScore(5);
            } else if (act.contains("今日活跃") || act.contains("小时前")) {
                job.setHrActiveScore(4);
            } else if (act.contains("3日内活跃") || act.contains("昨日活跃")) {
                job.setHrActiveScore(3);
            } else if (act.contains("本周活跃") || act.contains("7日内活跃")) {
                job.setHrActiveScore(2);
            } else if (act.contains("半月活跃") || act.contains("2周内活跃")) {
                job.setHrActiveScore(1);
            } else {
                job.setHrActiveScore(0);
            }
        }
    }
}
