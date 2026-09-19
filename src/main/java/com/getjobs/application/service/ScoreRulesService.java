package com.getjobs.application.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.getjobs.application.entity.ConfigEntity;
import com.getjobs.application.mapper.ConfigMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * JD评分规则服务：规则存放在 config 表（key=SCORE_RULES）的 JSON 里，用户可自定义；
 * 未配置时使用内置的通用默认规则（不针对任何特定求职者）。
 *
 * JSON 结构（所有 match 均为"包含匹配"）：
 * {
 *   "threshold": 80,                  // 低于该分不投
 *   "degreeReject":  ["硕士","博士"], // 学历硬排除（岗位学历要求含该词直接跳过）
 *   "titleReject":   ["算法","总监"], // 岗位名硬排除
 *   "degree":      [{"match":"大专","score":20}],      // 学历（首个命中生效）
 *   "experience":  [{"match":"应届","score":20}],      // 经验（首个命中生效）
 *   "industry":    [{"match":"AI","score":25}],       // 公司行业（首个命中生效）
 *   "jobBoost":    [{"match":"AI运营","score":20}],   // 岗位名加分（首个命中生效）
 *   "jobPenalty":  [{"match":"客服","score":-15}],    // 岗位名减分（全部命中累加）
 *   "jdBoost":     [{"match":"专业不限","score":15}], // JD正文加分（全部命中累加）
 *   "jdPenalty":   [{"match":"仅限计算机","score":-30}]
 * }
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class ScoreRulesService {

    private final ConfigMapper configMapper;

    /** 通用默认规则：只反映"应届/低学历门槛/成长性行业"这类普遍偏好，不含任何个人化指向 */
    public static final String DEFAULT_RULES_JSON = "{\n"
            + "  \"threshold\": 80,\n"
            + "  \"degreeReject\": [\"硕士\", \"博士\"],\n"
            + "  \"titleReject\": [\"算法\", \"机器学习\", \"深度学习\", \"后端开发\", \"Java开发\", \"C++开发\", \"嵌入式\", \"架构师\", \"研究员\", \"总监\", \"负责人\"],\n"
            + "  \"degree\": [{\"match\": \"大专\", \"score\": 20}, {\"match\": \"学历不限\", \"score\": 20}, {\"match\": \"不限\", \"score\": 20}, {\"match\": \"本科优先\", \"score\": 0}, {\"match\": \"本科\", \"score\": -40}],\n"
            + "  \"experience\": [{\"match\": \"应届\", \"score\": 20}, {\"match\": \"经验不限\", \"score\": 20}, {\"match\": \"不限\", \"score\": 20}, {\"match\": \"在读\", \"score\": 20}, {\"match\": \"1年以内\", \"score\": 15}, {\"match\": \"3年\", \"score\": -40}, {\"match\": \"5年\", \"score\": -40}, {\"match\": \"资深\", \"score\": -40}, {\"match\": \"高级\", \"score\": -40}],\n"
            + "  \"industry\": [{\"match\": \"AI\", \"score\": 25}, {\"match\": \"AIGC\", \"score\": 25}, {\"match\": \"大模型\", \"score\": 25}, {\"match\": \"人工智能\", \"score\": 25}, {\"match\": \"生成式\", \"score\": 25}, {\"match\": \"智能体\", \"score\": 25}, {\"match\": \"软件\", \"score\": 15}, {\"match\": \"SaaS\", \"score\": 15}, {\"match\": \"信息\", \"score\": 15}, {\"match\": \"智能制造\", \"score\": 15}, {\"match\": \"半导体\", \"score\": 15}, {\"match\": \"自动化\", \"score\": 15}, {\"match\": \"机器人\", \"score\": 15}],\n"
            + "  \"jobBoost\": [{\"match\": \"AI运营\", \"score\": 20}, {\"match\": \"AI应用\", \"score\": 20}, {\"match\": \"实施\", \"score\": 15}, {\"match\": \"客户成功\", \"score\": 15}, {\"match\": \"技术支持\", \"score\": 15}, {\"match\": \"售前\", \"score\": 15}, {\"match\": \"解决方案\", \"score\": 15}, {\"match\": \"管培生\", \"score\": 15}, {\"match\": \"运营\", \"score\": 5}],\n"
            + "  \"jobPenalty\": [{\"match\": \"客服\", \"score\": -15}, {\"match\": \"销售\", \"score\": -10}, {\"match\": \"商务\", \"score\": -10}, {\"match\": \"行政\", \"score\": -20}, {\"match\": \"文员\", \"score\": -20}, {\"match\": \"提成\", \"score\": -30}, {\"match\": \"电销\", \"score\": -30}],\n"
            + "  \"jdBoost\": [{\"match\": \"专业不限\", \"score\": 15}, {\"match\": \"不限专业\", \"score\": 15}],\n"
            + "  \"jdPenalty\": [{\"match\": \"仅限计算机\", \"score\": -30}, {\"match\": \"限计算机专业\", \"score\": -30}]\n"
            + "}";

    public static class Rule {
        public final String match;
        public final int score;

        Rule(String match, int score) {
            this.match = match;
            this.score = score;
        }
    }

    public static class Rules {
        public int threshold = 80;
        public List<String> degreeReject = new ArrayList<>();
        public List<String> titleReject = new ArrayList<>();
        public List<Rule> degree = new ArrayList<>();
        public List<Rule> experience = new ArrayList<>();
        public List<Rule> industry = new ArrayList<>();
        public List<Rule> jobBoost = new ArrayList<>();
        public List<Rule> jobPenalty = new ArrayList<>();
        public List<Rule> jdBoost = new ArrayList<>();
        public List<Rule> jdPenalty = new ArrayList<>();
    }

    /**
     * 加载评分规则：优先读 config 表 SCORE_RULES，解析失败或未配置时回退内置默认
     */
    public Rules loadRules() {
        try {
            ConfigEntity entity = configMapper.selectOne(new QueryWrapper<ConfigEntity>()
                    .eq("config_key", "SCORE_RULES").last("LIMIT 1"));
            if (entity != null && entity.getConfigValue() != null && !entity.getConfigValue().isBlank()) {
                Rules rules = parse(entity.getConfigValue());
                if (rules != null) return rules;
            }
        } catch (Exception e) {
            log.warn("读取SCORE_RULES失败，使用默认评分规则: {}", e.getMessage());
        }
        return parse(DEFAULT_RULES_JSON);
    }

    private Rules parse(String json) {
        try {
            JSONObject root = new JSONObject(json);
            Rules r = new Rules();
            r.threshold = root.optInt("threshold", 80);
            r.degreeReject = toStringList(root.optJSONArray("degreeReject"));
            r.titleReject = toStringList(root.optJSONArray("titleReject"));
            r.degree = toRules(root.optJSONArray("degree"));
            r.experience = toRules(root.optJSONArray("experience"));
            r.industry = toRules(root.optJSONArray("industry"));
            r.jobBoost = toRules(root.optJSONArray("jobBoost"));
            r.jobPenalty = toRules(root.optJSONArray("jobPenalty"));
            r.jdBoost = toRules(root.optJSONArray("jdBoost"));
            r.jdPenalty = toRules(root.optJSONArray("jdPenalty"));
            return r;
        } catch (Exception e) {
            log.warn("SCORE_RULES JSON 解析失败: {}", e.getMessage());
            return null;
        }
    }

    private List<String> toStringList(JSONArray arr) {
        List<String> list = new ArrayList<>();
        if (arr == null) return list;
        for (int i = 0; i < arr.length(); i++) {
            String s = arr.optString(i, null);
            if (s != null && !s.isBlank()) list.add(s);
        }
        return list;
    }

    private List<Rule> toRules(JSONArray arr) {
        List<Rule> list = new ArrayList<>();
        if (arr == null) return list;
        for (int i = 0; i < arr.length(); i++) {
            JSONObject o = arr.optJSONObject(i);
            if (o == null) continue;
            String match = o.optString("match", "");
            if (!match.isBlank()) list.add(new Rule(match, o.optInt("score", 0)));
        }
        return list;
    }
}
