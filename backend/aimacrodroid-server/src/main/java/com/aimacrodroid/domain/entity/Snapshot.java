package com.aimacrodroid.domain.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;
import java.util.Map;

/**
 * 截图与页面结构快照存证表
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "amd_snapshot", autoResultMap = true)
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
     * 冗余查询字段: 设备标识
     */
    private String deviceId;

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
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<Map<String, Object>> elements;

    /**
     * 快照在设备端的生成时间戳
     */
    private Long snapshotTimestamp;
}
