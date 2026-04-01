package com.wq.mobiletaskagent.client;

public class ModelClientException extends RuntimeException {
    private final String errorCode;

    public ModelClientException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public ModelClientException(String errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    public String getErrorCode() {
        return errorCode;
    }
}
