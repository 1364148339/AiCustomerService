package com.aimacrodroid.controller;

import com.aimacrodroid.common.api.Result;
import com.aimacrodroid.domain.entity.Alert;
import com.aimacrodroid.security.OperatorRole;
import com.aimacrodroid.security.RequireRoles;
import com.aimacrodroid.service.AlertService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "告警管理", description = "任务执行告警查询接口")
@RestController
@RequestMapping("/api/alerts")
@RequiredArgsConstructor
public class AlertController {
    private final AlertService alertService;

    @Operation(summary = "按任务查询告警")
    @GetMapping
    @RequireRoles({OperatorRole.ADMIN, OperatorRole.OPS, OperatorRole.READONLY})
    public Result<List<Alert>> queryAlerts(@RequestParam(value = "taskId", required = false) Long taskId) {
        return Result.success(alertService.queryAlerts(taskId));
    }

    @Operation(summary = "ACK告警")
    @PostMapping("/{alertId}/ack")
    @RequireRoles({OperatorRole.ADMIN, OperatorRole.OPS})
    public Result<Void> ackAlert(@PathVariable("alertId") Long alertId) {
        alertService.ackAlert(alertId);
        return Result.success();
    }

    @Operation(summary = "关闭告警")
    @PostMapping("/{alertId}/close")
    @RequireRoles({OperatorRole.ADMIN, OperatorRole.OPS})
    public Result<Void> closeAlert(@PathVariable("alertId") Long alertId,
                                   @RequestParam(value = "reason", required = false) String reason) {
        alertService.closeAlert(alertId, reason);
        return Result.success();
    }
}
