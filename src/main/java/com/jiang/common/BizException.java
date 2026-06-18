package com.jiang.common;

import lombok.Getter;

/**
 * 业务异常，统一由 GlobalExceptionHandler 捕获并转换为 Result。
 */
@Getter
public class BizException extends RuntimeException {

    private final int code;

    public BizException(String message) {
        super(message);
        this.code = 500;
    }

    public BizException(int code, String message) {
        super(message);
        this.code = code;
    }
}
