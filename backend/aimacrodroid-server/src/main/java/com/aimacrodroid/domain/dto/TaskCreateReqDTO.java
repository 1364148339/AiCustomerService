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

    @NotBlank(message = "任务类型不能为空")
    @Schema(description = "任务类型", example = "CHECKIN")
    private String type;

    @NotBlank(message = "下发轨道不能为空")
    @Schema(description = "下发轨道(ATOMIC/INTENT)", example = "INTENT")
    private String track;

    @NotEmpty(message = "目标设备列表不能为空")
    @Schema(description = "目标设备ID列表")
    private List<String> devices;

    @Schema(description = "意图标识(INTENT轨道必填)", example = "daily_checkin")
    private String intent;

    @Schema(description = "原子指令列表(ATOMIC轨道必填)")
    private List<Map<String, Object>> commands;

    @Schema(description = "任务约束")
    private Map<String, Object> constraints;

    @Schema(description = "成功标准")
    private Map<String, Object> successCriteria;

    @Schema(description = "可观测性配置")
    private Map<String, Object> observability;

    @Schema(description = "安全护栏")
    private Map<String, Object> safetyRails;

    @Schema(description = "执行节奏配置")
    private Map<String, Object> rhythm;

    @Schema(description = "循环配置")
    private Map<String, Object> loop;

    @Schema(description = "任务级重试策略")
    private Map<String, Object> retryPolicy;

    @NotNull(message = "优先级不能为空")
    @Schema(description = "优先级", example = "5")
    private Integer priority = 5;
}
