package com.example.demo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

/** US-02 로그인 규칙: 학번/관리자 ID 로그인, 필수 입력 안내, 역할이 담긴 토큰(Access 1시간, Refresh 7일). */
@ExtendWith(MockitoExtension.class)
class AuthServiceTest {
    private static final String SECRET = "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=";
    private static final String PASSWORD = "abcd1234!";

    @Mock MemberRepository members;
    @Mock JdbcTemplate jdbc;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(4);
    private final JwtService jwt = new JwtService(SECRET);
    private AuthService auth;

    @BeforeEach
    void setUp() {
        auth = new AuthService(members, jdbc, jwt, encoder);
    }

    private Member active(Member member, long id) {
        ReflectionTestUtils.setField(member, "id", id);
        return member;
    }

    private static HttpStatus statusOf(Throwable error) {
        return HttpStatus.valueOf(((ResponseStatusException) error).getStatusCode().value());
    }

    @Test
    void blankIdOrPasswordAsksForRequiredInput() {
        assertThatThrownBy(() -> auth.login("", PASSWORD))
                .satisfies(e -> assertThat(statusOf(e)).isEqualTo(HttpStatus.BAD_REQUEST))
                .hasMessageContaining("입력");
        assertThatThrownBy(() -> auth.login("202404510", ""))
                .satisfies(e -> assertThat(statusOf(e)).isEqualTo(HttpStatus.BAD_REQUEST));
        verify(members, never()).findByStudentid(anyString());
    }

    @Test
    void wrongPasswordIsUnauthorized() {
        Member student = new Member("학생", "202404510", "s@univ.ac.kr", encoder.encode(PASSWORD));
        student.activate();
        when(members.findByStudentid("202404510")).thenReturn(Optional.of(active(student, 1L)));
        assertThatThrownBy(() -> auth.login("202404510", "other1234!"))
                .satisfies(e -> assertThat(statusOf(e)).isEqualTo(HttpStatus.UNAUTHORIZED));
    }

    @Test
    void pendingAccountCannotLogIn() {
        Member pending = new Member("학생", "202404510", "s@univ.ac.kr", encoder.encode(PASSWORD));
        when(members.findByStudentid("202404510")).thenReturn(Optional.of(active(pending, 1L)));
        assertThatThrownBy(() -> auth.login("202404510", PASSWORD))
                .satisfies(e -> assertThat(statusOf(e)).isEqualTo(HttpStatus.FORBIDDEN))
                .hasMessageContaining("이메일 인증");
    }

    @Test
    void studentTokenCarriesStudentRoleAndSpecTtl() {
        Member student = new Member("학생", "202404510", "s@univ.ac.kr", encoder.encode(PASSWORD));
        student.activate();
        when(members.findByStudentid("202404510")).thenReturn(Optional.of(active(student, 1L)));

        var tokens = auth.login("202404510", PASSWORD);

        var access = jwt.decode(tokens.accessToken(), "access");
        assertThat(access.getClaimAsString(JwtService.ROLE_CLAIM)).isEqualTo("ROLE_STUDENT");
        assertThat(tokens.expiresIn()).isEqualTo(3600);            // Access 1시간
        assertThat(tokens.refreshExpiresIn()).isEqualTo(7 * 24 * 3600); // Refresh 7일
        assertThat(tokens.user().role()).isEqualTo("ROLE_STUDENT");
    }

    @Test
    void adminLogsInWithAdminIdAndGetsAdminRole() {
        Member admin = Member.admin("관리자", "libadmin", "a@library.ac.kr", encoder.encode(PASSWORD));
        when(members.findByStudentid("libadmin")).thenReturn(Optional.of(active(admin, 9L)));

        var tokens = auth.login("libadmin", PASSWORD);

        assertThat(jwt.decode(tokens.accessToken(), "access").getClaimAsString(JwtService.ROLE_CLAIM))
                .isEqualTo("ROLE_ADMIN");
    }
}
