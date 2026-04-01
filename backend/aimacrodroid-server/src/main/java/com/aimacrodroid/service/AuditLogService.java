package com.aimacrodroid.service;

import com.aimacrodroid.domain.entity.AuditLog;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.Map;

public interface AuditLogService extends IService<AuditLog> {
    void record(String operatorId, String actionCode, String bizType, String bizId, String resultCode, Map<String, Object> detail);
}
