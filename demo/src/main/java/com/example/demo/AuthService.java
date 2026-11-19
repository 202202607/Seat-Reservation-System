package com.example.demo;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AuthService {
    private final MemberRepository members;
    private final JdbcTemplate jdbc;
    private final JwtService tokens;
    private final BCryptPasswordEncoder encoder;
    // 존재하지 않는 학번도 BCrypt 연산을 수행합니다.
    private final String dummyHash;

    public AuthService(MemberRepository members, JdbcTemplate jdbc, JwtService tokens, BCryptPasswordEncoder encoder) {
        this.members = members; this.jdbc = jdbc; this.tokens = tokens; this.encoder = encoder;
        this.dummyHash = encoder.encode(UUID.randomUUID().toString());
    }

    public record UserInfo(long id, String studentid, String name, String role) {}
    public record TokenResponse(String tokenType, String accessToken, String refreshToken,
                                long expiresIn, long refreshExpiresIn, UserInfo user) {}
    private record Session(String id, long memberId, String refreshHash, Instant expiresAt, int revoked) {}

    /**
     * US-02 AC1·AC2: 학번(숫자 9자리) 또는 관리자 ID와 비밀번호로 로그인한다.
     * 빈 값이면 400(필수 입력 안내), 형식이 틀리거나 불일치면 401(실패 안내)이다.
     */
    @Transactional
    public TokenResponse login(String loginId, String password) {
        if (loginId == null || loginId.isBlank() || password == null || password.isEmpty())
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "학번(관리자 ID)과 비밀번호를 모두 입력해 주세요.");
        String id = loginId.trim();
        if (!(id.matches(SignupService.STUDENT_ID_PATTERN) || id.matches(SignupService.ADMIN_ID_PATTERN))
                || password.length() > 200)
            throw unauthorized();
        Member member = members.findByStudentid(id).orElse(null);
        boolean matches = encoder.matches(password, member == null ? dummyHash : member.getPasswordHash());
        if (!matches || member == null) throw unauthorized();
        if (!member.isActive()) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "이메일 인증을 완료해야 로그인할 수 있습니다.");}
        Instant now = Instant.now();
        String sid = UUID.randomUUID().toString();
        TokenResponse result = issue(member, sid, now);
        jdbc.update("INSERT INTO AUTH_SESSIONS (SESSION_ID, MEMBER_ID, REFRESH_HASH, EXPIRES_AT, REVOKED) VALUES (?, ?, ?, ?, 0)",
                sid, member.getId(), hash(result.refreshToken()), Timestamp.from(now.plus(JwtService.REFRESH_TTL)));
        return result;
    }

    @Transactional
    public TokenResponse refresh(String refreshToken) {
        Jwt jwt = decodeRefresh(refreshToken);
        Session session = lockSession(jwt);
        if (session.revoked() != 0 || !session.expiresAt().isAfter(Instant.now())
                || !sameHash(session.refreshHash(), refreshToken)) throw unauthorized();
        Member member = members.findById(session.memberId()).orElseThrow(AuthService::unauthorized);
        // 비활성화된 계정은 새 토큰을 받지 못한다. 역할이 바뀌었으면 새 토큰에 바뀐 역할이 담긴다.
        if (!member.isActive()) throw unauthorized();
        Instant now = Instant.now();
        TokenResponse result = issue(member, session.id(), now);
        jdbc.update("UPDATE AUTH_SESSIONS SET REFRESH_HASH = ?, EXPIRES_AT = ? WHERE SESSION_ID = ?",
                hash(result.refreshToken()), Timestamp.from(now.plus(JwtService.REFRESH_TTL)), session.id());
        return result;
    }

    @Transactional
    public void logout(String refreshToken) {
        Jwt jwt = decodeRefresh(refreshToken);
        Session session = lockSession(jwt);
        // 이미 로그아웃한 동일 세션에 대한 요청은 성공으로 처리합니다.
        if (session.revoked() != 0) return;
        if (!sameHash(session.refreshHash(), refreshToken)) throw unauthorized();
        jdbc.update("UPDATE AUTH_SESSIONS SET REVOKED = 1 WHERE SESSION_ID = ?", session.id());
    }

    /**
     * Access 토큰마다 세션이 살아 있는지와, 토큰의 역할이 DB의 현재 역할과 같은지 확인한다.
     * 역할이 회수되거나 계정이 비활성화되면 기존 토큰은 바로 거부되고, 클라이언트는 refresh 로 새 토큰을 받는다.
     */
    public boolean isActive(Jwt jwt) {
        String role = jwt.getClaimAsString(JwtService.ROLE_CLAIM);
        if (role == null) return false;
        Integer count = jdbc.queryForObject("""
                SELECT COUNT(*) FROM AUTH_SESSIONS s JOIN APP_MEMBERS m ON m.id = s.MEMBER_ID
                WHERE s.SESSION_ID = ? AND s.MEMBER_ID = ? AND s.REVOKED = 0 AND s.EXPIRES_AT > ?
                  AND m.status = 'ACTIVE' AND m.role = ?
                """,
                Integer.class, jwt.getClaimAsString("sid"), Long.parseLong(jwt.getSubject()),
                Timestamp.from(Instant.now()), role);
        return count != null && count == 1;
    }

    private Jwt decodeRefresh(String token) {
        if (token == null || token.isBlank() || token.length() > 8192) throw unauthorized();
        try { return tokens.decode(token, "refresh"); }
        catch (JwtException | IllegalArgumentException e) { throw unauthorized(); }
    }

    private Session lockSession(Jwt jwt) {
        var found = jdbc.query("SELECT SESSION_ID, MEMBER_ID, REFRESH_HASH, EXPIRES_AT, REVOKED FROM AUTH_SESSIONS WHERE SESSION_ID = ? FOR UPDATE",
                (rs, row) -> new Session(rs.getString(1), rs.getLong(2), rs.getString(3), rs.getTimestamp(4).toInstant(), rs.getInt(5)),
                jwt.getClaimAsString("sid"));
        if (found.size() != 1 || found.get(0).memberId() != Long.parseLong(jwt.getSubject())) throw unauthorized();
        return found.get(0);
    }

    private TokenResponse issue(Member member, String sid, Instant now) {
        return new TokenResponse("Bearer",
                tokens.issue(member.getId(), member.getRole(), sid, "access", now, now.plus(JwtService.ACCESS_TTL)),
                tokens.issue(member.getId(), member.getRole(), sid, "refresh", now, now.plus(JwtService.REFRESH_TTL)),
                JwtService.ACCESS_TTL.toSeconds(), JwtService.REFRESH_TTL.toSeconds(),
                new UserInfo(member.getId(), member.getStudentid(), member.getName(), member.getRole()));
    }

    private static String hash(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
    private static boolean sameHash(String expected, String token) {
        return MessageDigest.isEqual(expected.getBytes(StandardCharsets.US_ASCII), hash(token).getBytes(StandardCharsets.US_ASCII));
    }
    private static ResponseStatusException unauthorized() {
        return new ResponseStatusException(HttpStatus.UNAUTHORIZED, "학번(관리자 ID)·비밀번호 또는 인증 토큰을 확인해 주세요.");
    }
}
