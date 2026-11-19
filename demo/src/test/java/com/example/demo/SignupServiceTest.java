package com.example.demo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.mail.MailSendException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

/** US-01 회원가입 규칙. DB·메일은 Mock 으로 대체한다. */
@ExtendWith(MockitoExtension.class)
class SignupServiceTest {
    private static final String ADMIN_CODE = "admin-code-1234";
    private static final String GOOD_PASSWORD = "abcd1234!";

    @Mock MemberRepository repository;
    @Mock EmailVerificationService mail;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(4);
    private SignupService service;

    @BeforeEach
    void setUp() {
        service = new SignupService(repository, mail, encoder, ADMIN_CODE, "o.cnu.ac.kr");
    }

    private static SignupService.SignupRequest student(String studentid, String email, String password) {
        return new SignupService.SignupRequest("홍길동", studentid, email, password, null, null);
    }

    private static SignupService.SignupRequest admin(String adminId, String code) {
        return new SignupService.SignupRequest("관리자", null, "admin@library.ac.kr", GOOD_PASSWORD, adminId, code);
    }

    private static HttpStatus statusOf(Throwable error) {
        return HttpStatus.valueOf(((ResponseStatusException) error).getStatusCode().value());
    }

    @Test
    void studentSignupIsPendingStudentWithBcryptHashAndSendsMail() {
        var result = service.signup(student("202404510", "Student@O.CNU.ac.kr", GOOD_PASSWORD));

        ArgumentCaptor<Member> saved = ArgumentCaptor.forClass(Member.class);
        verify(repository).saveAndFlush(saved.capture());
        Member member = saved.getValue();
        assertThat(result).isEqualTo(SignupService.Result.STUDENT_PENDING);
        assertThat(member.getStatus()).isEqualTo("PENDING");
        assertThat(member.getRole()).isEqualTo("ROLE_STUDENT");
        assertThat(member.getEmail()).isEqualTo("student@o.cnu.ac.kr"); // 대문자 이메일도 소문자로 받아 준다
        assertThat(member.getPasswordHash()).isNotEqualTo(GOOD_PASSWORD).startsWith("$2");
        assertThat(encoder.matches(GOOD_PASSWORD, member.getPasswordHash())).isTrue();
        verify(mail).sendVerificationEmail(member);
    }

    @Test
    void adminSignupWithCorrectCodeIsActiveAdminWithoutMail() {
        var result = service.signup(admin("libadmin", ADMIN_CODE));

        ArgumentCaptor<Member> saved = ArgumentCaptor.forClass(Member.class);
        verify(repository).saveAndFlush(saved.capture());
        assertThat(result).isEqualTo(SignupService.Result.ADMIN_ACTIVE);
        assertThat(saved.getValue().getStatus()).isEqualTo("ACTIVE");
        assertThat(saved.getValue().getRole()).isEqualTo("ROLE_ADMIN");
        assertThat(saved.getValue().getStudentid()).isEqualTo("libadmin");
        assertThat(encoder.matches(GOOD_PASSWORD, saved.getValue().getPasswordHash())).isTrue();
        verify(mail, never()).sendVerificationEmail(any());
    }

    @Test
    void adminSignupWithWrongCodeIsForbidden() {
        assertThatThrownBy(() -> service.signup(admin("libadmin", "wrong")))
                .satisfies(e -> assertThat(statusOf(e)).isEqualTo(HttpStatus.FORBIDDEN));
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void adminSignupIsClosedWhenNoCodeConfigured() {
        SignupService closed = new SignupService(repository, mail, encoder, "", "o.cnu.ac.kr");
        assertThatThrownBy(() -> closed.signup(admin("libadmin", "anything")))
                .satisfies(e -> assertThat(statusOf(e)).isEqualTo(HttpStatus.FORBIDDEN));
    }

    @ParameterizedTest
    @ValueSource(strings = {"abc12!", "abcdefgh!", "12345678!", "abcd12345", ""})
    void rejectsWeakPasswords(String password) {
        assertThatThrownBy(() -> service.signup(student("202404510", "s@o.cnu.ac.kr", password)))
                .satisfies(e -> assertThat(statusOf(e)).isEqualTo(HttpStatus.BAD_REQUEST));
        verify(repository, never()).saveAndFlush(any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"abcd1234?", "abcd1234_", "abcd1234.", "abcd1234~"})
    void acceptsAnySpecialCharacter(String password) {
        service.signup(student("202404510", "s@o.cnu.ac.kr", password));
        verify(repository).saveAndFlush(any());
    }

    @Test
    void wrongStudentIdExplainsStudentIdNotName() {
        assertThatThrownBy(() -> service.signup(student("12345", "s@o.cnu.ac.kr", GOOD_PASSWORD)))
                .hasMessageContaining("학번");
    }

    @Test
    void rejectsAlreadyRegisteredEmailOrStudentId() {
        when(repository.existsByEmail("s@o.cnu.ac.kr")).thenReturn(true);
        assertThatThrownBy(() -> service.signup(student("202404510", "s@o.cnu.ac.kr", GOOD_PASSWORD)))
                .satisfies(e -> assertThat(statusOf(e)).isEqualTo(HttpStatus.CONFLICT))
                .hasMessageContaining("이메일");

        when(repository.existsByEmail("t@o.cnu.ac.kr")).thenReturn(false);
        when(repository.existsByStudentid("202404510")).thenReturn(true);
        assertThatThrownBy(() -> service.signup(student("202404510", "t@o.cnu.ac.kr", GOOD_PASSWORD)))
                .satisfies(e -> assertThat(statusOf(e)).isEqualTo(HttpStatus.CONFLICT))
                .hasMessageContaining("학번");
    }

    @Test
    void concurrentDuplicateSignupIsConflictNotServerError() {
        when(repository.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("unique"));
        assertThatThrownBy(() -> service.signup(student("202404510", "s@o.cnu.ac.kr", GOOD_PASSWORD)))
                .satisfies(e -> assertThat(statusOf(e)).isEqualTo(HttpStatus.CONFLICT));
    }

    @Test
    void mailFailureAbortsSignupSoTransactionRollsBack() {
        doThrow(new MailSendException("smtp down")).when(mail).sendVerificationEmail(any());
        assertThatThrownBy(() -> service.signup(student("202404510", "s@o.cnu.ac.kr", GOOD_PASSWORD)))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(statusOf(e)).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE));
    }

    /** US-01: 학생은 학교 이메일(@o.cnu.ac.kr)로만 가입한다. 관리자 가입에는 적용하지 않는다. */
    @ParameterizedTest
    @ValueSource(strings = {"student@gmail.com", "student@cnu.ac.kr", "student@o.cnu.ac.kr.evil.com", "student@xo.cnu.ac.kr"})
    void studentMustUseSchoolEmailDomain(String email) {
        assertThatThrownBy(() -> service.signup(student("202404510", email, GOOD_PASSWORD)))
                .satisfies(e -> assertThat(statusOf(e)).isEqualTo(HttpStatus.BAD_REQUEST))
                .hasMessageContaining("@o.cnu.ac.kr");
        verify(repository, never()).saveAndFlush(any());
    }
}
