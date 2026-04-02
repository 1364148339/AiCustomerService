package com.aimacrodroid.service;

import com.aimacrodroid.domain.entity.Alert;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;
import java.util.Map;

public interface AlertService extends IService<Alert> {
    void upsertEventAlert(Long taskId, String deviceId, Long runId, String level, String alertType, String alertKey, Map<String, Object> detail);

    List<Alert> queryAlerts(Long taskId);

    void ackAlert(Long alertId);

    void closeAlert(Long alertId, String reason);
}
