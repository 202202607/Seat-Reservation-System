package com.example.demo;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.mail.MailException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * 회원가입 규칙 (US-01). 컨트롤러는 요청·응답 변환만 한다.
 * <ul>
 *   <li>학생: 학교 이메일(@o.cnu.ac.kr)만 허용. PENDING + ROLE_STUDENT 로 저장하고 인증 링크 메일 발송 (AC1). 인증하면 ACTIVE (AC4).</li>
 *   <li>관리자: 관리자 가입 코드가 맞으면 ACTIVE + ROLE_ADMIN 으로 저장 (AC5, 설계서 p.4 Core Flow 1).</li>
 *   <li>비밀번호는 8자 이상·영문·숫자·특수문자 포함 (AC2), BCrypt 해시로 저장 (AC5).</li>
 * </ul>
 */
@Service
public class SignupService {
    /** 학번: 숫자 9자리. */
    static final String STUDENT_ID_PATTERN = "\\d{9}";
    /** 관리자 ID: 영문으로 시작하는 4~20자 (학번과 겹치지 않도록 숫자로 시작할 수 없음). */
    static final String ADMIN_ID_PATTERN = "[A-Za-z][A-Za-z0-9_]{3,19}";
    private static final String EMAIL_PATTERN = "^[a-z0-9_-]+(\\.[a-z0-9_-]+)*@(?:[a-z0-9-]+\\.)+[a-z0-9-]+$";

    private final MemberRepository repository;
    private final EmailVerificationService verificationService;
    private final BCryptPasswordEncoder encoder;
    private final String adminSignupCode;
    /** 학생 가입에 허용하는 학교 이메일 도메인 (US-01 "학교 이메일로 본인 인증"). 예: o.cnu.ac.kr */
    private final String studentEmailDomain;

    public SignupService(MemberRepository repository, EmailVerificationService verificationService,
                         BCryptPasswordEncoder encoder,
                         @Value("${app.admin.signup-code:}") String adminSignupCode,
                         @Value("${app.signup.student-email-domain:o.cnu.ac.kr}") String studentEmailDomain) {
        this.repository = repository;
        this.verificationService = verificationService;
        this.encoder = encoder;
        this.adminSignupCode = adminSignupCode == null ? "" : adminSignupCode.trim();
        this.studentEmailDomain = studentEmailDomain.trim().toLowerCase(Locale.ROOT);
    }

    /** adminId·adminCode 는 관리자 가입일 때만 채운다. 학생 가입은 studentid 를 쓴다. */
    public record SignupRequest(String name, String studentid, String email, String password,
                                String adminId, String adminCode) {
        public boolean isAdmin() {
            return adminCode != null && !adminCode.isBlank();
        }
    }

    public enum Result { STUDENT_PENDING, ADMIN_ACTIVE }

    @Transactional
    public Result signup(SignupRequest req) {
        boolean admin = req.isAdmin();
        if (admin && !adminCodeMatches(req.adminCode())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "관리자 가입 코드가 올바르지 않습니다.");
        }

        String name = req.name() == null ? "" : req.name().trim();
        if (name.isEmpty()) {
            throw badRequest("이름을 입력하세요.");
        }
        String loginId = (admin ? req.adminId() : req.studentid());
        loginId = loginId == null ? "" : loginId.trim();
        if (admin && !loginId.matches(ADMIN_ID_PATTERN)) {
            throw badRequest("관리자 ID는 영문으로 시작하는 4~20자의 영문·숫자·_ 로 입력하세요.");
        }
        if (!admin && !loginId.matches(STUDENT_ID_PATTERN)) {
            throw badRequest("학번은 숫자 9자리로 입력하세요.");
        }
        String email = req.email() == null ? "" : req.email().trim().toLowerCase(Locale.ROOT);
        if (!email.matches(EMAIL_PATTERN)) {
            throw badRequest("이메일 입력값을 확인하세요.");
        }
        if (!admin && !email.endsWith("@" + studentEmailDomain)) {
            throw badRequest("학교 이메일(@" + studentEmailDomain + ")로만 가입할 수 있습니다.");
        }
        validatePassword(req.password());

        if (repository.existsByEmail(email)) {
            throw conflict("이미 가입된 이메일입니다.");
        }
        if (repository.existsByStudentid(loginId)) {
            throw conflict(admin ? "이미 사용 중인 관리자 ID입니다." : "이미 가입된 학번입니다.");
        }

        String hash = encoder.encode(req.password());
        Member member = admin ? Member.admin(name, loginId, email, hash) : new Member(name, loginId, email, hash);
        try {
            // 동시에 같은 학번·이메일로 가입하면 UNIQUE 제약에서 걸린다 → 500 대신 409.
            repository.saveAndFlush(member);
        } catch (DataIntegrityViolationException e) {
            throw conflict("이미 가입된 학번(관리자 ID) 또는 이메일입니다.");
        }
        if (admin) {
            return Result.ADMIN_ACTIVE;
        }
        try {
            // 메일 발송이 실패하면 트랜잭션을 롤백해, 인증할 수 없는 PENDING 계정이 남지 않게 한다.
            verificationService.sendVerificationEmail(member);
        } catch (MailException e) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "인증 메일을 보내지 못해 가입이 취소되었습니다. 잠시 후 다시 시도해 주세요.");
        }
        return Result.STUDENT_PENDING;
    }

    /** AC2: 8자 이상, 영문·숫자·특수문자 모두 포함. BCrypt 는 72바이트까지만 쓰므로 상한도 둔다. */
    static void validatePassword(String password) {
        if (password == null || password.isEmpty()) {
            throw badRequest("비밀번호를 입력하세요.");
        }
        if (password.length() < 8) {
            throw badRequest("비밀번호는 8자 이상이어야 합니다.");
        }
        if (password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw badRequest("비밀번호가 너무 깁니다. (영문 기준 72자 이하)");
        }
        if (!password.matches(".*[0-9].*")) {
            throw badRequest("비밀번호에 숫자가 포함되어 있어야 합니다.");
        }
        if (!password.matches(".*[a-zA-Z].*")) {
            throw badRequest("비밀번호에 영문자가 포함되어 있어야 합니다.");
        }
        if (!password.matches(".*[^A-Za-z0-9\\s].*")) {
            throw badRequest("비밀번호에 특수문자가 포함되어 있어야 합니다.");
        }
    }

    /** 설정(ADMIN_SIGNUP_CODE)이 비어 있으면 관리자 가입은 막힌다. 비교는 시간 차이가 나지 않게 한다. */
    private boolean adminCodeMatches(String code) {
        if (adminSignupCode.isEmpty()) {
            return false;
        }
        return MessageDigest.isEqual(adminSignupCode.getBytes(StandardCharsets.UTF_8),
                code.trim().getBytes(StandardCharsets.UTF_8));
    }

    private static ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }

    private static ResponseStatusException conflict(String message) {
        return new ResponseStatusException(HttpStatus.CONFLICT, message);
    }
}
