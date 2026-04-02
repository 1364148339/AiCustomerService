package com.aimacrodroid.domain.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.Map;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "t_audit_log", autoResultMap = true)
public class AuditLog extends BaseEntity {
    private String traceId;

    private String operatorId;

    private String operatorRole;

    private String actionCode;

    private String bizType;

    private String bizId;

    private String requestIp;

    @TableField(value = "request_payload", typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> requestPayload;

    private String resultCode;

    private String resultMessage;
}
