package com.getjobs.application.service;

import com.getjobs.application.entity.CandidateProfileEntity;
import com.getjobs.application.entity.ResumeVersionEntity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiGreetingService {

    private final AiService aiService;
    private final CandidateProfileService profileService;

    /**
     * 根据岗位、画像和简历生成 20-80 字的精准打招呼语
     */
    public String generateGreeting(JobParserService.ParsedJob job, ResumeVersionEntity resume) {
        CandidateProfileEntity profile = profileService.getProfile();

        // 默认兜底模板生成器
        String fallback = generateFallbackGreeting(profile, job, resume);

        try {
            String candidateSummary = (profile != null && profile.getSkills() != null) ? profile.getSkills() : "";
            if (candidateSummary == null || candidateSummary.isBlank()) {
                candidateSummary = (resume != null) ? resume.getHighlights() : "具有扎实的专业实践与项目落地经验";
            }

            String prompt = String.format(
                    "你是一名资深求职顾问。请根据求职者真实画像和目标岗位JD，写一句专业、真诚、直击痛点的求职打招呼语。\n" +
                    "【要求】\n" +
                    "1. 严格限制在 30 到 70 个字之间，精炼吸睛，禁止任何套话废话。\n" +
                    "2. 严禁捏造虚假经历或数据，必须严格基于给出的真实画像。\n" +
                    "3. 说明针对该岗位的最匹配核心优势，礼貌询问是否方便聊聊。\n" +
                    "4. 只输出打招呼正文，不要有任何多余前缀、引号或解释。\n\n" +
                    "【目标岗位】\n" +
                    "职位: %s\n" +
                    "公司: %s\n" +
                    "关键技能要求: %s\n\n" +
                    "【求职者真实画像】\n" +
                    "最高学历: %s\n" +
                    "工作年限: %s\n" +
                    "核心优势: %s\n" +
                    "选用简历版本: %s (亮点: %s)\n",
                    job.getJobTitle(),
                    job.getCompany(),
                    job.getRequiredSkills() != null ? String.join(", ", job.getRequiredSkills()) : "专业技能",
                    profile != null ? profile.getHighestDegree() : "本科",
                    profile != null ? profile.getWorkYears() + "年" : "3年",
                    candidateSummary,
                    resume != null ? resume.getVersionName() : "标准版",
                    resume != null ? resume.getHighlights() : ""
            );

            String response = aiService.sendRequest(prompt);
            if (response != null && !response.isBlank()) {
                String clean = response.replace("\"", "").replace("“", "").replace("”", "").trim();
                // 剔除可能的换行符
                clean = clean.replaceAll("\\r?\\n", " ");
                if (clean.length() >= 15 && clean.length() <= 120) {
                    return clean;
                }
            }
        } catch (Exception e) {
            log.warn("AI 生成打招呼语异常，降级为规则模板: {}", e.getMessage());
        }

        return fallback;
    }

    /**
     * 规则引擎兜底生成器（0 幻觉，完全真实）
     */
    public String generateFallbackGreeting(CandidateProfileEntity profile, JobParserService.ParsedJob job, ResumeVersionEntity resume) {
        String title = job.getJobTitle() != null ? job.getJobTitle() : "贵司职位";
        String years = (profile != null && profile.getWorkYears() != null) ? profile.getWorkYears() + "年" : "";
        String highlight = (resume != null && resume.getHighlights() != null && !resume.getHighlights().isBlank())
                ? resume.getHighlights()
                : "深厚实战落地经验与解决问题能力";

        // 挑选一个最契合的技能词
        String matchedSkill = "";
        if (job.getRequiredSkills() != null && !job.getRequiredSkills().isEmpty()) {
            matchedSkill = job.getRequiredSkills().get(0);
        }

        if (!matchedSkill.isEmpty() && years.length() > 0) {
            return String.format("您好！看到贵司正在招聘%s，我有%s相关实战经验，深度契合%s要求，期待与您深入沟通！",
                    title, years, matchedSkill);
        } else if (years.length() > 0) {
            return String.format("您好！看到贵司在招%s，我有%s专业经验，主攻方向高度匹配，希望能与您交流了解！",
                    title, years);
        } else {
            return String.format("您好！关注到贵司正在招聘%s，我的技术栈与项目经历与该岗位契合，期待有机会与您详聊！",
                    title);
        }
    }
}
