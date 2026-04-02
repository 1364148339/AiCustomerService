package com.aimacrodroid.controller;

import com.aimacrodroid.common.api.Result;
import com.aimacrodroid.domain.entity.RunEvent;
import com.aimacrodroid.security.OperatorRole;
import com.aimacrodroid.security.RequireRoles;
import com.aimacrodroid.service.RunEventService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "执行日志", description = "任务执行日志查询接口")
@RestController
@RequestMapping("/api/logs")
@RequiredArgsConstructor
public class LogController {
    private final RunEventService runEventService;

    @Operation(summary = "按任务与设备查询日志")
    @GetMapping
    @RequireRoles({OperatorRole.ADMIN, OperatorRole.OPS, OperatorRole.READONLY})
    public Result<List<RunEvent>> queryLogs(@RequestParam(value = "taskId", required = false) Long taskId,
                                            @RequestParam(value = "deviceId", required = false) String deviceId) {
        return Result.success(runEventService.queryLogs(taskId, deviceId));
    }
}
