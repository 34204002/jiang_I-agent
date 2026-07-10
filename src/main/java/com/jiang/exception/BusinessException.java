package com.jiang.exception;

/** 业务逻辑异常 */
public class BusinessException extends RuntimeException {
    public BusinessException(String message) { super(message); }
}
