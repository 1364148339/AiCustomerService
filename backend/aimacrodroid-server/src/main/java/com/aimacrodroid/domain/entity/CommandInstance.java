package com.aimacrodroid.domain.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.Map;
import org.apache.ibatis.type.JdbcType;

/**
 * 原子任务指令序列表
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "amd_command_instance", autoResultMap = true)
public class CommandInstance extends BaseEntity {

    /**
     * 归属任务ID
     */
    private Long taskId;

    /**
     * 指令业务标识(如c_open)
     */
    private String commandId;

    /**
     * 动作类型(如find_and_tap, open_app)
     */
    private String action;

    /**
     * 动作核心参数(target, pkg等)
     */
    @TableField(typeHandler = JacksonTypeHandler.class, jdbcType = JdbcType.OTHER)
    private Map<String, Object> params;

    /**
     * 指令级重试策略
     */
    @TableField(typeHandler = JacksonTypeHandler.class, jdbcType = JdbcType.OTHER)
    private Map<String, Object> retryPolicy;

    /**
     * 幂等控制键
     */
    private String idempotentKey;

    /**
     * 执行顺序(从小到大)
     */
    private Integer orderNum;
}
