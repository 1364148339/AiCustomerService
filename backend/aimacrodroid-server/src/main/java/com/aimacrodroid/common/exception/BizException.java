package com.aimacrodroid.common.exception;

import lombok.Getter;

@Getter
public class BizException extends RuntimeException {
    private final String code;
    private final int httpStatus;

    public BizException(String code, String message) {
        this(code, message, 400);
    }

    public BizException(String code, String message, int httpStatus) {
        super(message);
        this.code = code;
        this.httpStatus = httpStatus;
    }
}
