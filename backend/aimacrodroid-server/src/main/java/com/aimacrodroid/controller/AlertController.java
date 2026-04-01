package com.aimacrodroid.controller;

import com.aimacrodroid.common.api.Result;
import com.aimacrodroid.domain.entity.Alert;
import com.aimacrodroid.service.AlertService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "告警管理", description = "任务执行告警查询接口")
@RestController
@RequestMapping("/api/alerts")
@RequiredArgsConstructor
public class AlertController {
    private final AlertService alertService;

    @Operation(summary = "按任务查询告警")
    @GetMapping
    public Result<List<Alert>> queryAlerts(@RequestParam(value = "taskId", required = false) Long taskId) {
        return Result.success(alertService.queryAlerts(taskId));
    }
}
