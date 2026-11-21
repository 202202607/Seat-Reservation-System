package com.example.demo;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.Filter;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.context.junit.jupiter.web.SpringJUnitWebConfig;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

/**
 * US-02 AC3·AC4: 실제 SecurityConfig 규칙을 DB 없이 검증한다.
 * 토큰 검증(JwtService)과 세션 확인(AuthService)만 Mock 으로 바꾸고, 컨트롤러는 빈 응답을 주는 스텁이다.
 */
@SpringJUnitWebConfig(SecurityRulesTest.TestConfig.class)
class SecurityRulesTest {
    private static final String STUDENT = "student-token";
    private static final String ADMIN = "admin-token";

    @Configuration
    @EnableWebMvc
    @EnableWebSecurity
    @Import(SecurityConfig.class)
    static class TestConfig {
        @Bean JwtService jwtService() {
            JwtService jwt = mock(JwtService.class);
            doThrow(new BadJwtException("bad")).when(jwt).decode(anyString(), eq("access"));
            doReturn(token("1", "ROLE_STUDENT")).when(jwt).decode(eq(STUDENT), eq("access"));
            doReturn(token("9", "ROLE_ADMIN")).when(jwt).decode(eq(ADMIN), eq("access"));
            return jwt;
        }

        @Bean AuthService authService() {
            AuthService auth = mock(AuthService.class);
            when(auth.isActive(any())).thenReturn(true);
            return auth;
        }

        @Bean StubController stubController() {
            return new StubController();
        }

        private static Jwt token(String subject, String role) {
            return Jwt.withTokenValue("t").header("alg", "HS256").subject(subject)
                    .claim(JwtService.ROLE_CLAIM, role).claim("sid", "s")
                    .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(3600)).build();
        }
    }

    /** 경로 규칙만 확인하려고 모든 경로에 200 을 주는 스텁. */
    @RestController
    static class StubController {
        @RequestMapping({"/api/admin/dashboard", "/api/reservations", "/api/reservations/my",
                "/api/reservations/1/cancel", "/api/reservations/1", "/api/seats", "/api/auth/me",
                "/api/auth/signup", "/api/auth/login", "/api/auth/verify-email"})
        String ok() {
            return "ok";
        }
    }

    @Autowired WebApplicationContext context;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context)
                .addFilters(context.getBean("springSecurityFilterChain", Filter.class))
                .build();
    }

    private static MockHttpServletRequestBuilder as(MockHttpServletRequestBuilder request, String token) {
        return request.header("Authorization", "Bearer " + token);
    }

    @Test
    void signupVerifyAndLoginNeedNoToken() throws Exception {
        mvc.perform(post("/api/auth/signup")).andExpect(status().isOk());
        mvc.perform(post("/api/auth/login")).andExpect(status().isOk());
        mvc.perform(get("/api/auth/verify-email")).andExpect(status().isOk());
    }

    @Test
    void otherApisRejectMissingOrInvalidToken() throws Exception {
        mvc.perform(get("/api/seats")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/reservations")).andExpect(status().isUnauthorized());
        mvc.perform(as(get("/api/seats"), "expired-or-forged")).andExpect(status().isUnauthorized());
    }

    @Test
    void studentIsDeniedAdminEndpoints() throws Exception {
        mvc.perform(as(get("/api/admin/dashboard"), STUDENT)).andExpect(status().isForbidden());
    }

    @Test
    void adminCanUseAdminEndpoints() throws Exception {
        mvc.perform(as(get("/api/admin/dashboard"), ADMIN)).andExpect(status().isOk());
    }

    @Test
    void studentCanUseStudentEndpoints() throws Exception {
        mvc.perform(as(get("/api/seats"), STUDENT)).andExpect(status().isOk());
        mvc.perform(as(post("/api/reservations"), STUDENT)).andExpect(status().isOk());
        mvc.perform(as(get("/api/reservations/my"), STUDENT)).andExpect(status().isOk());
        mvc.perform(as(patch("/api/reservations/1/cancel"), STUDENT)).andExpect(status().isOk());
    }

    @Test
    void adminCanViewSeatsButCannotActAsStudent() throws Exception {
        mvc.perform(as(get("/api/seats"), ADMIN)).andExpect(status().isOk());
        mvc.perform(as(post("/api/reservations"), ADMIN)).andExpect(status().isForbidden());
        mvc.perform(as(delete("/api/reservations/1"), ADMIN)).andExpect(status().isForbidden());
    }
}
