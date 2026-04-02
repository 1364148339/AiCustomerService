package com.aimacrodroid.worker;

import com.aimacrodroid.domain.entity.RunEvent;
import com.aimacrodroid.domain.entity.StepInstance;
import com.aimacrodroid.domain.entity.Task;
import com.aimacrodroid.domain.entity.TaskDeviceRun;
import com.aimacrodroid.mapper.RunEventMapper;
import com.aimacrodroid.mapper.StepInstanceMapper;
import com.aimacrodroid.mapper.TaskDeviceRunMapper;
import com.aimacrodroid.mapper.TaskMapper;
import com.aimacrodroid.service.AlertService;
import com.aimacrodroid.service.AuditLogService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
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
        query.eq(TaskDeviceRun::getStatus, "RUNNING")
                .orderByAsc(TaskDeviceRun::getGmtModified)
                .last("LIMIT " + (dispatchProperties.getBatchSize() * 5));
        List<TaskDeviceRun> runningRuns = taskDeviceRunMapper.selectList(query);
        for (TaskDeviceRun run : runningRuns) {
            if (isTimedOut(run)) {
                markRunTimeout(run);
            }
        }
    }

    private void dispatchTaskRuns(Task task) {
        LambdaQueryWrapper<TaskDeviceRun> runQuery = new LambdaQueryWrapper<>();
        runQuery.eq(TaskDeviceRun::getTaskId, task.getId())
                .eq(TaskDeviceRun::getStatus, "PENDING")
                .orderByAsc(TaskDeviceRun::getGmtCreate);
        List<TaskDeviceRun> pendingRuns = taskDeviceRunMapper.selectList(runQuery);
        int dispatched = 0;
        for (TaskDeviceRun run : pendingRuns) {
            if (!canDispatch(run.getDeviceId())) {
                continue;
            }
            run.setStatus("RUNNING");
            if (run.getStartedAt() == null) {
                run.setStartedAt(LocalDateTime.now());
            }
            if (run.getCurrentStepNo() == null) {
                run.setCurrentStepNo(1);
            }
            taskDeviceRunMapper.updateById(run);
            insertRunningEvent(task.getId(), run);
            auditLogService.record("worker", "RUN_DISPATCH", "TASK_DEVICE_RUN", String.valueOf(run.getId()), "SUCCESS", new HashMap<>());
            dispatched++;
        }
        if (dispatched > 0) {
            if (task.getStartedAt() == null) {
                task.setStartedAt(LocalDateTime.now());
            }
            task.setStatus("RUNNING");
            taskMapper.updateById(task);
            return;
        }
        if ("QUEUED".equals(task.getStatus()) && !pendingRuns.isEmpty()) {
            task.setStatus("DISPATCHING");
            taskMapper.updateById(task);
        }
    }

    private boolean canDispatch(Long deviceId) {
        LambdaQueryWrapper<TaskDeviceRun> query = new LambdaQueryWrapper<>();
        query.eq(TaskDeviceRun::getDeviceId, deviceId)
                .eq(TaskDeviceRun::getStatus, "RUNNING");
        Long runningCount = taskDeviceRunMapper.selectCount(query);
        return runningCount == null || runningCount < dispatchProperties.getDeviceConcurrency();
    }

    private void insertRunningEvent(Long taskId, TaskDeviceRun run) {
        RunEvent event = new RunEvent();
        event.setEventNo(workerEventNo("DISPATCH"));
        event.setTaskId(taskId);
        event.setRunId(run.getId());
        event.setStepId(resolveCurrentStepId(taskId, run.getCurrentStepNo()));
        event.setStatus("RUNNING");
        event.setOccurredAt(LocalDateTime.now());
        event.setSensitiveScreenDetected(0);
        runEventMapper.insert(event);
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
        Long deadlineMs = extractDeadlineMs(task.getConstraints());
        if (deadlineMs != null && deadlineMs > 0) {
            LocalDateTime taskBaseTime = task.getStartedAt() == null ? task.getGmtCreate() : task.getStartedAt();
            return taskBaseTime != null && taskBaseTime.plusNanos(deadlineMs * 1_000_000L).isBefore(now);
        }
        return false;
    }

    private void markRunTimeout(TaskDeviceRun run) {
        run.setStatus("FAIL");
        run.setErrorCode("STEP_EXEC_TIMEOUT");
        run.setErrorMessage("执行超时");
        run.setFinishedAt(LocalDateTime.now());
        taskDeviceRunMapper.updateById(run);
        RunEvent event = new RunEvent();
        event.setEventNo(workerEventNo("TIMEOUT"));
        event.setTaskId(run.getTaskId());
        event.setRunId(run.getId());
        event.setStepId(resolveCurrentStepId(run.getTaskId(), run.getCurrentStepNo()));
        event.setStatus("FAIL");
        event.setOccurredAt(LocalDateTime.now());
        event.setErrorCode("STEP_EXEC_TIMEOUT");
        event.setErrorMessage("执行超时");
        event.setSensitiveScreenDetected(0);
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
            if ("SUCCESS".equals(run.getStatus())) {
                success++;
            } else if ("FAIL".equals(run.getStatus())) {
                fail++;
            } else if ("RUNNING".equals(run.getStatus())) {
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
