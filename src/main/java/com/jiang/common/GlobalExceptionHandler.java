package com.jiang.common;

import com.jiang.exception.BusinessException;
import com.jiang.exception.NotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * 全局异常处理，拦截所有 Controller 层抛出的异常，返回统一 Result 格式。
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 忽略浏览器自动请求的静态资源（favicon.ico 等）
     */
    @ExceptionHandler(NoResourceFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public Result<Void> handleNoResource(NoResourceFoundException e) {
        // 不打印日志，这是正常的浏览器行为
        return Result.fail(404, "not found");
    }

    /**
     * 资源不存在
     */
    @ExceptionHandler(NotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public Result<Void> handleNotFound(NotFoundException e) {
        log.warn("资源不存在: {}", e.getMessage());
        return Result.fail(404, e.getMessage());
    }

    /**
     * 业务逻辑异常
     */
    @ExceptionHandler(BusinessException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<Void> handleBusiness(BusinessException e) {
        log.warn("业务异常: {}", e.getMessage());
        return Result.fail(400, e.getMessage());
    }

    /**
     * 请求参数不合法
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public Result<Void> handleIllegalArgument(IllegalArgumentException e) {
        log.warn("参数异常: {}", e.getMessage());
        return Result.fail(400, e.getMessage());
    }

    /**
     * 未预期的内部异常兜底
     */
    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Result<Void> handleUnknown(Exception e) {
        log.error("未知异常", e);
        return Result.fail(500, "服务器内部错误，请稍后再试");
    }
}
