package com.aimacrodroid.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@Schema(description = "设备就绪状态响应结果")
public class DeviceReadinessVO {

    @Schema(description = "Shizuku是否运行中(0:否 1:是)")
    private Integer shizukuRunning;

    @Schema(description = "悬浮窗权限是否授予(0:否 1:是)")
    private Integer overlayGranted;

    @Schema(description = "键盘是否启用(0:否 1:是)")
    private Integer keyboardEnabled;

    @Schema(description = "最后激活方式(wireless/adb/root)")
    private String lastActivationMethod;
}
