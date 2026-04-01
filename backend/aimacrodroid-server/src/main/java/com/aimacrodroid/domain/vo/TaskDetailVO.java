package com.aimacrodroid.domain.vo;

import com.aimacrodroid.domain.entity.StepInstance;
import com.aimacrodroid.domain.entity.Task;
import com.aimacrodroid.domain.entity.TaskDeviceRun;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
@Builder
@Schema(description = "任务详情响应结果")
public class TaskDetailVO {

    @Schema(description = "任务基本信息")
    private Task task;

    @Schema(description = "各设备的执行状态")
    private List<TaskDeviceRun> deviceRuns;

    @Schema(description = "步骤快照列表")
    private List<StepInstance> stepInstances;

    @Schema(description = "设备运行聚合统计")
    private Map<String, Integer> stats;
}
