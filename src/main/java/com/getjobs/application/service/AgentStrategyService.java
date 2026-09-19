package com.getjobs.application.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.getjobs.application.entity.AgentStrategyEntity;
import com.getjobs.application.mapper.AgentStrategyMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class AgentStrategyService {

    private final AgentStrategyMapper strategyMapper;

    public AgentStrategyEntity getStrategy() {
        var list = strategyMapper.selectList(new QueryWrapper<AgentStrategyEntity>().orderByDesc("id").last("LIMIT 1"));
        if (list != null && !list.isEmpty()) {
            return list.get(0);
        }
        // Initialize default BALANCED strategy
        AgentStrategyEntity defaultStrategy = new AgentStrategyEntity();
        defaultStrategy.setRunMode("BALANCED");
        defaultStrategy.setAutoApplyThreshold(78);
        defaultStrategy.setReviewMinThreshold(60);
        defaultStrategy.setMaxJobsPerCompanyPerDay(2);
        defaultStrategy.setDedupDays(30);
        defaultStrategy.setEnableAiGreeting(1);
        defaultStrategy.setEnableRiskBlock(1);
        defaultStrategy.setCreatedAt(LocalDateTime.now());
        defaultStrategy.setUpdatedAt(LocalDateTime.now());
        strategyMapper.insert(defaultStrategy);
        return defaultStrategy;
    }

    @Transactional
    public AgentStrategyEntity updateStrategy(AgentStrategyEntity entity) {
        AgentStrategyEntity current = getStrategy();
        if (entity.getRunMode() != null) current.setRunMode(entity.getRunMode());
        if (entity.getAutoApplyThreshold() != null) current.setAutoApplyThreshold(entity.getAutoApplyThreshold());
        if (entity.getReviewMinThreshold() != null) current.setReviewMinThreshold(entity.getReviewMinThreshold());
        if (entity.getMaxJobsPerCompanyPerDay() != null) current.setMaxJobsPerCompanyPerDay(entity.getMaxJobsPerCompanyPerDay());
        if (entity.getDedupDays() != null) current.setDedupDays(entity.getDedupDays());
        if (entity.getEnableAiGreeting() != null) current.setEnableAiGreeting(entity.getEnableAiGreeting());
        if (entity.getEnableRiskBlock() != null) current.setEnableRiskBlock(entity.getEnableRiskBlock());
        current.setUpdatedAt(LocalDateTime.now());
        strategyMapper.updateById(current);
        return current;
    }

    /**
     * 根据模式快捷切换
     */
    @Transactional
    public AgentStrategyEntity switchMode(String mode) {
        AgentStrategyEntity current = getStrategy();
        current.setRunMode(mode);
        if ("SAFE".equalsIgnoreCase(mode)) {
            current.setAutoApplyThreshold(85);
            current.setReviewMinThreshold(65);
            current.setMaxJobsPerCompanyPerDay(1);
        } else if ("AGGRESSIVE".equalsIgnoreCase(mode)) {
            current.setAutoApplyThreshold(70);
            current.setReviewMinThreshold(50);
            current.setMaxJobsPerCompanyPerDay(3);
        } else { // BALANCED
            current.setRunMode("BALANCED");
            current.setAutoApplyThreshold(78);
            current.setReviewMinThreshold(60);
            current.setMaxJobsPerCompanyPerDay(2);
        }
        current.setUpdatedAt(LocalDateTime.now());
        strategyMapper.updateById(current);
        return current;
    }
}
