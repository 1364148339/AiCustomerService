package com.aimacrodroid.domain.entity;

import java.util.Arrays;
import java.util.Locale;
import java.util.Map;

public enum RunEventType {
    STARTED("任务开始"),
    STEP_STARTED("步骤开始"),
    THINKING_UPDATED("思考更新"),
    ACTION_EXECUTED("动作执行"),
    COMPLETED("任务完成"),
    FAILED("任务失败"),
    SCREENSHOT_STARTED("截图开始"),
    SCREENSHOT_COMPLETED("截图完成"),
    UNKNOWN("未知事件");

    private final String zhDesc;

    RunEventType(String zhDesc) {
        this.zhDesc = zhDesc;
    }

    public String getZhDesc() {
        return zhDesc;
    }

    public static RunEventType fromValue(String raw) {
        if (raw == null || raw.isBlank()) {
            return UNKNOWN;
        }
        String normalized = raw.trim().toUpperCase(Locale.ROOT);
        return Arrays.stream(values())
                .filter(type -> type.name().equals(normalized))
                .findFirst()
                .orElse(UNKNOWN);
    }

    public static RunEventType fromTrace(Map<String, Object> traceEntry) {
        if (traceEntry == null) {
            return UNKNOWN;
        }
        Object value = traceEntry.get("eventType");
        return fromValue(value == null ? null : String.valueOf(value));
    }
}
