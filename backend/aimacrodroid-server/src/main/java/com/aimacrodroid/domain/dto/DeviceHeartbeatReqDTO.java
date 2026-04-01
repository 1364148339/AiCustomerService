package com.aimacrodroid.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.Map;

@Data
@Schema(description = "设备心跳上报请求参数")
public class DeviceHeartbeatReqDTO {

    @NotBlank(message = "设备唯一标识不能为空")
    @Schema(description = "设备唯一标识", example = "dev-01")
    private String deviceId;

    @Schema(description = "当前前台包名", example = "com.tencent.mm")
    private String foregroundPkg;

    @Schema(description = "电量百分比(0-100)", example = "85")
    private Integer batteryPct;

    @Schema(description = "网络类型(WIFI/5G/4G)", example = "WIFI")
    private String networkType;

    @Schema(description = "是否在充电(0:否 1:是)", example = "1")
    private Integer charging;

    @Schema(description = "Shizuku是否可用(0:否 1:是)", example = "1")
    private Integer shizukuAvailable;

    @Schema(description = "悬浮窗权限是否授予(0:否 1:是)", example = "1")
    private Integer overlayGranted;

    @Schema(description = "键盘是否启用(0:否 1:是)", example = "1")
    private Integer keyboardEnabled;

    @Schema(description = "是否支持SSE流式响应(0:否 1:是)", example = "1")
    private Integer sseSupported;

    @Schema(description = "设备能力集列表更新")
    private Map<String, Object> capabilities;
}
