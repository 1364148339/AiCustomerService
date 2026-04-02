package com.aimacrodroid.common.api;

import lombok.Data;

/**
 * 通用返回结果对象
 */
@Data
public class Result<T> {
    private String code;
    private String message;
    private String requestId;
    private T data;

    protected Result() {
    }

    protected Result(String code, String message, String requestId, T data) {
        this.code = code;
        this.message = message;
        this.requestId = requestId;
        this.data = data;
    }

    /**
     * 成功返回结果
     */
    public static <T> Result<T> success(T data) {
        return new Result<>("OK", "操作成功", RequestIdHolder.next(), data);
    }

    /**
     * 成功返回结果
     */
    public static <T> Result<T> success() {
        return new Result<>("OK", "操作成功", RequestIdHolder.next(), null);
    }

    /**
     * 失败返回结果
     */
    public static <T> Result<T> failed(String message) {
        return new Result<>("INTERNAL_ERROR", message, RequestIdHolder.next(), null);
    }

    /**
     * 失败返回结果
     */
    public static <T> Result<T> failed(long code, String message) {
        return new Result<>(String.valueOf(code), message, RequestIdHolder.next(), null);
    }

    public static <T> Result<T> failed(String code, String message) {
        return new Result<>(code, message, RequestIdHolder.next(), null);
    }

    private static class RequestIdHolder {
        private static String next() {
            return "req-" + java.util.UUID.randomUUID();
        }
    }
}
