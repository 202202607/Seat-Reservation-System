package com.example.demo;

import jakarta.persistence.*;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.util.Map;
import java.util.Optional;
import java.util.Locale;

import java.time.LocalDateTime;
import java.util.UUID;


// 회원 테이블
@Entity
@Table(name = "APP_MEMBERS")
class Member {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;

    @Column(unique = true, nullable = false)
    private String studentid;

    @Column(unique = true, nullable = false)
    private String email;

    private String passwordHash;

    private String status;

    private String role;

    public Long getId() { return id; }
    public String getName() { return name; }
    public String getStudentid() { return studentid; }
    public String getPasswordHash() { return passwordHash; }
    public String getStatus() { return status; }
    public String getRole() { return role; }

    protected Member() {}

    public Member(String name, String studentid, String email, String passwordHash) {
        this.name = name;
        this.studentid = studentid;
        this.email = email;
        this.passwordHash = passwordHash;
        this.status = "PENDING";
        this.role = "ROLE_STUDENT";
        issueVerificationToken();
    }

    @Column(unique = true)
    private String verificationToken;

    private LocalDateTime verificationTokenExpiresAt;

    public String getEmail() {
        return email;
    }

    public String getVerificationToken() {
        return verificationToken;
    }

    public void issueVerificationToken() {
        this.verificationToken = UUID.randomUUID().toString();
        this.verificationTokenExpiresAt =
                LocalDateTime.now().plusMinutes(30);
    }

    public boolean isVerificationExpired() {
        return verificationTokenExpiresAt == null ||
                LocalDateTime.now().isAfter(verificationTokenExpiresAt);
    }

    public void activate() {
        this.status = "ACTIVE";
        this.verificationToken = null;
        this.verificationTokenExpiresAt = null;
    }
}

// DB 저장 및 조회
interface MemberRepository extends JpaRepository<Member, Long> {
    java.util.Optional<Member> findByStudentid(String studentid);
    boolean existsByEmail(String email);
    boolean existsByStudentid(String studentid);
    Optional<Member> findByVerificationToken(String token);

    Optional<Member> findByEmail(String email);
}

// 회원가입 API
@RestController
@RequestMapping("/api")
public class SignupController {

    private final EmailVerificationService verificationService;
    private final MemberRepository repository;
    private final BCryptPasswordEncoder encoder =
            new BCryptPasswordEncoder();

    public SignupController(MemberRepository repository, EmailVerificationService verification) {
        this.repository = repository;
        this.verificationService = verification;
    }

    @PostMapping("/signup")
    public ResponseEntity<?> signup(@RequestBody SignupRequest req) {
        String password = req.password();
        if (req.name() == null || req.name().isBlank()
            || req.studentid() == null 
            || !req.studentid().matches("^\\d{9}$")) {
            return ResponseEntity.badRequest().body(Map.of("message", " 이름을 입력하세요."));
        }
        if (req.email() == null || !req.email().matches("^[a-z0-9_-]+(\\.[a-z0-9_-]+)*@(?:\\w+\\.)+\\w+$")){
            return ResponseEntity.badRequest().body(Map.of("message", "이메일 입력값을 확인하세요."));
        }
        if (password == null || password.isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("message", "비밀번호를 입력하세요."));
        }
        if (password.length() < 8) {
            return ResponseEntity.badRequest()
                    .body(Map.of("message", "비밀번호는 8자 이상이어야 합니다."));
        }
        if (!password.matches(".*[0-9].*")) {
            return ResponseEntity.badRequest()
                    .body(Map.of("message", "비밀번호에 숫자가 포함되어 있어야 합니다."));
        }
        if (!password.matches(".*[a-zA-Z].*")) {
            return ResponseEntity.badRequest()
                    .body(Map.of("message", "비밀번호에 영문자가 포함되어 있어야 합니다."));
        }
        if (!password.matches(".*[!@#$%^&*()+=-].*")) {
            return ResponseEntity.badRequest()
                    .body(Map.of("message", "비밀번호에 특수문자가 포함되어 있어야 합니다."));
        }

        String email = req.email().trim().toLowerCase(Locale.ROOT);
        String studentid = req.studentid().trim().toLowerCase(Locale.ROOT);
        if (repository.existsByEmail(email)) {
            return ResponseEntity.status(409)
                    .body(Map.of("message", "이미 가입된 이메일입니다."));
        }
        if (repository.existsByStudentid(studentid)) {
            return ResponseEntity.status(409)
                    .body(Map.of("message", "이미 가입된 학번입니다."));
        }

        Member member = new Member(
                req.name().trim(),
                req.studentid().trim(),
                email,
                encoder.encode(req.password())
        );

        repository.save(member);

        verificationService.sendVerificationEmail(member);

        return ResponseEntity.status(201)
                .body(Map.of("message", "회원가입 성공 - 이메일 인증을 완료하셔야 로그인할 수 있습니다."));
    }

    public record SignupRequest(
            String name,
            String studentid,
            String email,
            String password
    ) {}
}