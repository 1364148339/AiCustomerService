package com.aimacrodroid.domain.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.Map;

/**
 * 全局下发任务主表
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "amd_task", autoResultMap = true)
public class Task extends BaseEntity {

    /**
     * 任务业务编号
     */
    private String taskNo;

    /**
     * 任务名称
     */
    private String name;

    /**
     * 任务类型(如CHECKIN/VIDEO_REWARD)
     */
    private String type;

    /**
     * 下发轨道(ATOMIC/INTENT)
     */
    private String trackType;

    /**
     * 优先级(数字越大优先级越高)
     */
    private Integer priority;

    /**
     * 任务状态(QUEUED/DISPATCHING/RUNNING/SUCCESS/FAIL/CANCELED)
     */
    private String status;

    /**
     * 意图标识(INTENT轨道专有, 如daily_checkin)
     */
    private String intent;

    /**
     * 任务约束(deadlineMs, maxRetries等)
     */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> constraints;

    /**
     * 成功标准(包含uiTextContains, evidence等)
     */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> successCriteria;

    /**
     * 可观测性配置(snapshotLevel, logDetail等)
     */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> observability;

    /**
     * 安全护栏(forbidActions, humanApprovalOn等)
     */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> safetyRails;

    /**
     * 执行节奏配置(staySecondsMin等)
     */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> rhythm;

    /**
     * 循环配置(iterations, breakOnAlerts等)
     */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> loopConfig;

    /**
     * 任务级重试策略(maxRetries, backoffMs)
     */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> retryPolicy;
}
