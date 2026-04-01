package com.aimacrodroid.domain.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;
import java.util.Map;

/**
 * 任务执行事件时间线流表
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "t_run_event", autoResultMap = true)
public class RunEvent extends BaseEntity {
    private String eventNo;

    @TableField("run_id")
    private Long runId;

    /**
     * 任务ID
     */
    private Long taskId;

    /**
     * 步骤ID
     */
    @TableField("step_instance_id")
    private Long stepId;

    /**
     * 设备标识
     */
    @TableField(exist = false)
    private String deviceId;

    /**
     * 指令业务ID(非原子轨道为空)
     */
    @TableField(exist = false)
    private String commandId;

    /**
     * 事件节点状态(RUNNING/SUCCESS/FAIL)
     */
    @TableField("event_status")
    private String status;

    /**
     * 设备端实际上报时间戳(毫秒)
     */
    @TableField(exist = false)
    private Long eventTimestamp;

    /**
     * 本步骤耗时(毫秒)
     */
    private Long durationMs;

    /**
     * 本节点发生的错误码
     */
    private String errorCode;

    private String errorMessage;

    /**
     * 轨迹数据摘要
     */
    @TableField(value = "trace_json", typeHandler = JacksonTypeHandler.class)
    private List<Map<String, Object>> trace;

    /**
     * 大模型意图思考过程摘要
     */
    @TableField("thinking_text")
    private String thinking;

    /**
     * 是否触发敏感页面保护(0:否 1:是)
     */
    @TableField("is_sensitive_screen")
    private Integer sensitiveScreenDetected;

    /**
     * 本节点的进度数据快照
     */
    @TableField(value = "progress_json", typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> progress;

    private String screenshotUrl;

    private java.time.LocalDateTime occurredAt;

    /**
     * 防篡改签名(校验上报防伪造)
     */
    @TableField(exist = false)
    private String hmacSignature;
}
