package com.example.demo.common;

import org.springframework.http.HttpStatus;

/**
 * 비즈니스 규칙 위반을 나타내는 예외.
 * Service 계층이 웹 계층(ResponseStatusException)에 의존하지 않도록 상태 코드만 함께 전달한다.
 */
public class BusinessException extends RuntimeException {
    private final HttpStatus status;

    public BusinessException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public static BusinessException badRequest(String message) {
        return new BusinessException(HttpStatus.BAD_REQUEST, message);
    }

    public static BusinessException notFound(String message) {
        return new BusinessException(HttpStatus.NOT_FOUND, message);
    }

    public static BusinessException conflict(String message) {
        return new BusinessException(HttpStatus.CONFLICT, message);
    }
}
