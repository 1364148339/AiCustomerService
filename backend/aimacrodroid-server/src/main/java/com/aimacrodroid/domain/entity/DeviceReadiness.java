package com.aimacrodroid.domain.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 设备就绪状态变更历史表
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "amd_device_readiness")
public class DeviceReadiness extends BaseEntity {

    /**
     * 设备唯一标识
     */
    private String deviceId;

    /**
     * Shizuku是否运行中(0:否 1:是)
     */
    private Integer shizukuRunning;

    /**
     * 悬浮窗权限是否授予(0:否 1:是)
     */
    private Integer overlayGranted;

    /**
     * 键盘是否启用(0:否 1:是)
     */
    private Integer keyboardEnabled;

    /**
     * 最后激活方式(wireless/adb/root)
     */
    private String lastActivationMethod;

    /**
     * 状态变更原因或事件源
     */
    private String changeReason;
}
