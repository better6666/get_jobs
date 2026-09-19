package com.getjobs.application.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("keyword_management")
public class KeywordEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String category;      // PRIMARY / SECONDARY / NEGATIVE
    private String groupName;     // 分组名
    private String word;          // 关键词
    private Integer weight;       // 权重
    private Integer isActive;     // 1:启用, 0:停用
    private String contextRule;   // 上下文反误伤规则
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
