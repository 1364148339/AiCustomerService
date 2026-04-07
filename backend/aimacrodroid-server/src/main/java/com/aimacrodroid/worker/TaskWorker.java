package com.aimacrodroid.worker;

import com.aimacrodroid.domain.entity.Device;
import com.aimacrodroid.domain.entity.RunEvent;
import com.aimacrodroid.domain.entity.RunEventType;
import com.aimacrodroid.domain.entity.StepInstance;
import com.aimacrodroid.domain.entity.Task;
import com.aimacrodroid.domain.entity.TaskDeviceRun;
import com.aimacrodroid.mapper.DeviceMapper;
import com.aimacrodroid.mapper.RunEventMapper;
import com.aimacrodroid.mapper.StepInstanceMapper;
import com.aimacrodroid.mapper.TaskDeviceRunMapper;
import com.aimacrodroid.mapper.TaskMapper;
import com.aimacrodroid.service.AlertService;
import com.aimacrodroid.service.AuditLogService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class TaskWorker {
    private final TaskMapper taskMapper;
    private final TaskDeviceRunMapper taskDeviceRunMapper;
    private final StepInstanceMapper stepInstanceMapper;
    private final RunEventMapper runEventMapper;
    private final DeviceMapper deviceMapper;
    private final AlertService alertService;
    private final AuditLogService auditLogService;
    private final WorkerDispatchProperties dispatchProperties;
    private final WorkerStepProperties stepProperties;

    @Scheduled(fixedDelayString = "${task.dispatch.poll-interval-ms:1000}")
    @Transactional(rollbackFor = Exception.class)
    public void consumeQueue() {
        LambdaQueryWrapper<Task> taskQuery = new LambdaQueryWrapper<>();
        taskQuery.in(Task::getStatus, List.of("QUEUED", "DISPATCHING", "RUNNING"))
                .orderByDesc(Task::getPriority)
                .orderByAsc(Task::getGmtCreate)
                .last("LIMIT " + dispatchProperties.getBatchSize());
        List<Task> tasks = taskMapper.selectList(taskQuery);
        for (Task task : tasks) {
            dispatchTaskRuns(task);
        }
    }

    @Scheduled(fixedDelayString = "${task.dispatch.poll-interval-ms:1000}")
    @Transactional(rollbackFor = Exception.class)
    public void scanTimeoutRuns() {
        LambdaQueryWrapper<TaskDeviceRun> query = new LambdaQueryWrapper<>();
        query.eq(TaskDeviceRun::getRunStatus, "RUNNING")
                .orderByAsc(TaskDeviceRun::getGmtModified)
                .last("LIMIT " + (dispatchProperties.getBatchSize() * 5));
        List<TaskDeviceRun> runningRuns = taskDeviceRunMapper.selectList(query);
        for (TaskDeviceRun run : runningRuns) {
            if (isTimedOut(run)) {
                markRunTimeout(run);
            }
        }
    }

    @Scheduled(fixedDelay = 180000)
    @Transactional(rollbackFor = Exception.class)
    public void scanOfflineDevices() {
        LocalDateTime threshold = LocalDateTime.now().minusMinutes(3);
        LambdaUpdateWrapper<Device> update = new LambdaUpdateWrapper<>();
        update.eq(Device::getDeviceStatus, "ONLINE")
                .isNotNull(Device::getLastSeenAt)
                .lt(Device::getLastSeenAt, threshold)
                .set(Device::getDeviceStatus, "OFFLINE");
        int updated = deviceMapper.update(null, update);
        if (updated > 0) {
            log.info("设备离线扫描完成，标记离线数量={}", updated);
        }
    }

    private void dispatchTaskRuns(Task task) {
        if ("CANCELED".equals(task.getStatus()) || "SUCCESS".equals(task.getStatus()) || "FAIL".equals(task.getStatus())) {
            return;
        }
        LambdaQueryWrapper<TaskDeviceRun> runQuery = new LambdaQueryWrapper<>();
        runQuery.eq(TaskDeviceRun::getTaskId, task.getId())
                .eq(TaskDeviceRun::getRunStatus, "PENDING")
                .orderByAsc(TaskDeviceRun::getGmtCreate);
        List<TaskDeviceRun> pendingRuns = taskDeviceRunMapper.selectList(runQuery);
        if (!pendingRuns.isEmpty() && "QUEUED".equals(task.getStatus())) {
            task.setStatus("DISPATCHING");
            taskMapper.updateById(task);
            HashMap<String, Object> detail = new HashMap<>();
            detail.put("taskId", task.getId());
            detail.put("pendingCount", pendingRuns.size());
            auditLogService.record("worker", "TASK_DISPATCH_READY", "TASK", String.valueOf(task.getId()), "SUCCESS", detail);
        }
    }

    private boolean isTimedOut(TaskDeviceRun run) {
        Task task = taskMapper.selectById(run.getTaskId());
        if (task == null) {
            return false;
        }
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime runBaseTime = run.getStartedAt() == null ? run.getGmtCreate() : run.getStartedAt();
        int stepTimeoutMs = resolveStepTimeout(run.getTaskId(), run.getCurrentStepNo());
        if (runBaseTime != null && runBaseTime.plusNanos(stepTimeoutMs * 1_000_000L).isBefore(now)) {
            return true;
        }
        Long deadlineMs = extractDeadlineMs(task.getTaskConstraints());
        if (deadlineMs != null && deadlineMs > 0) {
            LocalDateTime taskBaseTime = task.getStartedAt() == null ? task.getGmtCreate() : task.getStartedAt();
            return taskBaseTime != null && taskBaseTime.plusNanos(deadlineMs * 1_000_000L).isBefore(now);
        }
        return false;
    }

    private void markRunTimeout(TaskDeviceRun run) {
        int retryMax = resolveStepRetryMax(run.getTaskId(), run.getCurrentStepNo());
        int retryCount = run.getRetryCount() == null ? 0 : run.getRetryCount();
        if (retryCount < retryMax) {
            int retryBackoffMs = resolveStepRetryBackoffMs(run.getTaskId(), run.getCurrentStepNo());
            LocalDateTime nextStartedAt = LocalDateTime.now().plusNanos(retryBackoffMs * 1_000_000L);
            LambdaUpdateWrapper<TaskDeviceRun> retryUpdate = new LambdaUpdateWrapper<>();
            retryUpdate.eq(TaskDeviceRun::getId, run.getId())
                    .eq(TaskDeviceRun::getRunStatus, "RUNNING")
                    .set(TaskDeviceRun::getRetryCount, retryCount + 1)
                    .set(TaskDeviceRun::getErrorCode, null)
                    .set(TaskDeviceRun::getErrorMessage, null)
                    .set(TaskDeviceRun::getStartedAt, nextStartedAt);
            int retryUpdated = taskDeviceRunMapper.update(null, retryUpdate);
            if (retryUpdated < 1) {
                return;
            }
            RunEvent retryEvent = new RunEvent();
            retryEvent.setEventNo(workerEventNo("RETRY"));
            retryEvent.setTaskId(run.getTaskId());
            retryEvent.setRunId(run.getId());
            retryEvent.setStepInstanceId(resolveCurrentStepId(run.getTaskId(), run.getCurrentStepNo()));
            retryEvent.setEventStatus("RUNNING");
            retryEvent.setEventType(RunEventType.UNKNOWN.name());
            retryEvent.setEventTypeDesc(RunEventType.UNKNOWN.getZhDesc());
            retryEvent.setOccurredAt(LocalDateTime.now());
            retryEvent.setErrorCode("STEP_RETRYING");
            retryEvent.setErrorMessage("步骤超时重试");
            retryEvent.setIsSensitiveScreen(0);
            runEventMapper.insert(retryEvent);
            Map<String, Object> retryDetail = new HashMap<>();
            retryDetail.put("runId", run.getId());
            retryDetail.put("taskId", run.getTaskId());
            retryDetail.put("deviceId", run.getDeviceId());
            retryDetail.put("stepNo", run.getCurrentStepNo());
            retryDetail.put("retryCount", retryCount + 1);
            retryDetail.put("retryMax", retryMax);
            retryDetail.put("backoffMs", retryBackoffMs);
            auditLogService.record("worker", "RUN_RETRY_BACKOFF", "TASK_DEVICE_RUN", String.valueOf(run.getId()), "SUCCESS", retryDetail);
            return;
        }
        LocalDateTime finishedAt = LocalDateTime.now();
        LambdaUpdateWrapper<TaskDeviceRun> failUpdate = new LambdaUpdateWrapper<>();
        failUpdate.eq(TaskDeviceRun::getId, run.getId())
                .eq(TaskDeviceRun::getRunStatus, "RUNNING")
                .set(TaskDeviceRun::getRunStatus, "FAIL")
                .set(TaskDeviceRun::getErrorCode, "STEP_EXEC_TIMEOUT")
                .set(TaskDeviceRun::getErrorMessage, "执行超时")
                .set(TaskDeviceRun::getFinishedAt, finishedAt);
        int failUpdated = taskDeviceRunMapper.update(null, failUpdate);
        if (failUpdated < 1) {
            return;
        }
        RunEvent event = new RunEvent();
        event.setEventNo(workerEventNo("TIMEOUT"));
        event.setTaskId(run.getTaskId());
        event.setRunId(run.getId());
        event.setStepInstanceId(resolveCurrentStepId(run.getTaskId(), run.getCurrentStepNo()));
        event.setEventStatus("FAIL");
        event.setEventType(RunEventType.FAILED.name());
        event.setEventTypeDesc(RunEventType.FAILED.getZhDesc());
        event.setOccurredAt(LocalDateTime.now());
        event.setErrorCode("STEP_EXEC_TIMEOUT");
        event.setErrorMessage("执行超时");
        event.setIsSensitiveScreen(0);
        runEventMapper.insert(event);
        Map<String, Object> detail = new HashMap<>();
        detail.put("runId", run.getId());
        detail.put("taskId", run.getTaskId());
        detail.put("deviceId", run.getDeviceId());
        detail.put("stepNo", run.getCurrentStepNo());
        detail.put("errorCode", "STEP_EXEC_TIMEOUT");
        alertService.upsertEventAlert(run.getTaskId(), String.valueOf(run.getDeviceId()), run.getId(), "HIGH", "TIMEOUT", "STEP_EXEC_TIMEOUT", detail);
        auditLogService.record("worker", "RUN_TIMEOUT", "TASK_DEVICE_RUN", String.valueOf(run.getId()), "SUCCESS", detail);
        refreshTaskStatus(run.getTaskId());
        log.warn("worker超时扫描触发失败 runId={} taskId={} deviceId={}", run.getId(), run.getTaskId(), run.getDeviceId());
    }

    private void refreshTaskStatus(Long taskId) {
        Task task = taskMapper.selectById(taskId);
        if (task == null) {
            return;
        }
        LambdaQueryWrapper<TaskDeviceRun> query = new LambdaQueryWrapper<>();
        query.eq(TaskDeviceRun::getTaskId, taskId);
        List<TaskDeviceRun> runs = taskDeviceRunMapper.selectList(query);
        int total = runs.size();
        int success = 0;
        int fail = 0;
        int running = 0;
        for (TaskDeviceRun run : runs) {
            if ("SUCCESS".equals(run.getRunStatus())) {
                success++;
            } else if ("FAIL".equals(run.getRunStatus())) {
                fail++;
            } else if ("RUNNING".equals(run.getRunStatus())) {
                running++;
            }
        }
        task.setTotalDeviceCount(total);
        task.setSuccessDeviceCount(success);
        task.setFailDeviceCount(fail);
        if (fail > 0) {
            task.setStatus("FAIL");
            task.setFinishedAt(LocalDateTime.now());
        } else if (success == total && total > 0) {
            task.setStatus("SUCCESS");
            task.setFinishedAt(LocalDateTime.now());
        } else if (running > 0) {
            task.setStatus("RUNNING");
        } else {
            task.setStatus("DISPATCHING");
        }
        taskMapper.updateById(task);
    }

    private int resolveStepTimeout(Long taskId, Integer currentStepNo) {
        Integer stepNo = currentStepNo == null || currentStepNo <= 0 ? 1 : currentStepNo;
        LambdaQueryWrapper<StepInstance> query = new LambdaQueryWrapper<>();
        query.eq(StepInstance::getTaskId, taskId)
                .eq(StepInstance::getStepNo, stepNo)
                .last("LIMIT 1");
        StepInstance step = stepInstanceMapper.selectOne(query);
        if (step == null || step.getTimeoutMs() == null || step.getTimeoutMs() <= 0) {
            return stepProperties.getDefaultTimeoutMs();
        }
        return step.getTimeoutMs();
    }

    private int resolveStepRetryMax(Long taskId, Integer currentStepNo) {
        StepInstance step = resolveStep(taskId, currentStepNo);
        if (step == null || step.getRetryMax() == null || step.getRetryMax() < 0) {
            return stepProperties.getMaxRetryDefault();
        }
        return step.getRetryMax();
    }

    private int resolveStepRetryBackoffMs(Long taskId, Integer currentStepNo) {
        StepInstance step = resolveStep(taskId, currentStepNo);
        if (step == null || step.getRetryBackoffMs() == null || step.getRetryBackoffMs() < 0) {
            return stepProperties.getDefaultBackoffMs();
        }
        return step.getRetryBackoffMs();
    }

    private StepInstance resolveStep(Long taskId, Integer currentStepNo) {
        Integer stepNo = currentStepNo == null || currentStepNo <= 0 ? 1 : currentStepNo;
        LambdaQueryWrapper<StepInstance> query = new LambdaQueryWrapper<>();
        query.eq(StepInstance::getTaskId, taskId)
                .eq(StepInstance::getStepNo, stepNo)
                .last("LIMIT 1");
        return stepInstanceMapper.selectOne(query);
    }

    private Long resolveCurrentStepId(Long taskId, Integer currentStepNo) {
        Integer stepNo = currentStepNo == null || currentStepNo <= 0 ? 1 : currentStepNo;
        LambdaQueryWrapper<StepInstance> query = new LambdaQueryWrapper<>();
        query.eq(StepInstance::getTaskId, taskId)
                .eq(StepInstance::getStepNo, stepNo)
                .last("LIMIT 1");
        StepInstance step = stepInstanceMapper.selectOne(query);
        return step == null ? null : step.getId();
    }

    private Long extractDeadlineMs(Map<String, Object> constraints) {
        if (constraints == null || !constraints.containsKey("deadlineMs")) {
            return null;
        }
        Object value = constraints.get("deadlineMs");
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        if (value instanceof String) {
            try {
                return Long.parseLong((String) value);
            } catch (Exception ignored) {
                return null;
            }
        }
        return null;
    }

    private String workerEventNo(String prefix) {
        return "WK-" + prefix + "-" + UUID.randomUUID().toString().replace("-", "").substring(0, 20).toUpperCase();
    }
}
