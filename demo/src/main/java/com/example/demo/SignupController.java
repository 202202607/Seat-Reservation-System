package com.example.demo;

import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

// 회원가입 API
@RestController
@RequestMapping("/api")
public class SignupController {
    private final SignupService signupService;

    public SignupController(SignupService signupService) {
        this.signupService = signupService;
    }

    @PostMapping("/signup")
    public ResponseEntity<?> signup(@RequestBody SignupService.SignupRequest req) {
        signupService.signup(req);
        return ResponseEntity.status(201)
                .body(Map.of("message", "회원가입 성공 - 이메일 인증을 완료하셔야 로그인할 수 있습니다."));
    }
}
