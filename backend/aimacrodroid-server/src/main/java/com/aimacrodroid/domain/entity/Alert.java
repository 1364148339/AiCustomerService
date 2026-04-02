package com.aimacrodroid.domain.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;
import java.util.Map;
import org.apache.ibatis.type.JdbcType;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "t_alert", autoResultMap = true)
public class Alert extends BaseEntity {
    private String alertNo;

    private Long taskId;

    private Long runId;

    private Long stepInstanceId;

    private String alertLevel;

    private String alertType;

    private String alertStatus;

    private String errorCode;

    private LocalDateTime firstOccurAt;

    private LocalDateTime lastOccurAt;

    @TableField(typeHandler = JacksonTypeHandler.class, jdbcType = JdbcType.OTHER)
    private Map<String, Object> detailJson;

    private String closeReason;
}
