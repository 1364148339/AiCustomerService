package com.aimacrodroid.service.impl;

import com.aimacrodroid.common.exception.BizException;
import com.aimacrodroid.domain.entity.Alert;
import com.aimacrodroid.mapper.AlertMapper;
import com.aimacrodroid.service.AlertService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class AlertServiceImpl extends ServiceImpl<AlertMapper, Alert> implements AlertService {
    @Override
    public void upsertEventAlert(Long taskId, String deviceId, Long runId, String level, String alertType, String alertKey, Map<String, Object> detail) {
        LambdaQueryWrapper<Alert> query = new LambdaQueryWrapper<>();
        query.eq(Alert::getTaskId, taskId)
                .eq(Alert::getRunId, runId)
                .eq(Alert::getAlertType, alertType)
                .eq(Alert::getErrorCode, alertKey)
                .eq(Alert::getAlertStatus, "OPEN")
                .orderByDesc(Alert::getId)
                .last("LIMIT 1");
        Alert existing = this.getOne(query, false);
        LocalDateTime now = LocalDateTime.now();
        if (existing == null) {
            Alert alert = new Alert();
            alert.setAlertNo("AL-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16).toUpperCase());
            alert.setTaskId(taskId);
            alert.setRunId(runId);
            alert.setAlertLevel(level);
            alert.setAlertType(alertType);
            alert.setErrorCode(alertKey);
            alert.setAlertStatus("OPEN");
            alert.setFirstOccurAt(now);
            alert.setLastOccurAt(now);
            alert.setDetailJson(detail);
            this.save(alert);
            return;
        }
        existing.setLastOccurAt(now);
        if (detail != null) {
            existing.setDetailJson(detail);
        }
        this.updateById(existing);
    }

    @Override
    public List<Alert> queryAlerts(Long taskId) {
        LambdaQueryWrapper<Alert> query = new LambdaQueryWrapper<>();
        if (taskId != null) {
            query.eq(Alert::getTaskId, taskId);
        }
        query.orderByDesc(Alert::getLastOccurAt).orderByDesc(Alert::getId);
        return this.list(query);
    }

    @Override
    public void ackAlert(Long alertId) {
        Alert alert = this.getById(alertId);
        if (alert == null) {
            throw new BizException("INVALID_PARAM", "告警不存在");
        }
        if ("CLOSED".equals(alert.getAlertStatus())) {
            return;
        }
        alert.setAlertStatus("ACK");
        this.updateById(alert);
    }

    @Override
    public void closeAlert(Long alertId, String reason) {
        Alert alert = this.getById(alertId);
        if (alert == null) {
            throw new BizException("INVALID_PARAM", "告警不存在");
        }
        alert.setAlertStatus("CLOSED");
        alert.setCloseReason(reason);
        this.updateById(alert);
    }
}
