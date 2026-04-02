package com.aimacrodroid.security;

import com.aimacrodroid.common.exception.BizException;
import com.aimacrodroid.domain.dto.EventReportReqDTO;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

class DeviceSignatureVerifierTest {
    private final ObjectMapper objectMapper = new ObjectMapper();
    private DeviceSignatureVerifier verifier;

    @BeforeEach
    void setUp() throws Exception {
        verifier = new DeviceSignatureVerifier(objectMapper);
        Field field = DeviceSignatureVerifier.class.getDeclaredField("hmacWindowSeconds");
        field.setAccessible(true);
        field.set(verifier, 300L);
    }

    @Test
    void shouldVerifyByRawTokenAndBase64() {
        String deviceId = "dev-1";
        String rawToken = "abc123456";
        EventReportReqDTO req = newReq();
        String payload = buildPayload(deviceId, req);
        req.setHmac(hmacBase64(rawToken, payload));
        verifier.verify(deviceId, rawToken, sha256Hex(rawToken), req);
    }

    @Test
    void shouldVerifyByStoredHashFallbackAndHex() {
        String deviceId = "dev-2";
        String rawToken = "token-xyz";
        String tokenHash = sha256Hex(rawToken);
        EventReportReqDTO req = newReq();
        String payload = buildPayload(deviceId, req);
        req.setHmac(hmacHex(tokenHash, payload));
        verifier.verify(deviceId, null, tokenHash, req);
    }

    @Test
    void shouldFailWhenRawTokenNotMatchStoredHash() {
        String deviceId = "dev-3";
        String rawToken = "token-xyz";
        String tokenHash = sha256Hex("another-token");
        EventReportReqDTO req = newReq();
        String payload = buildPayload(deviceId, req);
        req.setHmac(hmacBase64(rawToken, payload));
        BizException ex = Assertions.assertThrows(BizException.class, () -> verifier.verify(deviceId, rawToken, tokenHash, req));
        Assertions.assertEquals("SIGNATURE_INVALID", ex.getCode());
    }

    private EventReportReqDTO newReq() {
        EventReportReqDTO req = new EventReportReqDTO();
        req.setEventNo("E-1");
        req.setTaskId(1L);
        req.setCommandId("c_open");
        req.setStatus("RUNNING");
        req.setTimestamp(System.currentTimeMillis());
        return req;
    }

    private String buildPayload(String deviceId, EventReportReqDTO req) {
        String stepId = req.getStepId() == null ? (req.getCommandId() == null ? "" : req.getCommandId()) : String.valueOf(req.getStepId());
        return deviceId + ":" + req.getTaskId() + ":" + stepId + ":" + req.getTimestamp() + ":" + sha256Hex(normalizedBody(req));
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
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    private String hmacHex(String secret, String value) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec keySpec = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            mac.init(keySpec);
            byte[] bytes = mac.doFinal(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) {
                sb.append(String.format(Locale.ROOT, "%02x", b));
            }
            return sb.toString();
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    private String hmacBase64(String secret, String value) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec keySpec = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            mac.init(keySpec);
            byte[] bytes = mac.doFinal(value.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().withoutPadding().encodeToString(bytes);
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    private String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) {
                sb.append(String.format(Locale.ROOT, "%02x", b));
            }
            return sb.toString();
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }
}
