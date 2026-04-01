package com.aimacrodroid.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
@Schema(description = "设备注册请求参数")
public class DeviceRegisterReqDTO {

    @NotBlank(message = "设备唯一标识不能为空")
    @Schema(description = "设备唯一标识", example = "dev-01")
    private String deviceId;

    @Schema(description = "设备品牌", example = "Xiaomi")
    private String brand;

    @Schema(description = "设备型号", example = "Mi 10")
    private String model;

    @Schema(description = "安卓版本", example = "10.0")
    private String androidVersion;

    @Schema(description = "屏幕分辨率", example = "1080x2340")
    private String resolution;

    @NotNull(message = "Shizuku可用状态不能为空")
    @Schema(description = "Shizuku是否可用(支持布尔或0/1)", example = "true")
    private Object shizukuAvailable;

    @NotNull(message = "悬浮窗权限状态不能为空")
    @Schema(description = "悬浮窗权限是否授予(支持布尔或0/1)", example = "true")
    private Object overlayGranted;

    @NotNull(message = "键盘启用状态不能为空")
    @Schema(description = "键盘是否启用(支持布尔或0/1)", example = "true")
    private Object keyboardEnabled;

    @Schema(description = "是否支持SSE流式响应(支持布尔或0/1)", example = "true")
    private Object sseSupported;

    @Schema(description = "设备能力集(支持列表或对象)")
    private Object capabilities;
}
