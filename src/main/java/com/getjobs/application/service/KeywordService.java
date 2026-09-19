package com.getjobs.application.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.getjobs.application.entity.KeywordEntity;
import com.getjobs.application.mapper.KeywordMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class KeywordService {

    private final KeywordMapper keywordMapper;

    public List<KeywordEntity> listKeywords(String category) {
        QueryWrapper<KeywordEntity> query = new QueryWrapper<>();
        if (category != null && !category.trim().isEmpty()) {
            query.eq("category", category.trim().toUpperCase());
        }
        query.orderByAsc("category").orderByDesc("weight").orderByDesc("id");
        return keywordMapper.selectList(query);
    }

    public KeywordEntity getKeywordById(Long id) {
        return keywordMapper.selectById(id);
    }

    @Transactional
    public KeywordEntity saveOrUpdate(KeywordEntity entity) {
        if (entity.getId() == null) {
            entity.setCreatedAt(LocalDateTime.now());
            entity.setUpdatedAt(LocalDateTime.now());
            if (entity.getIsActive() == null) entity.setIsActive(1);
            if (entity.getWeight() == null) entity.setWeight(10);
            keywordMapper.insert(entity);
        } else {
            entity.setUpdatedAt(LocalDateTime.now());
            keywordMapper.updateById(entity);
        }
        return entity;
    }

    @Transactional
    public boolean delete(Long id) {
        return keywordMapper.deleteById(id) > 0;
    }

    @Transactional
    public boolean toggleActive(Long id, Integer isActive) {
        KeywordEntity entity = keywordMapper.selectById(id);
        if (entity != null) {
            entity.setIsActive(isActive);
            entity.setUpdatedAt(LocalDateTime.now());
            return keywordMapper.updateById(entity) > 0;
        }
        return false;
    }
}
