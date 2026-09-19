package com.getjobs.application.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.getjobs.application.entity.CandidateProfileEntity;
import com.getjobs.application.mapper.CandidateProfileMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class CandidateProfileService {

    private final CandidateProfileMapper profileMapper;

    public CandidateProfileEntity getProfile() {
        var list = profileMapper.selectList(new QueryWrapper<CandidateProfileEntity>().orderByDesc("id"));
        if (list != null && !list.isEmpty()) {
            return list.get(0);
        }
        return null;
    }

    @Transactional
    public CandidateProfileEntity saveOrUpdateProfile(CandidateProfileEntity entity) {
        CandidateProfileEntity existing = getProfile();
        if (existing == null) {
            entity.setCreatedAt(LocalDateTime.now());
            entity.setUpdatedAt(LocalDateTime.now());
            profileMapper.insert(entity);
            log.info("新建候选人画像成功，ID: {}", entity.getId());
            return entity;
        } else {
            entity.setId(existing.getId());
            entity.setUpdatedAt(LocalDateTime.now());
            profileMapper.updateById(entity);
            log.info("更新候选人画像成功，ID: {}", entity.getId());
            return entity;
        }
    }
}
