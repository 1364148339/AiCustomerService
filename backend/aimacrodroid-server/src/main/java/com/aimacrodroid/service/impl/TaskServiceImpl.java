package com.aimacrodroid.service.impl;

import com.aimacrodroid.common.exception.BizException;
import com.aimacrodroid.domain.dto.TaskCreateReqDTO;
import com.aimacrodroid.domain.entity.Device;
import com.aimacrodroid.domain.entity.ScenarioDefinition;
import com.aimacrodroid.domain.entity.ScenarioStep;
import com.aimacrodroid.domain.entity.StepInstance;
import com.aimacrodroid.domain.entity.Task;
import com.aimacrodroid.domain.entity.TaskDeviceRun;
import com.aimacrodroid.domain.vo.TaskCreateVO;
import com.aimacrodroid.domain.vo.TaskDetailVO;
import com.aimacrodroid.mapper.ScenarioDefinitionMapper;
import com.aimacrodroid.mapper.ScenarioStepMapper;
import com.aimacrodroid.mapper.StepInstanceMapper;
import com.aimacrodroid.mapper.TaskDeviceRunMapper;
import com.aimacrodroid.mapper.TaskMapper;
import com.aimacrodroid.mapper.DeviceMapper;
import com.aimacrodroid.service.AuditLogService;
import com.aimacrodroid.service.TaskService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class TaskServiceImpl extends ServiceImpl<TaskMapper, Task> implements TaskService {

    private final TaskDeviceRunMapper taskDeviceRunMapper;
    private final ScenarioDefinitionMapper scenarioDefinitionMapper;
    private final ScenarioStepMapper scenarioStepMapper;
    private final StepInstanceMapper stepInstanceMapper;
    private final DeviceMapper deviceMapper;
    private final AuditLogService auditLogService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TaskCreateVO createTask(TaskCreateReqDTO req) {
        LambdaQueryWrapper<ScenarioDefinition> scenarioQuery = new LambdaQueryWrapper<>();
        scenarioQuery.eq(ScenarioDefinition::getScenarioKey, req.getScenarioKey());
        ScenarioDefinition scenario = scenarioDefinitionMapper.selectOne(scenarioQuery);
        if (scenario == null) {
            throw new BizException("SCENARIO_NOT_FOUND", "场景不存在");
        }
        if (!"ACTIVE".equals(scenario.getStatus())) {
            throw new BizException("SCENARIO_NOT_ACTIVE", "场景未发布，无法创建任务");
        }

        LambdaQueryWrapper<ScenarioStep> stepQuery = new LambdaQueryWrapper<>();
        stepQuery.eq(ScenarioStep::getScenarioId, scenario.getId())
                .eq(ScenarioStep::getIsEnabled, 1)
                .orderByAsc(ScenarioStep::getStepNo);
        List<ScenarioStep> scenarioSteps = scenarioStepMapper.selectList(stepQuery);
        if (CollectionUtils.isEmpty(scenarioSteps)) {
            throw new BizException("SCENARIO_STEPS_EMPTY", "场景没有可执行步骤");
        }

        Task task = new Task();
        String taskNo = "T-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        task.setTaskNo(taskNo);
        task.setScenarioId(scenario.getId());
        task.setScenarioKey(scenario.getScenarioKey());
        task.setScenarioName(scenario.getScenarioName());
        task.setPriority(req.getPriority());
        task.setStatus("QUEUED");
        task.setConstraints(req.getConstraints());
        task.setObservability(req.getObservability());
        task.setTotalDeviceCount(req.getDevices().size());
        task.setSuccessDeviceCount(0);
        task.setFailDeviceCount(0);

        this.save(task);

        List<TaskDeviceRun> runs = new ArrayList<>();
        for (String deviceId : req.getDevices()) {
            Device device = getByDeviceCode(deviceId);
            TaskDeviceRun run = new TaskDeviceRun();
            run.setTaskId(task.getId());
            run.setDeviceId(device.getId());
            run.setStatus("PENDING");
            run.setRetryCount(0);
            runs.add(run);
        }
        runs.forEach(taskDeviceRunMapper::insert);

        List<StepInstance> stepInstances = new ArrayList<>();
        for (ScenarioStep scenarioStep : scenarioSteps) {
            StepInstance stepInstance = new StepInstance();
            stepInstance.setTaskId(task.getId());
            stepInstance.setSourceStepId(scenarioStep.getId());
            stepInstance.setStepNo(scenarioStep.getStepNo());
            stepInstance.setStepName(scenarioStep.getStepName());
            stepInstance.setActionCode(scenarioStep.getActionCode());
            stepInstance.setActionParams(scenarioStep.getActionParams());
            stepInstance.setTimeoutMs(scenarioStep.getTimeoutMs());
            stepInstance.setRetryMax(scenarioStep.getRetryMax());
            stepInstance.setRetryBackoffMs(scenarioStep.getRetryBackoffMs());
            stepInstances.add(stepInstance);
        }
        stepInstances.forEach(stepInstanceMapper::insert);
        auditLogService.record("system", "TASK_CREATE", "TASK", String.valueOf(task.getId()), "SUCCESS", new HashMap<>());

        log.info("任务创建成功, TaskNo: {}, 目标设备数: {}", taskNo, req.getDevices().size());

        return TaskCreateVO.builder()
                .taskId(task.getId())
                .taskNo(task.getTaskNo())
                .status(task.getStatus())
                .scenarioKey(task.getScenarioKey())
                .scenarioName(task.getScenarioName())
                .build();
    }

    @Override
    public TaskDetailVO getTaskDetail(Long taskId) {
        Task task = this.getById(taskId);
        if (task == null) {
            throw new BizException("TASK_NOT_FOUND", "任务不存在");
        }

        LambdaQueryWrapper<TaskDeviceRun> runQuery = new LambdaQueryWrapper<>();
        runQuery.eq(TaskDeviceRun::getTaskId, taskId).orderByAsc(TaskDeviceRun::getId);
        List<TaskDeviceRun> deviceRuns = taskDeviceRunMapper.selectList(runQuery);

        LambdaQueryWrapper<StepInstance> stepQuery = new LambdaQueryWrapper<>();
        stepQuery.eq(StepInstance::getTaskId, taskId).orderByAsc(StepInstance::getStepNo);
        List<StepInstance> stepInstances = stepInstanceMapper.selectList(stepQuery);

        HashMap<String, Integer> stats = new HashMap<>();
        stats.put("total", deviceRuns.size());
        stats.put("success", 0);
        stats.put("running", 0);
        stats.put("fail", 0);
        stats.put("pending", 0);
        for (TaskDeviceRun run : deviceRuns) {
            String status = run.getStatus();
            if ("SUCCESS".equals(status)) {
                stats.put("success", stats.get("success") + 1);
            } else if ("FAIL".equals(status)) {
                stats.put("fail", stats.get("fail") + 1);
            } else if ("RUNNING".equals(status)) {
                stats.put("running", stats.get("running") + 1);
            } else {
                stats.put("pending", stats.get("pending") + 1);
            }
        }

        return TaskDetailVO.builder()
                .task(task)
                .deviceRuns(deviceRuns)
                .stepInstances(stepInstances)
                .stats(stats)
                .build();
    }

    private Device getByDeviceCode(String deviceCode) {
        LambdaQueryWrapper<Device> query = new LambdaQueryWrapper<>();
        query.eq(Device::getDeviceId, deviceCode).last("LIMIT 1");
        Device device = deviceMapper.selectOne(query);
        if (device == null) {
            throw new BizException("DEVICE_NOT_FOUND", "设备不存在: " + deviceCode);
        }
        return device;
    }
}
