package com.aimacrodroid.domain.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 设备就绪状态变更历史表
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "t_device_readiness")
public class DeviceReadiness extends BaseEntity {

    /**
     * 设备唯一标识
     */
    private Long deviceId;

    private String foregroundPkg;

    private Integer batteryPct;

    private String networkType;

    private Integer isCharging;

    /**
     * Shizuku是否运行中(0:否 1:是)
     */
    @TableField("is_shizuku_available")
    private Integer shizukuRunning;

    /**
     * 悬浮窗权限是否授予(0:否 1:是)
     */
    @TableField("is_overlay_granted")
    private Integer overlayGranted;

    /**
     * 键盘是否启用(0:否 1:是)
     */
    @TableField("is_keyboard_enabled")
    private Integer keyboardEnabled;

    @TableField("is_sse_supported")
    private Integer sseSupported;

    /**
     * 最后激活方式(wireless/adb/root)
     */
    private LocalDateTime heartbeatAt;
}
