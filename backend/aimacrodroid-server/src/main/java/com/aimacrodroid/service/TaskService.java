package com.aimacrodroid.service;

import com.aimacrodroid.domain.dto.TaskCreateReqDTO;
import com.aimacrodroid.domain.entity.Task;
import com.aimacrodroid.domain.vo.TaskCreateVO;
import com.aimacrodroid.domain.vo.TaskDetailVO;
import com.baomidou.mybatisplus.extension.service.IService;

public interface TaskService extends IService<Task> {

    /**
     * 创建并下发任务
     */
    TaskCreateVO createTask(TaskCreateReqDTO req);
    /**
     * 获取任务详情（包含执行状态及指令序列）
     */
    TaskDetailVO getTaskDetail(Long taskId);

    void cancelTask(Long taskId, String reason);
}
