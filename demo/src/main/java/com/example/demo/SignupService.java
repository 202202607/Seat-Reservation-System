package com.example.demo;

import java.util.Locale;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/** 회원가입 규칙 (US-01). 컨트롤러는 요청·응답 변환만 한다. */
@Service
public class SignupService {
    private final MemberRepository repository;
    private final EmailVerificationService verificationService;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    public SignupService(MemberRepository repository, EmailVerificationService verificationService) {
        this.repository = repository;
        this.verificationService = verificationService;
    }

    public record SignupRequest(String name, String studentid, String email, String password) {}

    public void signup(SignupRequest req) {
        String password = req.password();
        if (req.name() == null || req.name().isBlank()
            || req.studentid() == null
            || !req.studentid().matches("^\\d{9}$")) {
            throw badRequest(" 이름을 입력하세요.");
        }
        if (req.email() == null || !req.email().matches("^[a-z0-9_-]+(\\.[a-z0-9_-]+)*@(?:\\w+\\.)+\\w+$")) {
            throw badRequest("이메일 입력값을 확인하세요.");
        }
        if (password == null || password.isEmpty()) {
            throw badRequest("비밀번호를 입력하세요.");
        }
        if (password.length() < 8) {
            throw badRequest("비밀번호는 8자 이상이어야 합니다.");
        }
        if (!password.matches(".*[0-9].*")) {
            throw badRequest("비밀번호에 숫자가 포함되어 있어야 합니다.");
        }
        if (!password.matches(".*[a-zA-Z].*")) {
            throw badRequest("비밀번호에 영문자가 포함되어 있어야 합니다.");
        }
        if (!password.matches(".*[!@#$%^&*()+=-].*")) {
            throw badRequest("비밀번호에 특수문자가 포함되어 있어야 합니다.");
        }

        String email = req.email().trim().toLowerCase(Locale.ROOT);
        String studentid = req.studentid().trim().toLowerCase(Locale.ROOT);
        if (repository.existsByEmail(email)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "이미 가입된 이메일입니다.");
        }
        if (repository.existsByStudentid(studentid)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "이미 가입된 학번입니다.");
        }

        Member member = new Member(req.name().trim(), req.studentid().trim(), email, encoder.encode(req.password()));
        repository.save(member);
        verificationService.sendVerificationEmail(member);
    }

    private static ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}
