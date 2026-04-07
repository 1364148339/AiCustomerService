package com.aimacrodroid.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
@Schema(description = "更新设备别名请求")
public class DeviceAliasUpdateReqDTO {

    @NotBlank(message = "设备别名不能为空")
    @Schema(description = "设备别名", example = "前台测试机")
    private String alias;
}
