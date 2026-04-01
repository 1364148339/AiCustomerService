package com.aimacrodroid.security;

import com.aimacrodroid.common.exception.BizException;
import com.aimacrodroid.domain.dto.EventReportReqDTO;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class DeviceSignatureVerifier {
    private final ObjectMapper objectMapper;

    @Value("${security.hmac.window-seconds:300}")
    private long hmacWindowSeconds;

    public void verify(String deviceId, String token, EventReportReqDTO req) {
        if (!StringUtils.hasText(token)) {
            throw new BizException("SIGNATURE_INVALID", "设备鉴权信息缺失", 401);
        }
        long nowMs = Instant.now().toEpochMilli();
        long diffMs = Math.abs(nowMs - req.getTimestamp());
        if (diffMs > hmacWindowSeconds * 1000) {
            throw new BizException("SIGNATURE_EXPIRED", "签名已过期", 401);
        }
        String signPayload = buildPayload(deviceId, req);
        String expected = hmacSha256Hex(token, signPayload);
        if (!expected.equalsIgnoreCase(req.getHmac())) {
            throw new BizException("SIGNATURE_INVALID", "签名不合法", 401);
        }
    }

    private String buildPayload(String deviceId, EventReportReqDTO req) {
        String bodyDigest = sha256Hex(normalizedBody(req));
        String stepId = req.getStepId() == null ? "" : String.valueOf(req.getStepId());
        return deviceId + "|" + req.getTaskId() + "|" + stepId + "|" + req.getTimestamp() + "|" + bodyDigest;
    }

    private String normalizedBody(EventReportReqDTO req) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("eventNo", req.getEventNo());
        body.put("taskId", req.getTaskId());
        body.put("stepId", req.getStepId());
        body.put("commandId", req.getCommandId());
        body.put("status", req.getStatus());
        body.put("timestamp", req.getTimestamp());
        body.put("durationMs", req.getDurationMs());
        body.put("screenshotUrl", req.getScreenshotUrl());
        body.put("foregroundPkg", req.getForegroundPkg());
        body.put("elements", req.getElements());
        body.put("errorCode", req.getErrorCode());
        body.put("errorMessage", req.getErrorMessage());
        body.put("trace", req.getTrace());
        body.put("thinking", req.getThinking());
        body.put("sensitiveScreenDetected", req.getSensitiveScreenDetected());
        body.put("progress", req.getProgress());
        try {
            return objectMapper.writeValueAsString(body);
        } catch (JsonProcessingException e) {
            throw new BizException("INVALID_PARAM", "事件体序列化失败");
        }
    }

    private String hmacSha256Hex(String secret, String value) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec keySpec = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            mac.init(keySpec);
            byte[] bytes = mac.doFinal(value.getBytes(StandardCharsets.UTF_8));
            return hex(bytes);
        } catch (Exception e) {
            throw new BizException("SIGNATURE_INVALID", "签名校验失败", 401);
        }
    }

    private String sha256Hex(String value) {
        try {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            return hex(bytes);
        } catch (Exception e) {
            throw new BizException("INVALID_PARAM", "摘要计算失败");
        }
    }

    private String hex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format(Locale.ROOT, "%02x", b));
        }
        return sb.toString();
    }
}
