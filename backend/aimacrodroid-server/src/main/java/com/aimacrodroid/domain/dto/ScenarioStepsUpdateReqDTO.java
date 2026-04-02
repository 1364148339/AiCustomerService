package com.aimacrodroid.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

@Data
@Schema(description = "更新场景步骤请求")
public class ScenarioStepsUpdateReqDTO {

    @Valid
    @NotEmpty(message = "步骤列表不能为空")
    @Schema(description = "步骤列表")
    private List<ScenarioStepItemDTO> steps;
}
