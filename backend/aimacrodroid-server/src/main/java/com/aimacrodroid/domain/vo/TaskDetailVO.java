package com.aimacrodroid.domain.vo;

import com.aimacrodroid.domain.entity.CommandInstance;
import com.aimacrodroid.domain.entity.Task;
import com.aimacrodroid.domain.entity.TaskDeviceRun;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
@Schema(description = "任务详情响应结果")
public class TaskDetailVO {

    @Schema(description = "任务基本信息")
    private Task task;

    @Schema(description = "各设备的执行状态")
    private List<TaskDeviceRun> deviceRuns;

    @Schema(description = "原子指令序列(仅ATOMIC轨道返回)")
    private List<CommandInstance> commands;
}
