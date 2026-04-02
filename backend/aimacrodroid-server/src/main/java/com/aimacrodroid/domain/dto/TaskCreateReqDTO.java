package com.aimacrodroid.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
@Schema(description = "创建任务请求参数")
public class TaskCreateReqDTO {

    @NotBlank(message = "场景Key不能为空")
    @Schema(description = "场景唯一标识")
    private String scenarioKey;

    @NotEmpty(message = "目标设备列表不能为空")
    @Schema(description = "目标设备ID列表")
    private List<String> devices;

    @Schema(description = "任务约束")
    private Map<String, Object> constraints;

    @Schema(description = "可观测性配置")
    private Map<String, Object> observability;

    @NotNull(message = "优先级不能为空")
    @Schema(description = "优先级", example = "5")
    private Integer priority = 5;
}
