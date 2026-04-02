package com.aimacrodroid.service;

import com.aimacrodroid.domain.dto.EventReportReqDTO;
import com.aimacrodroid.domain.entity.RunEvent;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;

public interface RunEventService extends IService<RunEvent> {

    /**
     * 接收并处理设备上报的执行流水事件
     */
    void reportEvent(String deviceId, String rawToken, EventReportReqDTO req);

    List<RunEvent> queryLogs(Long taskId, String deviceId);
}
