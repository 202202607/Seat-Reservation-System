package com.example.demo;

import java.util.Map;

import com.example.demo.common.BusinessException;

import org.springframework.web.server.ResponseStatusException;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> handle(
            ResponseStatusException e) {

        return ResponseEntity
                .status(e.getStatusCode())
                .body(Map.of(
                    "message",
                    e.getReason() != null
                        ? e.getReason()
                        : "요청 처리에 실패했습니다."
                ));
    }

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<Map<String, String>> handleBusiness(BusinessException e) {
        return ResponseEntity
                .status(e.getStatus())
                .body(Map.of("message", e.getMessage()));
    }
}