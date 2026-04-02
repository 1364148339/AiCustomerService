package com.aimacrodroid.security;

public class OperatorContext {
    private static final ThreadLocal<String> OPERATOR_ID = new ThreadLocal<>();
    private static final ThreadLocal<OperatorRole> OPERATOR_ROLE = new ThreadLocal<>();

    private OperatorContext() {
    }

    public static void set(String operatorId, OperatorRole role) {
        OPERATOR_ID.set(operatorId);
        OPERATOR_ROLE.set(role);
    }

    public static String operatorId() {
        return OPERATOR_ID.get();
    }

    public static OperatorRole operatorRole() {
        return OPERATOR_ROLE.get();
    }

    public static void clear() {
        OPERATOR_ID.remove();
        OPERATOR_ROLE.remove();
    }
}
