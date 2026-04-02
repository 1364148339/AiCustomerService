package com.aimacrodroid.domain.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.Map;
import org.apache.ibatis.type.JdbcType;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "t_scenario_step", autoResultMap = true)
public class ScenarioStep extends BaseEntity {

    private Long scenarioId;

    private Integer stepNo;

    private String stepName;

    private String actionCode;

    @TableField(typeHandler = JacksonTypeHandler.class, jdbcType = JdbcType.OTHER)
    private Map<String, Object> actionParams;

    private Integer timeoutMs;

    private Integer retryMax;

    private Integer retryBackoffMs;

    private Integer isEnabled;
}
