package com.example.demo;

import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * 회원가입 API (US-01).
 * 설계서 p.10 경로는 POST /api/auth/signup 이다. 기존 화면이 쓰던 /api/signup 도 당분간 함께 받는다.
 */
@RestController
public class SignupController {
    private final SignupService signupService;

    public SignupController(SignupService signupService) {
        this.signupService = signupService;
    }

    @PostMapping({"/api/auth/signup", "/api/signup"})
    public ResponseEntity<Map<String, String>> signup(@RequestBody SignupService.SignupRequest req) {
        String message = switch (signupService.signup(req)) {
            case ADMIN_ACTIVE -> "관리자 가입이 완료되었습니다. 바로 로그인할 수 있습니다.";
            case STUDENT_PENDING -> "회원가입 성공 - 이메일 인증을 완료하셔야 로그인할 수 있습니다.";
        };
        return ResponseEntity.status(201).body(Map.of("message", message));
    }
}
