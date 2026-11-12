package com.example.demo;

import java.io.IOException;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {
    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, JwtService jwt, AuthService auth) throws Exception {
        // 토큰은 쿠키가 아닌 Authorization 헤더/JSON 본문으로만 받습니다.
        http.csrf(csrf -> csrf.disable())
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(requests -> requests
                .requestMatchers(HttpMethod.POST, "/api/signup", "/api/auth/login", "/api/auth/refresh", "/api/auth/logout").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/seats").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/auth/verify-email").permitAll()
                .anyRequest().authenticated())
            .oauth2ResourceServer(resource -> resource
                .jwt(config -> config.decoder(token -> {
                    var decoded = jwt.decode(token, "access");
                    if (!auth.isActive(decoded)) throw new BadJwtException("로그아웃되었거나 만료된 세션입니다.");
                    return decoded;
                }))
                .authenticationEntryPoint((request, response, exception) -> error(response, 401, "로그인이 필요하거나 토큰이 만료되었습니다.")))
            .exceptionHandling(errors -> errors
                .authenticationEntryPoint((request, response, exception) -> error(response, 401, "로그인이 필요합니다."))
                .accessDeniedHandler((request, response, exception) -> error(response, 403, "접근 권한이 없습니다.")));
        return http.build();
    }
    private static void error(HttpServletResponse response, int status, String message) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json;charset=UTF-8");
        if (status == 401) response.setHeader("WWW-Authenticate", "Bearer");
        response.getWriter().write("{\"message\":\"" + message + "\"}");
    }
}
