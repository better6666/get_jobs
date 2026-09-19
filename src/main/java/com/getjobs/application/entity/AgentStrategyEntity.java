package com.getjobs.application.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("agent_strategy_config")
public class AgentStrategyEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String runMode;                    // SAFE / BALANCED / AGGRESSIVE
    private Integer autoApplyThreshold;        // 默认 78
    private Integer reviewMinThreshold;        // 默认 60
    private Integer maxJobsPerCompanyPerDay;   // 默认 2
    private Integer dedupDays;                 // 默认 30
    private Integer enableAiGreeting;          // 默认 1
    private Integer enableRiskBlock;           // 默认 1
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
