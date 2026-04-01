package com.aimacrodroid.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@Schema(description = "设备注册响应结果")
public class DeviceRegisterVO {

    @Schema(description = "是否注册成功", example = "true")
    private Boolean registered;

    @Schema(description = "设备鉴权Token", example = "Bearer abcdefg...")
    private String token;
}
