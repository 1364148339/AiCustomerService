package com.aimacrodroid.controller;

import com.aimacrodroid.common.api.Result;
import com.aimacrodroid.domain.dto.DeviceHeartbeatReqDTO;
import com.aimacrodroid.domain.dto.DeviceRegisterReqDTO;
import com.aimacrodroid.domain.entity.Device;
import com.aimacrodroid.domain.vo.DeviceReadinessVO;
import com.aimacrodroid.domain.vo.DeviceRegisterVO;
import com.aimacrodroid.domain.vo.DeviceStatusVO;
import com.aimacrodroid.service.DeviceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "设备管理", description = "设备注册与状态管理接口")
@RestController
@RequestMapping("/api/devices")
@RequiredArgsConstructor
public class DeviceController {

    private final DeviceService deviceService;

    @Operation(summary = "设备注册", description = "设备首次接入或重新接入时调用，分配 Token 用于后续通信鉴权")
    @PostMapping("/register")
    public Result<DeviceRegisterVO> register(@Validated @RequestBody DeviceRegisterReqDTO req) {
        return Result.success(deviceService.register(req));
    }

    @Operation(summary = "设备心跳上报", description = "设备定期上报存活状态、电量、前台包名及能力就绪状态")
    @PostMapping("/heartbeat")
    public Result<Void> heartbeat(@Validated @RequestBody DeviceHeartbeatReqDTO req) {
        deviceService.heartbeat(req);
        return Result.success();
    }

    @Operation(summary = "获取设备状态", description = "获取设备的实时状态、前台包名和最近心跳")
    @GetMapping("/{id}/status")
    public Result<DeviceStatusVO> getStatus(@PathVariable("id") String deviceId) {
        return Result.success(deviceService.getDeviceStatus(deviceId));
    }

    @Operation(summary = "获取设备就绪状态", description = "获取设备的Shizuku/悬浮窗/无障碍键盘是否就绪")
    @GetMapping("/{id}/readiness")
    public Result<DeviceReadinessVO> getReadiness(@PathVariable("id") String deviceId) {
        return Result.success(deviceService.getDeviceReadiness(deviceId));
    }

    @Operation(summary = "获取所有设备列表", description = "获取当前所有已注册的设备")
    @GetMapping
    public Result<List<Device>> listAll() {
        return Result.success(deviceService.list());
    }
}
