package com.aimacrodroid.controller;

import com.aimacrodroid.common.api.Result;
import com.aimacrodroid.domain.dto.TaskCreateReqDTO;
import com.aimacrodroid.domain.entity.Task;
import com.aimacrodroid.domain.vo.TaskCreateVO;
import com.aimacrodroid.domain.vo.TaskDetailVO;
import com.aimacrodroid.service.TaskService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "任务调度", description = "任务创建与下发管理接口")
@RestController
@RequestMapping("/api/tasks")
@RequiredArgsConstructor
public class TaskController {

    private final TaskService taskService;

    @Operation(summary = "创建并下发任务", description = "根据原子轨道或意图轨道创建任务，支持并发下发到多个设备")
    @PostMapping
    public Result<TaskCreateVO> createTask(@Validated @RequestBody TaskCreateReqDTO req) {
        return Result.success(taskService.createTask(req));
    }

    @Operation(summary = "获取任务详情", description = "获取任务的基本信息、各设备的子任务进度，以及原子指令序列(仅原子轨道)")
    @GetMapping("/{taskId}")
    public Result<TaskDetailVO> getTaskDetail(@PathVariable("taskId") Long taskId) {
        return Result.success(taskService.getTaskDetail(taskId));
    }

    @Operation(summary = "获取所有任务列表", description = "获取当前所有下发的任务")
    @GetMapping
    public Result<List<Task>> listAll() {
        return Result.success(taskService.list());
    }
}
