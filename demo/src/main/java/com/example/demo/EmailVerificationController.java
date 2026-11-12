package com.example.demo;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class EmailVerificationController {

    private final EmailVerificationService verificationService;

    public EmailVerificationController(
            EmailVerificationService verificationService) {
        this.verificationService = verificationService;
    }

    @GetMapping("/verify-email")
    public String verifyEmail(@RequestParam String token) {
        verificationService.verify(token);
        return "이메일 인증이 완료되었습니다. 이제 로그인할 수 있습니다.";
    }
}
