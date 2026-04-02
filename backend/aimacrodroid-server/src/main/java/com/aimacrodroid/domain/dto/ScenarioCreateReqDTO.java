package com.aimacrodroid.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
@Schema(description = "创建场景请求参数")
public class ScenarioCreateReqDTO {

    @NotBlank(message = "场景名称不能为空")
    @Schema(description = "场景名称")
    private String scenarioName;

    @NotBlank(message = "场景Key不能为空")
    @Schema(description = "场景唯一标识")
    private String scenarioKey;

    @Schema(description = "场景描述")
    private String description;
}
