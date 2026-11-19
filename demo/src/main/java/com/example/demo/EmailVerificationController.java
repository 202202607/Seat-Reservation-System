package com.example.demo;

import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
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

    /** 설계서 p.10 경로는 POST /api/auth/email/send 이다. 기존 경로도 함께 받는다. */
    @PostMapping({"/email/send", "/resend-verification"})
    public Map<String, String> resend(@RequestBody ResendRequest request) {
        verificationService.resendVerificationEmail(request.email());
        return Map.of("message", "인증 메일 발송 요청을 처리했습니다.");
    }

    public record ResendRequest(String email) {}
}
