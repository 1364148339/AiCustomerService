package com.aimacrodroid.domain.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 设备信息与能力表
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "t_device", autoResultMap = true)
public class Device extends BaseEntity {

    /**
     * 设备唯一标识(如Android ID/MAC等)
     */
    @TableField("device_code")
    private String deviceId;

    /**
     * 设备品牌
     */
    private String brand;

    /**
     * 设备型号
     */
    private String model;

    /**
     * 安卓版本号(如7.0)
     */
    private String androidVersion;

    /**
     * 屏幕分辨率(如1080x1920)
     */
    private String resolution;

    /**
     * Shizuku是否可用(0:否 1:是)
     */
    @TableField(exist = false)
    private Integer shizukuAvailable;

    /**
     * 悬浮窗权限是否授予(0:否 1:是)
     */
    @TableField(exist = false)
    private Integer overlayGranted;

    /**
     * 键盘是否启用(0:否 1:是)
     */
    @TableField(exist = false)
    private Integer keyboardEnabled;

    /**
     * 是否支持SSE流式响应(0:否 1:是)
     */
    @TableField(exist = false)
    private Integer sseSupported;

    /**
     * 设备能力集列表
     */
    @TableField(value = "capability_json", typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> capabilities;

    /**
     * 当前前台包名
     */
    @TableField(exist = false)
    private String foregroundPkg;

    /**
     * 电量百分比(0-100)
     */
    @TableField(exist = false)
    private Integer batteryPct;

    /**
     * 网络类型(WIFI/5G/4G等)
     */
    @TableField(exist = false)
    private String networkType;

    /**
     * 是否在充电(0:否 1:是)
     */
    @TableField(exist = false)
    private Integer isCharging;

    /**
     * 最近心跳时间
     */
    @TableField("last_seen_at")
    private LocalDateTime lastHeartbeatTime;

    /**
     * 设备鉴权Token(用于HMAC验签)
     */
    @TableField("token_hash")
    private String token;

    /**
     * 设备状态(ONLINE/OFFLINE)
     */
    @TableField("device_status")
    private String status;
}
