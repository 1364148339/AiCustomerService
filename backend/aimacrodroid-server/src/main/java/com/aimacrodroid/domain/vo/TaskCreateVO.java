package com.aimacrodroid.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@Schema(description = "创建任务响应结果")
public class TaskCreateVO {

    @Schema(description = "全局任务ID")
    private Long taskId;

    @Schema(description = "全局任务业务编号")
    private String taskNo;

    @Schema(description = "任务状态", example = "QUEUED")
    private String status;

    @Schema(description = "场景唯一标识")
    private String scenarioKey;

    @Schema(description = "场景名称")
    private String scenarioName;
}
