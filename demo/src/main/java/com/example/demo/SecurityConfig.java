package com.example.demo;

import java.io.IOException;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;

import jakarta.servlet.DispatcherType;

/**
 * 인증·권한 규칙 (US-02).
 * <ul>
 *   <li>AC3: 회원가입·이메일 인증·로그인(토큰 갱신·로그아웃 포함)을 제외한 API는 토큰이 필요하다.</li>
 *   <li>AC4: JWT 의 role 클레임(ROLE_STUDENT / ROLE_ADMIN)으로 역할을 나눈다. 권한은 설계서 p.10~12 API 표를 따른다.</li>
 * </ul>
 */
@Configuration
public class SecurityConfig {
    private static final String STUDENT = "STUDENT";
    private static final String ADMIN = "ADMIN";

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, JwtService jwt, AuthService auth) throws Exception {
        // 토큰은 쿠키가 아닌 Authorization 헤더/JSON 본문으로만 받습니다.
        http.csrf(csrf -> csrf.disable())
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(requests -> requests
                .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                // SSE(/api/seats/stream)의 비동기 재디스패치. 최초 요청에서 이미 인증을 통과했다.
                .dispatcherTypeMatchers(DispatcherType.ASYNC).permitAll()
                // US-01·US-02: 가입·인증·로그인은 토큰 없이 허용
                .requestMatchers(HttpMethod.POST, "/api/auth/signup", "/api/signup",
                        "/api/auth/email/send", "/api/auth/resend-verification",
                        "/api/auth/login", "/api/auth/refresh", "/api/auth/logout").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/auth/verify-email").permitAll()
                // 관리자 전용 (US-03, US-12~18)
                .requestMatchers("/api/admin/**").hasRole(ADMIN)
                // 학생 전용: 예약 생성(US-05), 내 예약 조회·취소(US-06, US-09), 이후 QR·체크인(US-08)
                .requestMatchers("/api/reservations", "/api/reservations/**", "/api/check-ins").hasRole(STUDENT)
                // 좌석 조회·실시간 스트림(US-04)은 학생·관리자 모두 → 로그인만 확인
                .anyRequest().authenticated())
            .oauth2ResourceServer(resource -> resource
                .jwt(config -> config
                    .decoder(token -> {
                        var decoded = jwt.decode(token, "access");
                        if (!auth.isActive(decoded)) throw new BadJwtException("로그아웃되었거나 만료된 세션입니다.");
                        return decoded;
                    })
                    .jwtAuthenticationConverter(roleConverter()))
                .authenticationEntryPoint((request, response, exception) -> error(response, 401, "로그인이 필요하거나 토큰이 만료되었습니다."))
                .accessDeniedHandler((request, response, exception) -> error(response, 403, "접근 권한이 없습니다.")))
            .exceptionHandling(errors -> errors
                .authenticationEntryPoint((request, response, exception) -> error(response, 401, "로그인이 필요합니다."))
                .accessDeniedHandler((request, response, exception) -> error(response, 403, "접근 권한이 없습니다.")));
        return http.build();
    }

    /** JWT 의 role 클레임 값(ROLE_STUDENT 등)을 그대로 권한으로 쓴다. 기본값(scope → SCOPE_)은 쓰지 않는다. */
    static JwtAuthenticationConverter roleConverter() {
        JwtGrantedAuthoritiesConverter authorities = new JwtGrantedAuthoritiesConverter();
        authorities.setAuthoritiesClaimName(JwtService.ROLE_CLAIM);
        authorities.setAuthorityPrefix("");
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(authorities);
        return converter;
    }

    /** 학생·관리자 비밀번호 모두 BCrypt 해시로 저장한다 (US-01 AC5). */
    @Bean
    BCryptPasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    private static void error(HttpServletResponse response, int status, String message) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json;charset=UTF-8");
        if (status == 401) response.setHeader("WWW-Authenticate", "Bearer");
        response.getWriter().write("{\"message\":\"" + message + "\"}");
    }
}
