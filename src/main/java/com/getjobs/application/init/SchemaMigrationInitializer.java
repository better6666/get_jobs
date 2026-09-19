package com.getjobs.application.init;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 数据库结构迁移：为升级用户补齐新增列（新装用户拿到的 db 模板已含全部列，此处幂等跳过）。
 * 每次给产品新增字段时，在这里追加一条安全的 ALTER TABLE 即可。
 */
@Component
@Order(1)
@RequiredArgsConstructor
@Slf4j
public class SchemaMigrationInitializer implements CommandLineRunner {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(String... args) {
        // 1. 原有增量列更新
        addColumnIfMissing("boss_data", "match_score", "INTEGER");

        // 2. 初始化 AI 求职 Agent 核心表体系
        initAgentTables();

        // 3. 初始化默认种子数据
        initAgentSeeds();
    }

    private void initAgentTables() {
        try {
            // (1) 候选人全维度画像表
            jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS candidate_profile (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    name VARCHAR(100),
                    gender VARCHAR(20),
                    age INTEGER,
                    current_city VARCHAR(100),
                    target_cities TEXT,
                    accept_remote INTEGER DEFAULT 1,
                    accept_relocation INTEGER DEFAULT 0,
                    job_status VARCHAR(50),
                    graduate_year INTEGER,
                    graduate_type VARCHAR(50),
                    onboard_time VARCHAR(50),
                    highest_degree VARCHAR(50),
                    school VARCHAR(100),
                    major VARCHAR(100),
                    is_unified INTEGER DEFAULT 1,
                    is_full_time INTEGER DEFAULT 1,
                    graduation_date VARCHAR(50),
                    work_years INTEGER DEFAULT 0,
                    internship_exp TEXT,
                    work_exp TEXT,
                    project_exp TEXT,
                    freelance_exp TEXT,
                    skills TEXT,
                    languages TEXT,
                    certificates TEXT,
                    target_jobs TEXT,
                    target_industries TEXT,
                    min_salary INTEGER DEFAULT 0,
                    expected_salary INTEGER DEFAULT 0,
                    preferred_scale TEXT,
                    preferred_comp_types TEXT,
                    accept_outsourcing INTEGER DEFAULT 0,
                    accept_dispatch INTEGER DEFAULT 0,
                    accept_sales INTEGER DEFAULT 0,
                    accept_tele_sales INTEGER DEFAULT 0,
                    accept_shift INTEGER DEFAULT 0,
                    accept_overtime INTEGER DEFAULT 1,
                    accept_travel INTEGER DEFAULT 0,
                    weights_config TEXT,
                    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
                    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP
                )
            """);

            // (2) 多版本简历库表
            jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS resume_version (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    resume_name VARCHAR(100) NOT NULL,
                    target_jobs TEXT,
                    target_industries TEXT,
                    matched_skills TEXT,
                    strengths TEXT,
                    file_path VARCHAR(500),
                    is_default INTEGER DEFAULT 0,
                    version VARCHAR(20) DEFAULT 'v1.0',
                    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
                    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP
                )
            """);

            // (3) 统一投递与全流程生命周期记录总表
            jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS application_record (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    application_id VARCHAR(64) UNIQUE,
                    platform VARCHAR(30) NOT NULL,
                    job_id VARCHAR(100) NOT NULL,
                    job_title VARCHAR(200) NOT NULL,
                    company VARCHAR(200) NOT NULL,
                    company_scale VARCHAR(50),
                    industry VARCHAR(100),
                    salary VARCHAR(100),
                    location VARCHAR(100),
                    job_url VARCHAR(500),
                    match_score INTEGER DEFAULT 0,
                    score_breakdown TEXT,
                    strengths TEXT,
                    risks TEXT,
                    risk_level VARCHAR(20) DEFAULT 'LOW',
                    match_level VARCHAR(20),
                    resume_id INTEGER,
                    resume_name VARCHAR(100),
                    greeting_used TEXT,
                    status VARCHAR(50) NOT NULL DEFAULT 'DISCOVERED',
                    apply_time DATETIME,
                    hr_reply TEXT,
                    hr_reply_time DATETIME,
                    interview_status VARCHAR(50),
                    interview_time DATETIME,
                    notes TEXT,
                    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
                    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP
                )
            """);

            // (4) 智能 Agent 投递策略控制表
            jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS agent_strategy_config (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    run_mode VARCHAR(30) DEFAULT 'BALANCED',
                    auto_apply_threshold INTEGER DEFAULT 78,
                    review_min_threshold INTEGER DEFAULT 60,
                    max_jobs_per_company_per_day INTEGER DEFAULT 2,
                    dedup_days INTEGER DEFAULT 30,
                    enable_ai_greeting INTEGER DEFAULT 1,
                    enable_risk_block INTEGER DEFAULT 1,
                    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
                    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP
                )
            """);

