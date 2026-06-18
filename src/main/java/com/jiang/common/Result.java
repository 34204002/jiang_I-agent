package com.jiang.common;

import lombok.Data;

/**
 * 统一 API 响应体。
 * <p>
 * 所有 Controller 返回值统一包装，前端解析格式固定：
 * <pre>{@code
 * {"code": 200, "message": "success", "data": {...}}
 * }</pre>
 * </p>
 */
@Data
public class Result<T> {

    private int code;
    private String message;
    private T data;

    private Result(int code, String message, T data) {
        this.code = code;
        this.message = message;
        this.data = data;
    }

    // ==================== 工厂方法 ====================

    public static <T> Result<T> success(T data) {
        return new Result<>(200, "success", data);
    }

    public static <T> Result<T> success() {
        return new Result<>(200, "success", null);
    }

    public static <T> Result<T> fail(int code, String message) {
        return new Result<>(code, message, null);
    }

    public static <T> Result<T> fail(String message) {
        return new Result<>(500, message, null);
    }
}
