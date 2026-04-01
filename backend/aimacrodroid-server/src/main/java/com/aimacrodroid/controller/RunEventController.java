package com.aimacrodroid.controller;

import com.aimacrodroid.common.api.Result;
import com.aimacrodroid.domain.dto.EventReportReqDTO;
import com.aimacrodroid.domain.entity.RunEvent;
import com.aimacrodroid.service.RunEventService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "监控事件", description = "设备端回传的执行流水管理接口")
@RestController
@RequestMapping("/api/devices")
@RequiredArgsConstructor
public class RunEventController {

    private final RunEventService runEventService;

    @Operation(summary = "设备端事件回传", description = "设备端在任务执行关键节点(如截图、点击、失败)时回传事件")
    @PostMapping("/{deviceId}/events")
    public Result<Void> reportEvent(@PathVariable("deviceId") String deviceId, 
                                    @Validated @RequestBody EventReportReqDTO req) {
        runEventService.reportEvent(deviceId, req);
        return Result.success();
    }

    @Operation(summary = "获取所有流水事件", description = "获取设备端回传的所有日志记录(仅作调试)")
    @GetMapping("/events")
    public Result<List<RunEvent>> listAll() {
        return Result.success(runEventService.list());
    }
}