            // (5) 关键词与排除词全维度管理表
            jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS keyword_management (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    category VARCHAR(30) NOT NULL,
                    group_name VARCHAR(50),
                    word VARCHAR(100) NOT NULL,
                    weight INTEGER DEFAULT 10,
                    is_active INTEGER DEFAULT 1,
                    context_rule TEXT,
                    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
                    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP
                )
            """);

            // 建立索引加速查询
            jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_app_platform_job ON application_record(platform, job_id);");
            jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_app_company ON application_record(company);");
            jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_app_status ON application_record(status);");
            jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_kw_category ON keyword_management(category);");

            log.info("✓ AI 求职 Agent 核心数据表结构初始化/迁移成功");
        } catch (Exception e) {
            log.error("初始化 AI 求职 Agent 数据表异常: {}", e.getMessage(), e);
        }
    }

    private void initAgentSeeds() {
        try {
            // 种子1: 候选人画像
            Integer profileCount = jdbcTemplate.queryForObject("SELECT count(1) FROM candidate_profile", Integer.class);
            if (profileCount == null || profileCount == 0) {
                jdbcTemplate.update("""
                    INSERT INTO candidate_profile (
                        name, gender, age, current_city, target_cities, accept_remote, accept_relocation,
                        job_status, graduate_year, graduate_type, onboard_time, highest_degree, school,
                        major, is_unified, is_full_time, graduation_date, work_years, internship_exp,
                        work_exp, project_exp, freelance_exp, skills, languages, certificates, target_jobs,
                        target_industries, min_salary, expected_salary, accept_outsourcing, accept_sales,
                        accept_tele_sales, accept_shift, accept_overtime, accept_travel, weights_config
                    ) VALUES (
                        ?, ?, ?, ?, ?, ?, ?,
                        ?, ?, ?, ?, ?, ?,
                        ?, ?, ?, ?, ?, ?,
                        ?, ?, ?, ?, ?, ?, ?,
                        ?, ?, ?, ?, ?,
                        ?, ?, ?, ?, ?
                    )
                """,
                    "示例用户", "保密", 0, "示例城市", "[\"示例城市A\", \"示例城市B\"]", 1, 1,
                    "离校应届生/随时到岗", 2026, "应届生", "可尽快到岗", "大专", "示例职业技术学院",
                    "示例专业", 1, 1, "2026-06", 0,
                    "[{\"company\":\"示例公司\",\"title\":\"示例实习岗位\",\"duration\":\"2025.07-2025.10\",\"desc\":\"示例工作描述。\"}]",
                    "[]",
                    "[{\"name\":\"示例项目\",\"role\":\"示例角色\",\"desc\":\"示例项目描述。\"}]",
                    "[]",
                    "[\"示例技能A\", \"示例技能B\", \"示例技能C\"]",
                    "[{\"lang\":\"英语\",\"level\":\"良好\"}]",
                    "[\"示例证书\"]",
                    "[\"示例岗位A\", \"示例岗位B\", \"示例岗位C\"]",
                    "[\"示例行业A\", \"示例行业B\"]",
                    0, 0, 0, 0,
                    0, 0, 1, 0,
                    "{\"degree\":15,\"experience\":15,\"skills\":20,\"direction\":15,\"industry\":10,\"salary\":8,\"city\":5,\"hrActive\":5,\"freshness\":4,\"company\":3}"
                );
                log.info("✓ 已写入候选人画像种子数据");
            }

            // 种子2: 多版本简历
            Integer resumeCount = jdbcTemplate.queryForObject("SELECT count(1) FROM resume_version", Integer.class);
            if (resumeCount == null || resumeCount == 0) {
                jdbcTemplate.update("""
                    INSERT INTO resume_version (resume_name, target_jobs, target_industries, matched_skills, strengths, is_default, version)
                    VALUES (?, ?, ?, ?, ?, ?, ?)
                """,
                    "通用应届综合版", "[\"管培生\", \"运营助理\", \"综合行政\", \"设计助理\"]",
                    "[\"互联网\", \"零售\", \"文化创意\"]",
                    "[\"应届学习能力强\", \"沟通协调\", \"Office/飞书\", \"责任心\"]",
                    "大专应届毕业生，综合学习能力强，工作踏实认真，有自动化与设计双重技能复合背景，可尽快到岗稳定实习。",
                    1, "v1.0"
                );
                jdbcTemplate.update("""
                    INSERT INTO resume_version (resume_name, target_jobs, target_industries, matched_skills, strengths, is_default, version)
                    VALUES (?, ?, ?, ?, ?, ?, ?)
                """,
                    "AI应用与自动化版", "[\"AI运营\", \"AIGC应用\", \"自动化实施\", \"Prompt工程师\", \"技术支持\"]",
                    "[\"人工智能\", \"软件/信息技术\", \"智能制造\"]",
                    "[\"Prompt Engineering\", \"Playwright自动化\", \"Agent工作流\", \"OpenAI API\", \"Python\", \"系统仿真\"]",
                    "熟练调用大模型API与智能体搭建，独立完成过多平台自动化工具，日常深度使用AI提效工具，动手能力极强。",
                    0, "v1.0"
                );
                jdbcTemplate.update("""
                    INSERT INTO resume_version (resume_name, target_jobs, target_industries, matched_skills, strengths, is_default, version)
                    VALUES (?, ?, ?, ?, ?, ?, ?)
                """,
                    "陈列与视觉设计版", "[\"陈列设计\", \"视觉陈列\", \"VM\", \"空间设计\", \"美工/视觉企划\"]",
                    "[\"时尚/零售\", \"服装\", \"文化创意\", \"展览展示\"]",
                    "[\"SketchUp\", \"AutoCAD\", \"Photoshop\", \"Illustrator\", \"InDesign\", \"卖场动线\", \"橱窗陈列\"]",
                    "示例专业科班背景，精通示例工具与相关软件，已准备好完整作品集可随时审阅。",
                    0, "v1.0"
                );
                log.info("✓ 已写入多版本简历种子数据");
            }

            // 种子3: 投递策略配置
            Integer strategyCount = jdbcTemplate.queryForObject("SELECT count(1) FROM agent_strategy_config", Integer.class);
            if (strategyCount == null || strategyCount == 0) {
                jdbcTemplate.update("""
                    INSERT INTO agent_strategy_config (run_mode, auto_apply_threshold, review_min_threshold, max_jobs_per_company_per_day, dedup_days, enable_ai_greeting, enable_risk_block)
                    VALUES ('BALANCED', 78, 60, 2, 30, 1, 1)
                """);
                log.info("✓ 已写入默认 Agent 投递策略配置 (平衡模式: 78分自动投 / 60-77分人工复核 / 同公司每天最多2岗)");
            }

            // 种子4: 关键词体系 (Primary / Secondary / Negative)
            Integer kwCount = jdbcTemplate.queryForObject("SELECT count(1) FROM keyword_management", Integer.class);
            if (kwCount == null || kwCount == 0) {
                String[][] defaultKws = new String[][]{
                    {"PRIMARY", "AI方向", "AI运营", "20"},
                    {"PRIMARY", "AI方向", "AI应用", "20"},
                    {"PRIMARY", "AI方向", "自动化", "15"},
                    {"PRIMARY", "设计方向", "陈列设计", "20"},
                    {"PRIMARY", "设计方向", "视觉陈列", "20"},
                    {"PRIMARY", "综合方向", "管培生", "15"},
                    {"SECONDARY", "技术加分", "ChatGPT", "10"},
                    {"SECONDARY", "技术加分", "大模型", "10"},
                    {"SECONDARY", "技术加分", "AIGC", "10"},
                    {"SECONDARY", "设计加分", "SketchUp", "10"},
                    {"SECONDARY", "设计加分", "AutoCAD", "10"},
                    {"NEGATIVE", "黑名单词", "电话销售", "-40"},
                    {"NEGATIVE", "黑名单词", "保险代理", "-40"},
                    {"NEGATIVE", "黑名单词", "地推", "-30"},
                    {"NEGATIVE", "黑名单词", "纯佣金", "-40"},
                    {"NEGATIVE", "黑名单词", "网络主播", "-40"},
                    {"NEGATIVE", "黑名单词", "贷款培训", "-50"}
                };
                for (String[] kw : defaultKws) {
                    jdbcTemplate.update(
                        "INSERT INTO keyword_management (category, group_name, word, weight, is_active) VALUES (?, ?, ?, ?, 1)",
                        kw[0], kw[1], kw[2], Integer.parseInt(kw[3])
                    );
                }
                log.info("✓ 已写入默认关键词与排除词矩阵");
            }

        } catch (Exception e) {
            log.warn("初始化 Agent 种子数据异常: {}", e.getMessage());
        }
    }

    private void addColumnIfMissing(String table, String column, String type) {
        try {
            var columns = jdbcTemplate.queryForList(
                    "SELECT name FROM pragma_table_info('" + table + "')", String.class);
            if (columns.stream().noneMatch(c -> c.equalsIgnoreCase(column))) {
                jdbcTemplate.execute("ALTER TABLE " + table + " ADD COLUMN " + column + " " + type);
                log.info("数据库迁移完成：{}.{} 已添加", table, column);
            }
        } catch (Exception e) {
            log.warn("数据库迁移检查失败（{}.{})：{}", table, column, e.getMessage());
        }
    }
}
