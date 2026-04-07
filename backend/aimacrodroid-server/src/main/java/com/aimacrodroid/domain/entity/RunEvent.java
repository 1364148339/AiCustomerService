package com.aimacrodroid.domain.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;
import java.util.Map;
import org.apache.ibatis.type.JdbcType;

/**
 * 任务执行事件时间线流表
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "t_run_event", autoResultMap = true)
public class RunEvent extends BaseEntity {
    private String eventNo;

    private Long runId;

    /**
     * 任务ID
     */
    private Long taskId;

    /**
     * 步骤ID
     */
    private Long stepInstanceId;

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
    private String eventStatus;

    /**
     * 事件类型(参考设备端 TaskEventType)
     */
    private String eventType;

    /**
     * 事件类型中文解释
     */
    private String eventTypeDesc;

    /**
     * 设备端实际上报时间戳(毫秒)
     */
    @TableField(exist = false)
    private Long eventTimestamp;

    @TableField(exist = false)
    private Integer stepNo;

    @TableField(exist = false)
    private String stepName;

    @TableField(exist = false)
    private String resultDesc;

    @TableField(exist = false)
    private String stageDesc;

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
    @TableField(typeHandler = JacksonTypeHandler.class, jdbcType = JdbcType.OTHER)
    private List<Map<String, Object>> traceJson;

    /**
     * 大模型意图思考过程摘要
     */
    private String thinkingText;

    /**
     * 是否触发敏感页面保护(0:否 1:是)
     */
    private Integer isSensitiveScreen;

    /**
     * 本节点的进度数据快照
     */
    @TableField(typeHandler = JacksonTypeHandler.class, jdbcType = JdbcType.OTHER)
    private Map<String, Object> progressJson;

    private String failureCategory;

    private Boolean recoverable;

    private String actionResult;

    private String pageType;

    private String pageSignature;

    private Boolean targetResolved;

    private String screenshotUrl;

    private java.time.LocalDateTime occurredAt;

    /**
     * 防篡改签名(校验上报防伪造)
     */
    @TableField(exist = false)
    private String hmacSignature;
}
