package com.aimacrodroid.service.impl;

import com.aimacrodroid.domain.entity.AuditLog;
import com.aimacrodroid.mapper.AuditLogMapper;
import com.aimacrodroid.security.OperatorContext;
import com.aimacrodroid.security.OperatorRole;
import com.aimacrodroid.service.AuditLogService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
public class AuditLogServiceImpl extends ServiceImpl<AuditLogMapper, AuditLog> implements AuditLogService {
    @Override
    public void record(String operatorId, String actionCode, String bizType, String bizId, String resultCode, Map<String, Object> detail) {
        try {
            AuditLog logEntity = new AuditLog();
            logEntity.setTraceId("trace-" + UUID.randomUUID());
            String contextOperatorId = OperatorContext.operatorId();
            OperatorRole contextRole = OperatorContext.operatorRole();
            logEntity.setOperatorId(contextOperatorId == null || contextOperatorId.isBlank() ? operatorId : contextOperatorId);
            logEntity.setOperatorRole(contextRole == null ? "system" : contextRole.name());
            logEntity.setActionCode(actionCode);
            logEntity.setBizType(bizType);
            logEntity.setBizId(bizId);
            logEntity.setRequestPayload(detail);
            logEntity.setResultCode(resultCode);
            logEntity.setResultMessage("SUCCESS".equalsIgnoreCase(resultCode) ? "OK" : resultCode);
            this.save(logEntity);
        } catch (Exception ex) {
            log.warn("审计日志记录失败 actionCode={} bizType={} bizId={}", actionCode, bizType, bizId, ex);
        }
    }
}
