package com.example.demo.common;

import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;

/** JWT의 subject(회원 PK)를 꺼내는 공통 유틸. */
public final class CurrentUser {
    private CurrentUser() {}

    public static long id(Jwt jwt) {
        if (jwt == null) {
            throw new BusinessException(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다.");
        }
        try {
            return Long.parseLong(jwt.getSubject());
        } catch (NumberFormatException e) {
            throw new BusinessException(HttpStatus.UNAUTHORIZED, "로그인 정보가 올바르지 않습니다.");
        }
    }
}
