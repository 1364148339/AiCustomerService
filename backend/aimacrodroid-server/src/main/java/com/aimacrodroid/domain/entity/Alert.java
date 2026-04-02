package com.aimacrodroid.domain.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;
import java.util.Map;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "t_alert", autoResultMap = true)
public class Alert extends BaseEntity {
    private String alertNo;

    private Long taskId;

    private Long runId;

    @TableField("step_instance_id")
    private Long stepId;

    @TableField("alert_level")
    private String level;

    private String alertType;

    @TableField("alert_status")
    private String status;

    private String errorCode;

    private LocalDateTime firstOccurAt;

    private LocalDateTime lastOccurAt;

    @TableField(value = "detail_json", typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> detail;

    private String closeReason;
}
