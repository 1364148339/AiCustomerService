package com.aimacrodroid.service.impl;

import com.aimacrodroid.common.exception.BizException;
import com.aimacrodroid.domain.dto.EventReportReqDTO;
import com.aimacrodroid.domain.entity.Device;
import com.aimacrodroid.domain.entity.RunEvent;
import com.aimacrodroid.domain.entity.Snapshot;
import com.aimacrodroid.domain.entity.Task;
import com.aimacrodroid.domain.entity.TaskDeviceRun;
import com.aimacrodroid.mapper.DeviceMapper;
import com.aimacrodroid.mapper.RunEventMapper;
import com.aimacrodroid.mapper.SnapshotMapper;
import com.aimacrodroid.mapper.TaskMapper;
import com.aimacrodroid.mapper.TaskDeviceRunMapper;
import com.aimacrodroid.security.DeviceSignatureVerifier;
import com.aimacrodroid.service.AlertService;
import com.aimacrodroid.service.AuditLogService;
import com.aimacrodroid.service.RunEventService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class RunEventServiceImpl extends ServiceImpl<RunEventMapper, RunEvent> implements RunEventService {

    private final SnapshotMapper snapshotMapper;
    private final TaskDeviceRunMapper taskDeviceRunMapper;
    private final TaskMapper taskMapper;
    private final DeviceMapper deviceMapper;
    private final AlertService alertService;
    private final AuditLogService auditLogService;
    private final DeviceSignatureVerifier deviceSignatureVerifier;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void reportEvent(String deviceId, String rawToken, EventReportReqDTO req) {
        Device device = getDeviceOrThrow(deviceId);
        try {
            deviceSignatureVerifier.verify(deviceId, rawToken, device.getTokenHash(), req);
        } catch (BizException ex) {
            if ("SIGNATURE_INVALID".equals(ex.getCode()) || "SIGNATURE_EXPIRED".equals(ex.getCode())) {
                HashMap<String, Object> detail = new HashMap<>();
                detail.put("deviceId", deviceId);
                detail.put("taskId", req.getTaskId());
                detail.put("eventNo", req.getEventNo());
                detail.put("errorCode", ex.getCode());
                detail.put("message", ex.getMessage());
                auditLogService.record(deviceId, "EVENT_SIGNATURE_VERIFY", "RUN_EVENT", req.getEventNo(), "FAIL", detail);
            }
            throw ex;
        }
        if (isDuplicateEvent(req)) {
            return;
        }
        TaskDeviceRun run = getRunOrThrow(req.getTaskId(), device.getId());
        RunEvent event = new RunEvent();
        event.setEventNo(req.getEventNo());
        event.setTaskId(req.getTaskId());
        event.setRunId(run.getId());
        event.setStepInstanceId(req.getStepId());
        event.setCommandId(req.getCommandId());
        event.setEventStatus(req.getStatus());
        event.setOccurredAt(toTime(req.getTimestamp()));
        event.setDurationMs(req.getDurationMs());
        event.setErrorCode(req.getErrorCode());
        event.setErrorMessage(req.getErrorMessage());
        event.setTraceJson(req.getTrace());
        event.setThinkingText(req.getThinking());
        event.setIsSensitiveScreen(Boolean.TRUE.equals(req.getSensitiveScreenDetected()) ? 1 : 0);
        event.setProgressJson(req.getProgress());
        event.setScreenshotUrl(req.getScreenshotUrl());
        event.setHmacSignature(req.getHmac());
        this.save(event);

        if (req.getScreenshotUrl() != null || req.getElements() != null) {
            Snapshot snapshot = new Snapshot();
            snapshot.setEventId(event.getId());
            snapshot.setTaskId(req.getTaskId());
            snapshot.setRunId(run.getId());
            snapshot.setScreenshotUrl(req.getScreenshotUrl() != null ? req.getScreenshotUrl() : "");
            snapshot.setForegroundPkg(req.getForegroundPkg());
            snapshot.setElementJson(req.getElements());
            snapshot.setCapturedAt(toTime(req.getTimestamp()));
            snapshotMapper.insert(snapshot);
        }

        boolean needUpdate = false;
        if (canTransit(run.getRunStatus(), req.getStatus())) {
            run.setRunStatus(req.getStatus());
            if ("RUNNING".equals(req.getStatus()) && run.getStartedAt() == null) {
                run.setStartedAt(LocalDateTime.now());
            }
            if ("SUCCESS".equals(req.getStatus()) || "FAIL".equals(req.getStatus())) {
                run.setFinishedAt(LocalDateTime.now());
            }
            needUpdate = true;
        } else if (!req.getStatus().equals(run.getRunStatus())) {
            throw new BizException("INVALID_PARAM", "设备运行状态流转不合法");
        }
        if (req.getErrorCode() != null) {
            run.setErrorCode(req.getErrorCode());
            run.setErrorMessage(req.getErrorMessage());
            needUpdate = true;
        }
        if (needUpdate) {
            taskDeviceRunMapper.updateById(run);
        }
        refreshTaskStatus(req.getTaskId());
        if ("FAIL".equals(req.getStatus()) || StringUtils.hasText(req.getErrorCode()) || Boolean.TRUE.equals(req.getSensitiveScreenDetected())) {
            HashMap<String, Object> detail = new HashMap<>();
            detail.put("eventNo", req.getEventNo());
            detail.put("status", req.getStatus());
            detail.put("errorCode", req.getErrorCode());
            detail.put("errorMessage", req.getErrorMessage());
            detail.put("stepId", req.getStepId());
            detail.put("timestamp", req.getTimestamp());
            String alertType = "FAIL";
            String alertKey = StringUtils.hasText(req.getErrorCode()) ? req.getErrorCode() : req.getStatus();
            if (Boolean.TRUE.equals(req.getSensitiveScreenDetected())) {
                alertType = "RETRY_EXCEEDED";
                alertKey = "SENSITIVE_SCREEN";
            }
            alertService.upsertEventAlert(req.getTaskId(), deviceId, run.getId(), "HIGH", alertType, alertKey, detail);
        }
        auditLogService.record(deviceId, "EVENT_REPORT", "RUN_EVENT", String.valueOf(event.getId()), "SUCCESS", new HashMap<>());
        log.info("接收到设备 {} 的任务 {} 回传事件: {}", deviceId, req.getTaskId(), req.getStatus());
    }

    @Override
    public List<RunEvent> queryLogs(Long taskId, String deviceId) {
        LambdaQueryWrapper<RunEvent> query = new LambdaQueryWrapper<>();
        if (taskId != null) {
            query.eq(RunEvent::getTaskId, taskId);
        }
        if (StringUtils.hasText(deviceId)) {
            Device device = getDeviceOrThrow(deviceId);
            LambdaQueryWrapper<TaskDeviceRun> runQuery = new LambdaQueryWrapper<>();
            runQuery.eq(TaskDeviceRun::getDeviceId, device.getId());
            if (taskId != null) {
                runQuery.eq(TaskDeviceRun::getTaskId, taskId);
            }
            List<TaskDeviceRun> runs = taskDeviceRunMapper.selectList(runQuery);
            if (runs.isEmpty()) {
                return Collections.emptyList();
            }
            query.in(RunEvent::getRunId, runs.stream().map(TaskDeviceRun::getId).toList());
        }
        query.orderByDesc(RunEvent::getOccurredAt).orderByDesc(RunEvent::getId);
        List<RunEvent> events = this.list(query);
        if (events.isEmpty()) {
            return events;
        }
        Set<Long> runIds = events.stream().map(RunEvent::getRunId).collect(Collectors.toSet());
        LambdaQueryWrapper<TaskDeviceRun> runBindQuery = new LambdaQueryWrapper<>();
        runBindQuery.in(TaskDeviceRun::getId, runIds);
        List<TaskDeviceRun> runBinds = taskDeviceRunMapper.selectList(runBindQuery);
        if (runBinds.isEmpty()) {
            return events;
        }
        Map<Long, Long> runDeviceMap = runBinds.stream()
                .collect(Collectors.toMap(TaskDeviceRun::getId, TaskDeviceRun::getDeviceId));
        Set<Long> devicePkIds = runBinds.stream().map(TaskDeviceRun::getDeviceId).collect(Collectors.toSet());
        LambdaQueryWrapper<Device> deviceQuery = new LambdaQueryWrapper<>();
        deviceQuery.in(Device::getId, devicePkIds);
        List<Device> devices = deviceMapper.selectList(deviceQuery);
        Map<Long, String> deviceCodeMap = devices.stream()
                .collect(Collectors.toMap(Device::getId, Device::getDeviceCode));
        for (RunEvent event : events) {
            Long devicePkId = runDeviceMap.get(event.getRunId());
            if (devicePkId != null) {
                event.setDeviceId(deviceCodeMap.getOrDefault(devicePkId, String.valueOf(devicePkId)));
            }
            if (event.getOccurredAt() != null) {
                event.setEventTimestamp(event.getOccurredAt().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli());
            }
        }
        return events;
    }

    private Device getDeviceOrThrow(String deviceId) {
        LambdaQueryWrapper<Device> query = new LambdaQueryWrapper<>();
        query.eq(Device::getDeviceCode, deviceId).last("LIMIT 1");
        Device device = deviceMapper.selectOne(query);
        if (device == null) {
            throw new BizException("DEVICE_NOT_FOUND", "设备不存在");
        }
        return device;
    }

    private boolean isDuplicateEvent(EventReportReqDTO req) {
        LambdaQueryWrapper<RunEvent> query = new LambdaQueryWrapper<>();
        query.eq(RunEvent::getEventNo, req.getEventNo())
                .last("LIMIT 1");
        return this.getOne(query, false) != null;
    }

    private TaskDeviceRun getRunOrThrow(Long taskId, Long devicePkId) {
        LambdaQueryWrapper<TaskDeviceRun> runQuery = new LambdaQueryWrapper<>();
        runQuery.eq(TaskDeviceRun::getTaskId, taskId)
                .eq(TaskDeviceRun::getDeviceId, devicePkId)
                .last("LIMIT 1");
        TaskDeviceRun run = taskDeviceRunMapper.selectOne(runQuery);
        if (run == null) {
            throw new BizException("RUN_NOT_FOUND", "设备运行实例不存在");
        }
        return run;
    }

    private boolean canTransit(String current, String target) {
        if (target == null) {
            return false;
        }
        if (target.equals(current)) {
            return true;
        }
        if ("PENDING".equals(current)) {
            return "RUNNING".equals(target) || "SUCCESS".equals(target) || "FAIL".equals(target);
        }
        if ("RUNNING".equals(current)) {
            return "SUCCESS".equals(target) || "FAIL".equals(target) || "RUNNING".equals(target);
        }
        return false;
    }

    private void refreshTaskStatus(Long taskId) {
        Task task = taskMapper.selectById(taskId);
        if (task == null) {
            throw new BizException("TASK_NOT_FOUND", "任务不存在");
        }
        LambdaQueryWrapper<TaskDeviceRun> query = new LambdaQueryWrapper<>();
        query.eq(TaskDeviceRun::getTaskId, taskId);
        List<TaskDeviceRun> runs = taskDeviceRunMapper.selectList(query);
        int total = runs.size();
        int success = 0;
        int fail = 0;
        int running = 0;
        int pending = 0;
        if ("CANCELED".equals(task.getStatus())) {
            return;
        }
        for (TaskDeviceRun run : runs) {
            if ("SUCCESS".equals(run.getRunStatus())) {
                success++;
            } else if ("FAIL".equals(run.getRunStatus())) {
                fail++;
            } else if ("RUNNING".equals(run.getRunStatus())) {
                running++;
            } else {
                pending++;
            }
        }
        task.setTotalDeviceCount(total);
        task.setSuccessDeviceCount(success);
        task.setFailDeviceCount(fail);
        if (task.getStartedAt() == null && pending < total) {
            task.setStartedAt(LocalDateTime.now());
        }
        if (fail > 0) {
            task.setStatus("FAIL");
            task.setFinishedAt(LocalDateTime.now());
        } else if (success == total && total > 0) {
            task.setStatus("SUCCESS");
            task.setFinishedAt(LocalDateTime.now());
        } else if (running > 0) {
            task.setStatus("RUNNING");
        } else if (pending < total) {
            task.setStatus("DISPATCHING");
        } else if ("QUEUED".equals(task.getStatus()) && task.getGmtCreate() != null && task.getGmtCreate().toEpochSecond(ZoneOffset.UTC) < LocalDateTime.now().minusSeconds(1).toEpochSecond(ZoneOffset.UTC)) {
            task.setStatus("DISPATCHING");
        }
        taskMapper.updateById(task);
    }

    private LocalDateTime toTime(Long timestampMs) {
        if (timestampMs == null) {
            return LocalDateTime.now();
        }
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(timestampMs), java.time.ZoneId.systemDefault());
    }
}
