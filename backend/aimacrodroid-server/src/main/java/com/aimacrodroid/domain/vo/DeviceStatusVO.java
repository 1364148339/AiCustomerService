package com.aimacrodroid.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

@Data
@Builder
@Schema(description = "设备状态信息响应结果")
public class DeviceStatusVO {

    @Schema(description = "在线状态 (ONLINE/OFFLINE)")
    private String status;

    @Schema(description = "最近心跳时间")
    private LocalDateTime lastHeartbeatTime;

    @Schema(description = "当前前台包名")
    private String foregroundPkg;

    @Schema(description = "设备能力集列表")
    private Map<String, Object> capabilities;

    @Schema(description = "Shizuku是否可用(0:否 1:是)")
    private Integer shizukuAvailable;

    @Schema(description = "悬浮窗权限是否授予(0:否 1:是)")
    private Integer overlayGranted;

    @Schema(description = "键盘是否启用(0:否 1:是)")
    private Integer keyboardEnabled;
}
