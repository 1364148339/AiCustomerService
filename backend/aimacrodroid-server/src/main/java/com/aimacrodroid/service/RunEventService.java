package com.aimacrodroid.service;

import com.aimacrodroid.domain.dto.EventReportReqDTO;
import com.aimacrodroid.domain.entity.RunEvent;
import com.baomidou.mybatisplus.extension.service.IService;

public interface RunEventService extends IService<RunEvent> {

    /**
     * 接收并处理设备上报的执行流水事件
     */
    void reportEvent(String deviceId, EventReportReqDTO req);
}
