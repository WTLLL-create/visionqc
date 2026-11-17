package com.visionqc.backend.common;

/** 业务异常：由全局异常处理器统一转为 HTTP 400/404/409 等结构化错误 */
public class BusinessException extends RuntimeException {
    private final int httpStatus;

    public BusinessException(int httpStatus, String message) {
        super(message);
        this.httpStatus = httpStatus;
    }

    public int getHttpStatus() {
        return httpStatus;
    }

    public static BusinessException notFound(String message) {
        return new BusinessException(404, message);
    }

    public static BusinessException conflict(String message) {
        return new BusinessException(409, message);
    }

    public static BusinessException badRequest(String message) {
        return new BusinessException(400, message);
    }

    public static BusinessException forbidden(String message) {
        return new BusinessException(403, message);
    }
}
