package com.getjobs.application.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("candidate_profile")
public class CandidateProfileEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String name;
    private String gender;
    private Integer age;
    private String currentCity;
    private String targetCities;        // JSON 数组
    private Integer acceptRemote;       // 1:接受, 0:不接受
    private Integer acceptRelocation;   // 1:接受, 0:不接受
    private String jobStatus;
    private Integer graduateYear;
    private String graduateType;        // 应届生 / 往届生 / 社招
    private String onboardTime;

    // 学历
    private String highestDegree;
    private String school;
    private String major;
    private Integer isUnified;          // 是否统招
    private Integer isFullTime;         // 是否全日制
    private String graduationDate;

    // 经历与能力
    private Integer workYears;
    private String internshipExp;       // JSON
    private String workExp;             // JSON
    private String projectExp;          // JSON
    private String freelanceExp;        // JSON
    private String skills;              // JSON 数组
    private String languages;           // JSON
    private String certificates;        // JSON

    // 求职偏好
    private String targetJobs;          // JSON 数组
    private String targetIndustries;    // JSON 数组
    private Integer minSalary;
    private Integer expectedSalary;
    private String preferredScale;      // JSON
    private String preferredCompTypes;  // JSON
    private Integer acceptOutsourcing;  // 是否接受外包
    private Integer acceptDispatch;     // 是否接受派遣
    private Integer acceptSales;        // 是否接受销售
    private Integer acceptTeleSales;    // 是否接受电销
    private Integer acceptShift;        // 是否接受轮班
    private Integer acceptOvertime;     // 是否接受加班
    private Integer acceptTravel;       // 是否接受出差

    // 权重配置 (JSON)
    private String weightsConfig;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
