package com.aimacrodroid.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.Map;

@Data
@Schema(description = "场景步骤项")
public class ScenarioStepItemDTO {

    @NotNull(message = "步骤序号不能为空")
    @Min(value = 1, message = "步骤序号必须大于0")
    @Schema(description = "步骤序号")
    private Integer stepNo;

    @NotBlank(message = "步骤名称不能为空")
    @Schema(description = "步骤名称")
    private String stepName;

    @NotBlank(message = "动作编码不能为空")
    @Schema(description = "动作编码")
    private String actionCode;

    @Schema(description = "动作参数")
    private Map<String, Object> actionParams;

    @Schema(description = "超时时间毫秒")
    private Integer timeoutMs = 5000;

    @Schema(description = "最大重试次数")
    private Integer retryMax = 0;

    @Schema(description = "重试退避毫秒")
    private Integer retryBackoffMs = 1000;

    @Schema(description = "是否启用")
    private Boolean enabled = true;
}
