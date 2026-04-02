package com.aimacrodroid.security;

import java.util.Locale;

public enum OperatorRole {
    ADMIN,
    OPS,
    READONLY;

    public static OperatorRole from(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return OperatorRole.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (Exception ex) {
            return null;
        }
    }
}
