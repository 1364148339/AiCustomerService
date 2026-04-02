package com.aimacrodroid.domain.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.type.JdbcType;

/**
 * 截图与页面结构快照存证表
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "t_snapshot", autoResultMap = true)
public class Snapshot extends BaseEntity {

    /**
     * 关联的具体事件ID
     */
    private Long eventId;

    /**
     * 冗余查询字段: 任务ID
     */
    private Long taskId;

    /**
     * 运行实例ID
     */
    private Long runId;

    /**
     * 截图对象存储路径URL
     */
    private String screenshotUrl;

    /**
     * 截图时刻前台包名
     */
    private String foregroundPkg;

    /**
     * OCR/Accessibility树提取的结构化视图数据
     */
    @TableField(typeHandler = JacksonTypeHandler.class, jdbcType = JdbcType.OTHER)
    private List<Map<String, Object>> elementJson;

    /**
     * 快照在设备端的生成时间戳
     */
    private LocalDateTime capturedAt;
}
