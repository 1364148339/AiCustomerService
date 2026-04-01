package com.aimacrodroid.service.impl;

import com.aimacrodroid.domain.dto.EventReportReqDTO;
import com.aimacrodroid.domain.entity.RunEvent;
import com.aimacrodroid.domain.entity.Snapshot;
import com.aimacrodroid.domain.entity.TaskDeviceRun;
import com.aimacrodroid.mapper.RunEventMapper;
import com.aimacrodroid.service.RunEventService;
import com.aimacrodroid.service.SnapshotService;
import com.aimacrodroid.service.TaskDeviceRunService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class RunEventServiceImpl extends ServiceImpl<RunEventMapper, RunEvent> implements RunEventService {

    private final SnapshotService snapshotService;
    private final TaskDeviceRunService taskDeviceRunService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void reportEvent(String deviceId, EventReportReqDTO req) {
        // TODO: 1. 验证 HMAC 签名 (由于涉及 Device Token 查询，Phase 1 暂时忽略)

        // 2. 记录事件流水
        RunEvent event = new RunEvent();
        event.setTaskId(req.getTaskId());
        event.setDeviceId(deviceId);
        event.setCommandId(req.getCommandId());
        event.setStatus(req.getStatus());
        event.setEventTimestamp(req.getTimestamp());
        event.setDurationMs(req.getDurationMs());
        event.setErrorCode(req.getErrorCode());
        event.setTrace(req.getTrace());
        event.setThinking(req.getThinking());
        event.setSensitiveScreenDetected(req.getSensitiveScreenDetected());
        event.setProgress(req.getProgress());
        event.setHmacSignature(req.getHmac());
        
        this.save(event);

        // 3. 如果带有截图或元素树，保存快照存证
        if (req.getScreenshotUrl() != null || req.getElements() != null) {
            Snapshot snapshot = new Snapshot();
            snapshot.setEventId(event.getId());
            snapshot.setTaskId(req.getTaskId());
            snapshot.setDeviceId(deviceId);
            snapshot.setScreenshotUrl(req.getScreenshotUrl() != null ? req.getScreenshotUrl() : "");
            snapshot.setForegroundPkg(req.getForegroundPkg());
            snapshot.setElements(req.getElements());
            snapshot.setSnapshotTimestamp(req.getTimestamp());
            snapshotService.save(snapshot);
        }

        // 4. 更新 TaskDeviceRun 的最新状态及进度
        LambdaQueryWrapper<TaskDeviceRun> runQuery = new LambdaQueryWrapper<>();
        runQuery.eq(TaskDeviceRun::getTaskId, req.getTaskId())
                .eq(TaskDeviceRun::getDeviceId, deviceId);
        
        TaskDeviceRun run = taskDeviceRunService.getOne(runQuery);
        if (run != null) {
            boolean needUpdate = false;
            // 状态流转只允许 PENDING -> RUNNING -> SUCCESS/FAIL
            if (!"SUCCESS".equals(run.getStatus()) && !"FAIL".equals(run.getStatus())) {
                run.setStatus(req.getStatus());
                needUpdate = true;
            }
            if (req.getProgress() != null) {
                run.setProgress(req.getProgress());
                needUpdate = true;
            }
            if (req.getErrorCode() != null) {
                run.setErrorCode(req.getErrorCode());
                run.setErrorMessage(req.getErrorMessage());
                needUpdate = true;
            }
            if (needUpdate) {
                taskDeviceRunService.updateById(run);
            }
        }
        
        log.info("接收到设备 {} 的任务 {} 回传事件: {}", deviceId, req.getTaskId(), req.getStatus());
    }
}
