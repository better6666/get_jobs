package com.getjobs.application.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("resume_version")
public class ResumeVersionEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String resumeName;
    private String targetJobs;          // JSON 数组
    private String targetIndustries;    // JSON 数组
    private String matchedSkills;       // JSON 数组
    private String strengths;           // 简历亮点描述
    private String filePath;            // 文件路径
    private Integer isDefault;          // 1:默认, 0:非默认
    private String version;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public String getVersionName() {
        return resumeName != null ? resumeName : version;
    }

    public String getHighlights() {
        return strengths != null ? strengths : "";
    }
}
