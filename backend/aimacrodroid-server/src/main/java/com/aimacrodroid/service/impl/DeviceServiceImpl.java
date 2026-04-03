package com.aimacrodroid.service.impl;

import com.aimacrodroid.common.exception.BizException;
import com.aimacrodroid.domain.dto.DeviceHeartbeatReqDTO;
import com.aimacrodroid.domain.dto.DeviceRegisterReqDTO;
import com.aimacrodroid.domain.entity.Device;
import com.aimacrodroid.domain.entity.DeviceReadiness;
import com.aimacrodroid.domain.entity.RunEvent;
import com.aimacrodroid.domain.entity.StepInstance;
import com.aimacrodroid.domain.entity.Task;
import com.aimacrodroid.domain.entity.TaskDeviceRun;
import com.aimacrodroid.domain.vo.DeviceReadinessVO;
import com.aimacrodroid.domain.vo.DeviceRegisterVO;
import com.aimacrodroid.domain.vo.DeviceStatusVO;
import com.aimacrodroid.domain.vo.DeviceTaskVO;
import com.aimacrodroid.mapper.DeviceMapper;
import com.aimacrodroid.mapper.DeviceReadinessMapper;
import com.aimacrodroid.mapper.RunEventMapper;
import com.aimacrodroid.mapper.StepInstanceMapper;
import com.aimacrodroid.mapper.TaskDeviceRunMapper;
import com.aimacrodroid.mapper.TaskMapper;
import com.aimacrodroid.service.AuditLogService;
import com.aimacrodroid.service.DeviceService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class DeviceServiceImpl extends ServiceImpl<DeviceMapper, Device> implements DeviceService {

    private final DeviceReadinessMapper deviceReadinessMapper;
    private final TaskDeviceRunMapper taskDeviceRunMapper;
    private final TaskMapper taskMapper;
    private final StepInstanceMapper stepInstanceMapper;
    private final RunEventMapper runEventMapper;
    private final AuditLogService auditLogService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DeviceRegisterVO register(DeviceRegisterReqDTO req) {
        Device existingDevice = getByDeviceCode(req.getDeviceId());
        String token = UUID.randomUUID().toString().replace("-", "");
        String tokenHash = sha256(token);
        Device current;
        if (existingDevice == null) {
            current = new Device();
            current.setDeviceCode(req.getDeviceId());
            current.setBrand(req.getBrand());
            current.setModel(req.getModel());
            current.setAndroidVersion(req.getAndroidVersion());
            current.setResolution(req.getResolution());
            current.setCapabilityJson(normalizeCapabilitiesForStorage(req.getCapabilities()));
            current.setDeviceStatus("ONLINE");
            current.setTokenHash(tokenHash);
            current.setLastSeenAt(LocalDateTime.now());
            this.save(current);
            current = getByDeviceCode(req.getDeviceId());
        } else {
            current = existingDevice;
            current.setBrand(req.getBrand());
            current.setModel(req.getModel());
            current.setAndroidVersion(req.getAndroidVersion());
            current.setResolution(req.getResolution());
            current.setCapabilityJson(normalizeCapabilitiesForStorage(req.getCapabilities()));
            current.setDeviceStatus("ONLINE");
            current.setTokenHash(tokenHash);
            current.setLastSeenAt(LocalDateTime.now());
            this.updateById(current);
        }
        upsertReadiness(current.getId(), req.getShizukuAvailable(), req.getOverlayGranted(), req.getKeyboardEnabled(), req.getSseSupported(), null, null, null, null);
        auditLogService.record(req.getDeviceId(), "DEVICE_REGISTER", "DEVICE", req.getDeviceId(), "SUCCESS", new HashMap<>());
        return DeviceRegisterVO.builder()
                .registered(true)
                .token(token)
                .build();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void heartbeat(DeviceHeartbeatReqDTO req) {
        Device device = getByDeviceCode(req.getDeviceId());
        if (device == null) {
            log.warn("收到未注册设备的心跳，deviceId: {}", req.getDeviceId());
            throw new BizException("DEVICE_NOT_FOUND", "设备未注册");
        }
        if (req.getCapabilities() != null) {
            device.setCapabilityJson(normalizeCapabilitiesForStorage(req.getCapabilities()));
        }
        device.setLastSeenAt(LocalDateTime.now());
        device.setDeviceStatus("ONLINE");
        this.updateById(device);
        upsertReadiness(device.getId(), req.getShizukuAvailable(), req.getOverlayGranted(), req.getKeyboardEnabled(), req.getSseSupported(),
                req.getForegroundPkg(), req.getBatteryPct(), req.getNetworkType(), req.getCharging());
        auditLogService.record(req.getDeviceId(), "DEVICE_HEARTBEAT", "DEVICE", req.getDeviceId(), "SUCCESS", new HashMap<>());
    }

    @Override
    public DeviceStatusVO getDeviceStatus(String deviceId) {
        Device device = getByDeviceCode(deviceId);
        if (device == null) {
            throw new BizException("DEVICE_NOT_FOUND", "设备不存在");
        }
        String status = device.getDeviceStatus();
        if ("ONLINE".equals(status) && device.getLastSeenAt() != null && device.getLastSeenAt().plusMinutes(3).isBefore(LocalDateTime.now())) {
            status = "OFFLINE";
            device.setDeviceStatus("OFFLINE");
            this.updateById(device);
        }
        DeviceReadiness readiness = getLatestReadiness(device.getId());
        return DeviceStatusVO.builder()
                .status(status)
                .lastHeartbeatTime(device.getLastSeenAt())
                .foregroundPkg(readiness == null ? null : readiness.getForegroundPkg())
                .capabilities(device.getCapabilityJson())
                .shizukuAvailable(readiness == null ? 0 : readiness.getIsShizukuAvailable())
                .overlayGranted(readiness == null ? 0 : readiness.getIsOverlayGranted())
                .keyboardEnabled(readiness == null ? 0 : readiness.getIsKeyboardEnabled())
                .build();
    }

    @Override
    public DeviceReadinessVO getDeviceReadiness(String deviceId) {
        Device device = getByDeviceCode(deviceId);
        if (device == null) {
            throw new BizException("DEVICE_NOT_FOUND", "设备不存在");
        }
        DeviceReadiness readiness = getLatestReadiness(device.getId());
        if (readiness == null) {
            return DeviceReadinessVO.builder()
                    .shizukuRunning(0)
                    .overlayGranted(0)
                    .keyboardEnabled(0)
                    .lastActivationMethod("unknown")
                    .build();
        }
        return DeviceReadinessVO.builder()
                .shizukuRunning(readiness.getIsShizukuAvailable())
                .overlayGranted(readiness.getIsOverlayGranted())
                .keyboardEnabled(readiness.getIsKeyboardEnabled())
                .lastActivationMethod("unknown")
                .build();
    }

    @Override
    public List<DeviceTaskVO> pullPendingTasks(String deviceId) {
        Device device = getByDeviceCode(deviceId);
        if (device == null) {
            throw new BizException("DEVICE_NOT_FOUND", "设备不存在");
        }
        List<TaskDeviceRun> runs = queryDeviceRuns(device.getId(), "RUNNING");
        if (runs.isEmpty()) {
            runs = queryDeviceRuns(device.getId(), "PENDING");
        }
        if (runs.isEmpty()) {
            return Collections.emptyList();
        }
        List<Long> taskIds = runs.stream().map(TaskDeviceRun::getTaskId).distinct().collect(Collectors.toList());
        LambdaQueryWrapper<Task> taskQuery = new LambdaQueryWrapper<>();
        taskQuery.in(Task::getId, taskIds);
        List<Task> tasks = taskMapper.selectList(taskQuery);
        Map<Long, Task> taskMap = tasks.stream().collect(Collectors.toMap(Task::getId, t -> t));
        LambdaQueryWrapper<StepInstance> stepQuery = new LambdaQueryWrapper<>();
        stepQuery.in(StepInstance::getTaskId, taskIds).orderByAsc(StepInstance::getStepNo);
        Map<Long, List<Map<String, Object>>> stepMap = stepInstanceMapper.selectList(stepQuery).stream()
                .collect(Collectors.groupingBy(StepInstance::getTaskId, Collectors.mapping(this::toStepPayload, Collectors.toList())));
        List<DeviceTaskVO> result = new ArrayList<>();
        for (TaskDeviceRun run : runs) {
            Task task = taskMap.get(run.getTaskId());
            if (task == null) {
                continue;
            }
            markRunAckIfNeeded(deviceId, run);
            List<Map<String, Object>> steps = pickPendingSteps(stepMap.get(task.getId()), run.getCurrentStepNo());
            result.add(DeviceTaskVO.builder()
                    .id(String.valueOf(task.getId()))
                    .scenarioKey(task.getScenarioKey())
                    .scenarioName(task.getScenarioName())
                    .track("atomic")
                    .steps(steps)
                    .commands(steps)
                    .constraints(task.getTaskConstraints())
                    .observability(task.getObservability())
                    .priority(task.getPriority() == null ? 0 : task.getPriority())
                    .build());
        }
        result.sort(Comparator.comparingInt(DeviceTaskVO::getPriority).reversed().thenComparing(DeviceTaskVO::getId));
        return result;
    }

    private void markRunAckIfNeeded(String deviceCode, TaskDeviceRun run) {
        String ackEventNo = "ACK-" + run.getId();
        LambdaQueryWrapper<RunEvent> ackQuery = new LambdaQueryWrapper<>();
        ackQuery.eq(RunEvent::getEventNo, ackEventNo)
                .last("LIMIT 1");
        if (runEventMapper.selectOne(ackQuery) != null) {
            return;
        }
        if ("PENDING".equals(run.getRunStatus())) {
            run.setRunStatus("RUNNING");
            if (run.getStartedAt() == null) {
                run.setStartedAt(LocalDateTime.now());
            }
            if (run.getCurrentStepNo() == null) {
                run.setCurrentStepNo(1);
            }
            taskDeviceRunMapper.updateById(run);
        }
        RunEvent event = new RunEvent();
        event.setEventNo(ackEventNo);
        event.setTaskId(run.getTaskId());
        event.setRunId(run.getId());
        event.setStepInstanceId(resolveCurrentStepId(run.getTaskId(), run.getCurrentStepNo()));
        event.setEventStatus("RUNNING");
        event.setErrorMessage("设备确认开始执行");
        HashMap<String, Object> progress = new HashMap<>();
        progress.put("source", "DEVICE_TASK_ACKED");
        progress.put("deviceId", deviceCode);
        event.setProgressJson(progress);
        event.setOccurredAt(LocalDateTime.now());
        runEventMapper.insert(event);
        Task task = taskMapper.selectById(run.getTaskId());
        if (task != null && !"CANCELED".equals(task.getStatus()) && !"SUCCESS".equals(task.getStatus()) && !"FAIL".equals(task.getStatus())) {
            if (task.getStartedAt() == null) {
                task.setStartedAt(LocalDateTime.now());
            }
            task.setStatus("RUNNING");
            taskMapper.updateById(task);
        }
        HashMap<String, Object> detail = new HashMap<>();
        detail.put("taskId", run.getTaskId());
        detail.put("runId", run.getId());
        detail.put("deviceId", deviceCode);
        auditLogService.record(deviceCode, "DEVICE_TASK_ACK", "TASK_DEVICE_RUN", String.valueOf(run.getId()), "SUCCESS", detail);
    }

    private List<Map<String, Object>> pickPendingSteps(List<Map<String, Object>> steps, Integer currentStepNo) {
        if (steps == null || steps.isEmpty()) {
            return Collections.emptyList();
        }
        int stepNo = (currentStepNo == null || currentStepNo <= 0) ? 1 : currentStepNo;
        List<Map<String, Object>> pending = steps.stream()
                .filter(step -> extractStepNo(step) >= stepNo)
                .collect(Collectors.toList());
        if (!pending.isEmpty()) {
            return pending;
        }
        return steps;
    }

    private int extractStepNo(Map<String, Object> step) {
        if (step == null) {
            return 0;
        }
        Object value = step.get("stepNo");
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String s) {
            try {
                return Integer.parseInt(s);
            } catch (Exception ignored) {
                return 0;
            }
        }
        return 0;
    }

    private List<TaskDeviceRun> queryDeviceRuns(Long devicePkId, String status) {
        LambdaQueryWrapper<TaskDeviceRun> runQuery = new LambdaQueryWrapper<>();
        runQuery.eq(TaskDeviceRun::getDeviceId, devicePkId)
                .eq(TaskDeviceRun::getRunStatus, status)
                .orderByAsc(TaskDeviceRun::getGmtCreate);
        return taskDeviceRunMapper.selectList(runQuery);
    }

    private Long resolveCurrentStepId(Long taskId, Integer currentStepNo) {
        int stepNo = (currentStepNo == null || currentStepNo <= 0) ? 1 : currentStepNo;
        LambdaQueryWrapper<StepInstance> query = new LambdaQueryWrapper<>();
        query.eq(StepInstance::getTaskId, taskId)
                .eq(StepInstance::getStepNo, stepNo)
                .last("LIMIT 1");
        StepInstance step = stepInstanceMapper.selectOne(query);
        return step == null ? null : step.getId();
    }

    private void upsertReadiness(Long devicePkId, Object shizuku, Object overlay, Object keyboard, Object sse, String foregroundPkg,
                                 Integer batteryPct, String networkType, Object charging) {
        LambdaQueryWrapper<DeviceReadiness> query = new LambdaQueryWrapper<>();
        query.eq(DeviceReadiness::getDeviceId, devicePkId).last("LIMIT 1");
        DeviceReadiness readiness = deviceReadinessMapper.selectOne(query);
        if (readiness == null) {
            readiness = new DeviceReadiness();
            readiness.setDeviceId(devicePkId);
        }
        if (foregroundPkg != null) {
            readiness.setForegroundPkg(foregroundPkg);
        }
        if (batteryPct != null) {
            readiness.setBatteryPct(batteryPct);
        }
        if (networkType != null) {
            readiness.setNetworkType(networkType);
        }
        if (charging != null) {
            readiness.setIsCharging(toIntFlag(charging));
        }
        if (shizuku != null) {
            readiness.setIsShizukuAvailable(toIntFlag(shizuku));
        }
        if (overlay != null) {
            readiness.setIsOverlayGranted(toIntFlag(overlay));
        }
        if (keyboard != null) {
            readiness.setIsKeyboardEnabled(toIntFlag(keyboard));
        }
        if (sse != null) {
            readiness.setIsSseSupported(toIntFlag(sse));
        }
        readiness.setHeartbeatAt(LocalDateTime.now());
        if (readiness.getId() == null) {
            deviceReadinessMapper.insert(readiness);
        } else {
            deviceReadinessMapper.updateById(readiness);
        }
    }

    private Device getByDeviceCode(String deviceCode) {
        LambdaQueryWrapper<Device> query = new LambdaQueryWrapper<>();
        query.eq(Device::getDeviceCode, deviceCode).last("LIMIT 1");
        return this.getOne(query, false);
    }

    private DeviceReadiness getLatestReadiness(Long devicePkId) {
        LambdaQueryWrapper<DeviceReadiness> query = new LambdaQueryWrapper<>();
        query.eq(DeviceReadiness::getDeviceId, devicePkId).last("LIMIT 1");
        return deviceReadinessMapper.selectOne(query);
    }

    private Integer toIntFlag(Object value) {
        if (value == null) {
            return 0;
        }
        if (value instanceof Boolean) {
            Boolean b = (Boolean) value;
            return b ? 1 : 0;
        }
        if (value instanceof Number) {
            Number n = (Number) value;
            return n.intValue() == 0 ? 0 : 1;
        }
        if (value instanceof String) {
            String s = (String) value;
            String normalized = s.trim().toLowerCase();
            if ("true".equals(normalized) || "1".equals(normalized) || "yes".equals(normalized)) {
                return 1;
            }
        }
        return 0;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> normalizeCapabilitiesForStorage(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Map<?, ?>) {
            Map<?, ?> mapValue = (Map<?, ?>) value;
            return (Map<String, Object>) mapValue;
        }
        if (value instanceof List<?>) {
            List<?> listValue = (List<?>) value;
            Map<String, Object> wrapped = new HashMap<>();
            wrapped.put("items", listValue);
            return wrapped;
        }
        Map<String, Object> wrapped = new HashMap<>();
        wrapped.put("value", value);
        return wrapped;
    }

    private Map<String, Object> toStepPayload(StepInstance stepInstance) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("stepId", stepInstance.getId());
        payload.put("stepNo", stepInstance.getStepNo());
        payload.put("stepName", stepInstance.getStepName());
        payload.put("actionCode", stepInstance.getActionCode());
        payload.put("action", stepInstance.getActionCode());
        payload.put("params", stepInstance.getActionParams());
        payload.put("timeoutMs", stepInstance.getTimeoutMs());
        payload.put("retryMax", stepInstance.getRetryMax());
        payload.put("retryBackoffMs", stepInstance.getRetryBackoffMs());
        return payload;
    }

    private String sha256(String value) {
        try {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) {
                sb.append(String.format(java.util.Locale.ROOT, "%02x", b));
            }
            return sb.toString();
        } catch (Exception ex) {
            throw new BizException("INTERNAL_ERROR", "token摘要生成失败");
        }
    }
}
