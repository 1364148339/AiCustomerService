package com.aimacrodroid.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
@Builder
@Schema(description = "设备拉取任务返回体")
public class DeviceTaskVO {
    @Schema(description = "任务ID")
    private String id;
    @Schema(description = "场景Key")
    private String scenarioKey;
    @Schema(description = "场景名称")
    private String scenarioName;
    @Schema(description = "任务轨道")
    private String track;
    @Schema(description = "步骤列表")
    private List<Map<String, Object>> steps;
    @Schema(description = "兼容字段，映射为steps")
    private List<Map<String, Object>> commands;
    @Schema(description = "约束配置")
    private Map<String, Object> constraints;
    @Schema(description = "可观测配置")
    private Map<String, Object> observability;
    @Schema(description = "优先级")
    private Integer priority;
}
