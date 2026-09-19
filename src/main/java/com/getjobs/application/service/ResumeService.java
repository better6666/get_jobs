package com.getjobs.application.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.getjobs.application.entity.ResumeVersionEntity;
import com.getjobs.application.mapper.ResumeVersionMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ResumeService {

    private final ResumeVersionMapper resumeMapper;

    public List<ResumeVersionEntity> listAllResumes() {
        return resumeMapper.selectList(new QueryWrapper<ResumeVersionEntity>().orderByDesc("is_default").orderByDesc("id"));
    }

    public ResumeVersionEntity getResumeById(Long id) {
        return resumeMapper.selectById(id);
    }

    public ResumeVersionEntity getDefaultResume() {
        var list = resumeMapper.selectList(new QueryWrapper<ResumeVersionEntity>().eq("is_default", 1).last("LIMIT 1"));
        if (list != null && !list.isEmpty()) {
            return list.get(0);
        }
        var all = listAllResumes();
        return (all != null && !all.isEmpty()) ? all.get(0) : null;
    }

    /**
     * 智能选择最适合目标岗位的简历版本
     */
    public ResumeVersionEntity selectBestResume(JobParserService.ParsedJob job) {
        List<ResumeVersionEntity> all = listAllResumes();
        if (all == null || all.isEmpty()) return null;

        String targetText = (job.getJobTitle() + " " + job.getIndustry() + " " + job.getJobDescription()).toLowerCase();

        ResumeVersionEntity best = null;
        int maxMatch = -1;

        for (ResumeVersionEntity r : all) {
            int score = 0;
            String targetJobs = r.getTargetJobs() != null ? r.getTargetJobs().toLowerCase() : "";
            String skills = r.getMatchedSkills() != null ? r.getMatchedSkills().toLowerCase() : "";

            // 检查目标岗位关键词重合
            for (String part : targetJobs.replace("[", "").replace("]", "").replace("\"", "").split(",")) {
                String p = part.trim();
                if (!p.isEmpty() && targetText.contains(p)) {
                    score += 30;
                }
            }
            // 检查技能重合
            for (String part : skills.replace("[", "").replace("]", "").replace("\"", "").split(",")) {
                String p = part.trim();
                if (!p.isEmpty() && targetText.contains(p)) {
                    score += 15;
                }
            }
            if (Integer.valueOf(1).equals(r.getIsDefault())) {
                score += 5; // 默认版本加5分底分
            }

            if (score > maxMatch) {
                maxMatch = score;
                best = r;
            }
        }

        return best != null ? best : getDefaultResume();
    }

    @Transactional
    public ResumeVersionEntity saveOrUpdate(ResumeVersionEntity entity) {
        if (Integer.valueOf(1).equals(entity.getIsDefault())) {
            // 清除其它默认
            resumeMapper.update(null,
                    new com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper<ResumeVersionEntity>()
                            .set("is_default", 0));
        }

        if (entity.getId() == null) {
            entity.setCreatedAt(LocalDateTime.now());
            entity.setUpdatedAt(LocalDateTime.now());
            resumeMapper.insert(entity);
        } else {
            entity.setUpdatedAt(LocalDateTime.now());
            resumeMapper.updateById(entity);
        }
        return entity;
    }

    @Transactional
    public boolean delete(Long id) {
        return resumeMapper.deleteById(id) > 0;
    }
}
