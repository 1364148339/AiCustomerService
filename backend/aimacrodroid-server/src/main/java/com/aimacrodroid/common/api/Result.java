package com.aimacrodroid.common.api;

import lombok.Data;

/**
 * 通用返回结果对象
 */
@Data
public class Result<T> {
    private long code;
    private String message;
    private T data;

    protected Result() {
    }

    protected Result(long code, String message, T data) {
        this.code = code;
        this.message = message;
        this.data = data;
    }

    /**
     * 成功返回结果
     */
    public static <T> Result<T> success(T data) {
        return new Result<>(200, "操作成功", data);
    }

    /**
     * 成功返回结果
     */
    public static <T> Result<T> success() {
        return new Result<>(200, "操作成功", null);
    }

    /**
     * 失败返回结果
     */
    public static <T> Result<T> failed(String message) {
        return new Result<>(500, message, null);
    }

    /**
     * 失败返回结果
     */
    public static <T> Result<T> failed(long code, String message) {
        return new Result<>(code, message, null);
    }
}
