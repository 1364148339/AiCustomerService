package com.aimacrodroid.domain.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 全局下发任务主表
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "t_task", autoResultMap = true)
public class Task extends BaseEntity {

    /**
     * 任务业务编号
     */
    private String taskNo;

    private Long scenarioId;

    private String scenarioKey;

    private String scenarioName;

    /**
     * 优先级(数字越大优先级越高)
     */
    private Integer priority;

    /**
     * 任务状态(QUEUED/DISPATCHING/RUNNING/SUCCESS/FAIL/CANCELED)
     */
    private String status;

    @TableField(value = "task_constraints", typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> constraints;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> observability;

    private Integer totalDeviceCount;

    private Integer successDeviceCount;

    private Integer failDeviceCount;

    private LocalDateTime startedAt;

    private LocalDateTime finishedAt;
}
