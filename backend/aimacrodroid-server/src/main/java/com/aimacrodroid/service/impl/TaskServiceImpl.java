package com.aimacrodroid.service.impl;

import com.aimacrodroid.domain.dto.TaskCreateReqDTO;
import com.aimacrodroid.domain.entity.CommandInstance;
import com.aimacrodroid.domain.entity.Task;
import com.aimacrodroid.domain.entity.TaskDeviceRun;
import com.aimacrodroid.domain.vo.TaskCreateVO;
import com.aimacrodroid.domain.vo.TaskDetailVO;
import com.aimacrodroid.mapper.TaskMapper;
import com.aimacrodroid.service.CommandInstanceService;
import com.aimacrodroid.service.TaskDeviceRunService;
import com.aimacrodroid.service.TaskService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class TaskServiceImpl extends ServiceImpl<TaskMapper, Task> implements TaskService {

    private final TaskDeviceRunService taskDeviceRunService;
    private final CommandInstanceService commandInstanceService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TaskCreateVO createTask(TaskCreateReqDTO req) {
        // 1. 校验参数
        if ("INTENT".equals(req.getTrack()) && (req.getIntent() == null || req.getIntent().isEmpty())) {
            throw new IllegalArgumentException("INTENT 轨道必须指定 intent 意图标识");
        }
        if ("ATOMIC".equals(req.getTrack()) && CollectionUtils.isEmpty(req.getCommands())) {
            throw new IllegalArgumentException("ATOMIC 轨道必须提供 commands 原子指令列表");
        }

        // 2. 插入主任务表
        Task task = new Task();
        String taskNo = "T-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        task.setTaskNo(taskNo);
        task.setName(req.getType() + "-" + taskNo);
        task.setType(req.getType());
        task.setTrackType(req.getTrack());
        task.setPriority(req.getPriority());
        task.setStatus("QUEUED");
        
        task.setIntent(req.getIntent());
        task.setConstraints(req.getConstraints());
        task.setSuccessCriteria(req.getSuccessCriteria());
        task.setObservability(req.getObservability());
        task.setSafetyRails(req.getSafetyRails());
        task.setRhythm(req.getRhythm());
        task.setLoopConfig(req.getLoop());
        task.setRetryPolicy(req.getRetryPolicy());

        this.save(task);

        // 3. 插入设备任务运行态表
        List<TaskDeviceRun> runs = new ArrayList<>();
        for (String deviceId : req.getDevices()) {
            TaskDeviceRun run = new TaskDeviceRun();
            run.setTaskId(task.getId());
            run.setDeviceId(deviceId);
            run.setStatus("PENDING");
            run.setRetryCount(0);
            runs.add(run);
        }
        taskDeviceRunService.saveBatch(runs);

        // 4. 如果是原子轨道，插入指令序列表
        if ("ATOMIC".equals(req.getTrack())) {
            List<CommandInstance> cmds = new ArrayList<>();
            int order = 1;
            for (Map<String, Object> cmdMap : req.getCommands()) {
                CommandInstance cmd = new CommandInstance();
                cmd.setTaskId(task.getId());
                cmd.setCommandId((String) cmdMap.get("commandId"));
                cmd.setAction((String) cmdMap.get("action"));
                
                // 将 map 中取出强转
                if (cmdMap.get("params") instanceof Map) {
                    cmd.setParams((Map<String, Object>) cmdMap.get("params"));
                }
                if (cmdMap.get("retryPolicy") instanceof Map) {
                    cmd.setRetryPolicy((Map<String, Object>) cmdMap.get("retryPolicy"));
                }
                cmd.setIdempotentKey((String) cmdMap.get("idempotentKey"));
                cmd.setOrderNum(order++);
                cmds.add(cmd);
            }
            commandInstanceService.saveBatch(cmds);
        }

        log.info("任务创建成功, TaskNo: {}, 目标设备数: {}", taskNo, req.getDevices().size());

        return TaskCreateVO.builder()
                .taskId(task.getId())
                .taskNo(task.getTaskNo())
                .status(task.getStatus())
                .build();
    }

    @Override
    public TaskDetailVO getTaskDetail(Long taskId) {
        Task task = this.getById(taskId);
        if (task == null) {
            throw new RuntimeException("任务不存在");
        }

        // 1. 查询各设备的执行状态
        LambdaQueryWrapper<TaskDeviceRun> runQuery = new LambdaQueryWrapper<>();
        runQuery.eq(TaskDeviceRun::getTaskId, taskId);
        List<TaskDeviceRun> deviceRuns = taskDeviceRunService.list(runQuery);

        // 2. 如果是原子轨道，查询指令序列表
        List<CommandInstance> commands = null;
        if ("ATOMIC".equals(task.getTrackType())) {
            LambdaQueryWrapper<CommandInstance> cmdQuery = new LambdaQueryWrapper<>();
            cmdQuery.eq(CommandInstance::getTaskId, taskId)
                    .orderByAsc(CommandInstance::getOrderNum);
            commands = commandInstanceService.list(cmdQuery);
        }

        return TaskDetailVO.builder()
                .task(task)
                .deviceRuns(deviceRuns)
                .commands(commands)
                .build();
    }
}
