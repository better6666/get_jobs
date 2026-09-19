package com.getjobs.application.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("application_record")
public class ApplicationRecordEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String applicationId;
    private String platform;
    private String jobId;
    private String jobTitle;
    private String company;
    private String companyScale;
    private String industry;
    private String salary;
    private String location;
    private String jobUrl;

    // 匹配与评分
    private Integer matchScore;
    private String scoreBreakdown;     // JSON 详细扣分
    private String strengths;          // JSON 优势
    private String risks;              // JSON 风险
    private String riskLevel;          // LOW / MEDIUM / HIGH
    private String matchLevel;         // Priority / High / Normal / Review / Skip

    // 投递详情
    private Long resumeId;
    private String resumeName;
    private String greetingUsed;

    // 全生命周期状态
    private String status;             // DISCOVERED / FILTERED / REVIEW_PENDING / APPLIED / HR_READ / HR_REPLIED / INTERVIEW_INVITED / INTERVIEWED / OFFER / REJECTED / CLOSED
    private LocalDateTime applyTime;
    private String hrReply;
    private LocalDateTime hrReplyTime;
    private String interviewStatus;
    private LocalDateTime interviewTime;
    private String notes;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
