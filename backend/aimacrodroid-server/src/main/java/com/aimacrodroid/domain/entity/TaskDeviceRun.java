package com.aimacrodroid.domain.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.Map;

/**
 * 任务与设备维度的执行状态表
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "amd_task_device_run", autoResultMap = true)
public class TaskDeviceRun extends BaseEntity {

    /**
     * 任务ID
     */
    private Long taskId;

    /**
     * 设备标识
     */
    private String deviceId;

    /**
     * 设备任务状态(PENDING/RUNNING/SUCCESS/FAIL)
     */
    private String status;

    /**
     * 执行进度快照(watchedDurationMs, itemsCompleted等)
     */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> progress;

    /**
     * 错误码(如SHIZUKU_NOT_RUNNING)
     */
    private String errorCode;

    /**
     * 错误详细信息
     */
    private String errorMessage;

    /**
     * 当前重试次数
     */
    private Integer retryCount;
}
