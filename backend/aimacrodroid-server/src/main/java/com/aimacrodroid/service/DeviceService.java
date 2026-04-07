package com.aimacrodroid.service;

import com.aimacrodroid.domain.dto.DeviceAliasUpdateReqDTO;
import com.aimacrodroid.domain.dto.DeviceHeartbeatReqDTO;
import com.aimacrodroid.domain.dto.DeviceRegisterReqDTO;
import com.aimacrodroid.domain.entity.Device;
import com.aimacrodroid.domain.vo.DeviceReadinessVO;
import com.aimacrodroid.domain.vo.DeviceRegisterVO;
import com.aimacrodroid.domain.vo.DeviceStatusVO;
import com.aimacrodroid.domain.vo.DeviceTaskVO;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;

public interface DeviceService extends IService<Device> {

    /**
     * 设备注册
     */
    DeviceRegisterVO register(DeviceRegisterReqDTO req);

    /**
     * 设备心跳上报
     */
    void heartbeat(DeviceHeartbeatReqDTO req);

    List<Device> listDevices();

    void updateAlias(String deviceId, DeviceAliasUpdateReqDTO req);

    /**
     * 获取设备状态
     */
    DeviceStatusVO getDeviceStatus(String deviceId);

    /**
     * 获取设备就绪状态
     */
    DeviceReadinessVO getDeviceReadiness(String deviceId);

    List<DeviceTaskVO> pullPendingTasks(String deviceId);
}
