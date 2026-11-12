package com.example.demo;

import java.util.Map;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthService auth;
    public AuthController(AuthService auth) { this.auth = auth; }
    public record LoginRequest(String studentid, String password) {}
    public record RefreshRequest(String refreshToken) {}

    @PostMapping("/login")
    public ResponseEntity<AuthService.TokenResponse> login(@RequestBody LoginRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(auth.login(request.studentid(), request.password()));
    }
    @PostMapping("/refresh")
    public ResponseEntity<AuthService.TokenResponse> refresh(@RequestBody RefreshRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(auth.refresh(request.refreshToken()));
    }
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@RequestBody RefreshRequest request) {
        auth.logout(request.refreshToken());
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }
    @GetMapping("/me")
    public Map<String, Object> me(@AuthenticationPrincipal Jwt user) {
        return Map.of("id", Long.parseLong(user.getSubject()));
    }
}

// 인증 실패 사유를 프런트엔드가 읽을 수 있는 JSON으로 통일합니다.
@org.springframework.web.bind.annotation.RestControllerAdvice(assignableTypes = AuthController.class)
class AuthErrorHandler {
    @org.springframework.web.bind.annotation.ExceptionHandler(org.springframework.web.server.ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> handle(org.springframework.web.server.ResponseStatusException error) {
        return ResponseEntity.status(error.getStatusCode()).cacheControl(CacheControl.noStore())
                .body(Map.of("message", error.getReason() == null ? "인증 요청에 실패했습니다." : error.getReason()));
    }
}
