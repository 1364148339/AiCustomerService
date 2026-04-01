package com.aimacrodroid.service.impl;

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
                .eq(Alert::getStatus, "OPEN")
                .orderByDesc(Alert::getId)
                .last("LIMIT 1");
        Alert existing = this.getOne(query, false);
        LocalDateTime now = LocalDateTime.now();
        if (existing == null) {
            Alert alert = new Alert();
            alert.setAlertNo("AL-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16).toUpperCase());
            alert.setTaskId(taskId);
            alert.setRunId(runId);
            alert.setLevel(level);
            alert.setAlertType(alertType);
            alert.setErrorCode(alertKey);
            alert.setStatus("OPEN");
            alert.setFirstOccurAt(now);
            alert.setLastOccurAt(now);
            alert.setDetail(detail);
            this.save(alert);
            return;
        }
        existing.setLastOccurAt(now);
        if (detail != null) {
            existing.setDetail(detail);
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
}
