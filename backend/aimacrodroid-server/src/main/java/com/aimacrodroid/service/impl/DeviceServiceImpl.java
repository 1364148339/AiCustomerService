package com.aimacrodroid.service.impl;

import com.aimacrodroid.domain.dto.DeviceHeartbeatReqDTO;
import com.aimacrodroid.domain.dto.DeviceRegisterReqDTO;
import com.aimacrodroid.domain.entity.Device;
import com.aimacrodroid.domain.entity.DeviceReadiness;
import com.aimacrodroid.domain.vo.DeviceReadinessVO;
import com.aimacrodroid.domain.vo.DeviceRegisterVO;
import com.aimacrodroid.domain.vo.DeviceStatusVO;
import com.aimacrodroid.mapper.DeviceMapper;
import com.aimacrodroid.service.DeviceReadinessService;
import com.aimacrodroid.service.DeviceService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class DeviceServiceImpl extends ServiceImpl<DeviceMapper, Device> implements DeviceService {

    private final DeviceReadinessService deviceReadinessService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DeviceRegisterVO register(DeviceRegisterReqDTO req) {
        // 1. 查询设备是否已存在
        LambdaQueryWrapper<Device> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(Device::getDeviceId, req.getDeviceId());
        Device existingDevice = this.getOne(queryWrapper);

        String token;
        if (existingDevice == null) {
            // 2. 不存在则创建新设备
            Device newDevice = new Device();
            newDevice.setDeviceId(req.getDeviceId());
            newDevice.setBrand(req.getBrand());
            newDevice.setModel(req.getModel());
            newDevice.setAndroidVersion(req.getAndroidVersion());
            newDevice.setResolution(req.getResolution());
            newDevice.setShizukuAvailable(req.getShizukuAvailable());
            newDevice.setOverlayGranted(req.getOverlayGranted());
            newDevice.setKeyboardEnabled(req.getKeyboardEnabled());
            newDevice.setSseSupported(req.getSseSupported());
            newDevice.setCapabilities(req.getCapabilities());
            newDevice.setStatus("ONLINE");

            // 生成鉴权 Token
            token = UUID.randomUUID().toString().replace("-", "");
            newDevice.setToken(token);

            this.save(newDevice);
            @Override
    public DeviceStatusVO getDeviceStatus(String deviceId) {
        LambdaQueryWrapper<Device> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(Device::getDeviceId, deviceId);
        Device device = this.getOne(queryWrapper);
        
        if (device == null) {
            throw new RuntimeException("设备不存在");
        }

        // 判断设备是否离线 (例如心跳超过 3 分钟未上报)
        String status = device.getStatus();
        if ("ONLINE".equals(status) && device.getLastHeartbeatTime() != null) {
            if (device.getLastHeartbeatTime().plusMinutes(3).isBefore(LocalDateTime.now())) {
                status = "OFFLINE";
                device.setStatus("OFFLINE");
                this.updateById(device); // 懒更新状态
            }
        }

        return DeviceStatusVO.builder()
                .status(status)
                .lastHeartbeatTime(device.getLastHeartbeatTime())
                .foregroundPkg(device.getForegroundPkg())
                .capabilities(device.getCapabilities())
                .shizukuAvailable(device.getShizukuAvailable())
                .overlayGranted(device.getOverlayGranted())
                .keyboardEnabled(device.getKeyboardEnabled())
                .build();
    }

    @Override
    public DeviceReadinessVO getDeviceReadiness(String deviceId) {
        LambdaQueryWrapper<DeviceReadiness> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(DeviceReadiness::getDeviceId, deviceId)
                .orderByDesc(DeviceReadiness::getGmtCreate)
                .last("LIMIT 1");
        
        DeviceReadiness readiness = deviceReadinessService.getOne(queryWrapper);
        if (readiness == null) {
            // 如果没有流水，降级返回设备主表中的当前状态
            LambdaQueryWrapper<Device> dQuery = new LambdaQueryWrapper<>();
            dQuery.eq(Device::getDeviceId, deviceId);
            Device device = this.getOne(dQuery);
            if (device == null) {
                throw new RuntimeException("设备不存在");
            }
            return DeviceReadinessVO.builder()
                    .shizukuRunning(device.getShizukuAvailable())
                    .overlayGranted(device.getOverlayGranted())
                    .keyboardEnabled(device.getKeyboardEnabled())
                    .lastActivationMethod("unknown")
                    .build();
        }

        return DeviceReadinessVO.builder()
                .shizukuRunning(readiness.getShizukuRunning())
                .overlayGranted(readiness.getOverlayGranted())
                .keyboardEnabled(readiness.getKeyboardEnabled())
                .lastActivationMethod(readiness.getLastActivationMethod())
                .build();
    }

} else {
            // 3. 存在则更新设备信息和状态
            existingDevice.setBrand(req.getBrand());
            existingDevice.setModel(req.getModel());
            existingDevice.setAndroidVersion(req.getAndroidVersion());
            existingDevice.setResolution(req.getResolution());
            existingDevice.setShizukuAvailable(req.getShizukuAvailable());
            existingDevice.setOverlayGranted(req.getOverlayGranted());
            existingDevice.setKeyboardEnabled(req.getKeyboardEnabled());
            existingDevice.setSseSupported(req.getSseSupported());
            existingDevice.setCapabilities(req.getCapabilities());
            existingDevice.setStatus("ONLINE");
            
            // 可以选择重新生成 Token，或者复用旧 Token。Phase 1 先复用。
            token = existingDevice.getToken();

            this.updateById(existingDevice);
        }

        return DeviceRegisterVO.builder()
                .registered(true)
                .token(token)
                .build();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void heartbeat(DeviceHeartbeatReqDTO req) {
        LambdaQueryWrapper<Device> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(Device::getDeviceId, req.getDeviceId());
        Device device = this.getOne(queryWrapper);

        if (device == null) {
            log.warn("收到未注册设备的心跳，deviceId: {}", req.getDeviceId());
            throw new RuntimeException("设备未注册");
        }

        // 记录就绪状态变更流水
        checkAndRecordReadinessChange(device, req);

        // 更新设备心跳及状态信息
        device.setForegroundPkg(req.getForegroundPkg());
        device.setBatteryPct(req.getBatteryPct());
        device.setNetworkType(req.getNetworkType());
        device.setIsCharging(req.getCharging() != null ? req.getCharging() : 0);
        if (req.getShizukuAvailable() != null) device.setShizukuAvailable(req.getShizukuAvailable());
        if (req.getOverlayGranted() != null) device.setOverlayGranted(req.getOverlayGranted());
        if (req.getKeyboardEnabled() != null) device.setKeyboardEnabled(req.getKeyboardEnabled());
        if (req.getSseSupported() != null) device.setSseSupported(req.getSseSupported());
        if (req.getCapabilities() != null) device.setCapabilities(req.getCapabilities());
        
        device.setLastHeartbeatTime(LocalDateTime.now());
        device.setStatus("ONLINE");

        this.updateById(device);
    }

    private void checkAndRecordReadinessChange(Device oldDevice, DeviceHeartbeatReqDTO req) {
        boolean changed = false;
        
        Integer newShizuku = req.getShizukuAvailable() != null ? req.getShizukuAvailable() : oldDevice.getShizukuAvailable();
        Integer newOverlay = req.getOverlayGranted() != null ? req.getOverlayGranted() : oldDevice.getOverlayGranted();
        Integer newKeyboard = req.getKeyboardEnabled() != null ? req.getKeyboardEnabled() : oldDevice.getKeyboardEnabled();

        if (!Objects.equals(oldDevice.getShizukuAvailable(), newShizuku) ||
            !Objects.equals(oldDevice.getOverlayGranted(), newOverlay) ||
            !Objects.equals(oldDevice.getKeyboardEnabled(), newKeyboard)) {
            changed = true;
        }

        if (changed) {
            DeviceReadiness readiness = new DeviceReadiness();
            readiness.setDeviceId(oldDevice.getDeviceId());
            readiness.setShizukuRunning(newShizuku);
            readiness.setOverlayGranted(newOverlay);
            readiness.setKeyboardEnabled(newKeyboard);
            readiness.setChangeReason("Heartbeat status change");
            deviceReadinessService.save(readiness);
            log.info("设备[{}]就绪状态发生变更, 记录流水", oldDevice.getDeviceId());
        }
    }

    @Override
    public DeviceStatusVO getDeviceStatus(String deviceId) {
        LambdaQueryWrapper<Device> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(Device::getDeviceId, deviceId);
        Device device = this.getOne(queryWrapper);
        
        if (device == null) {
            throw new RuntimeException("设备不存在");
        }

        // 判断设备是否离线 (例如心跳超过 3 分钟未上报)
        String status = device.getStatus();
        if ("ONLINE".equals(status) && device.getLastHeartbeatTime() != null) {
            if (device.getLastHeartbeatTime().plusMinutes(3).isBefore(LocalDateTime.now())) {
                status = "OFFLINE";
                device.setStatus("OFFLINE");
                this.updateById(device); // 懒更新状态
            }
        }

        return DeviceStatusVO.builder()
                .status(status)
                .lastHeartbeatTime(device.getLastHeartbeatTime())
                .foregroundPkg(device.getForegroundPkg())
                .capabilities(device.getCapabilities())
                .shizukuAvailable(device.getShizukuAvailable())
                .overlayGranted(device.getOverlayGranted())
                .keyboardEnabled(device.getKeyboardEnabled())
                .build();
    }

    @Override
    public DeviceReadinessVO getDeviceReadiness(String deviceId) {
        LambdaQueryWrapper<DeviceReadiness> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(DeviceReadiness::getDeviceId, deviceId)
                .orderByDesc(DeviceReadiness::getGmtCreate)
                .last("LIMIT 1");
        
        DeviceReadiness readiness = deviceReadinessService.getOne(queryWrapper);
        if (readiness == null) {
            // 如果没有流水，降级返回设备主表中的当前状态
            LambdaQueryWrapper<Device> dQuery = new LambdaQueryWrapper<>();
            dQuery.eq(Device::getDeviceId, deviceId);
            Device device = this.getOne(dQuery);
            if (device == null) {
                throw new RuntimeException("设备不存在");
            }
            return DeviceReadinessVO.builder()
                    .shizukuRunning(device.getShizukuAvailable())
                    .overlayGranted(device.getOverlayGranted())
                    .keyboardEnabled(device.getKeyboardEnabled())
                    .lastActivationMethod("unknown")
                    .build();
        }

        return DeviceReadinessVO.builder()
                .shizukuRunning(readiness.getShizukuRunning())
                .overlayGranted(readiness.getOverlayGranted())
                .keyboardEnabled(readiness.getKeyboardEnabled())
                .lastActivationMethod(readiness.getLastActivationMethod())
                .build();
    }
}
