package com.example.demo;

import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;
import javax.crypto.spec.SecretKeySpec;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.stereotype.Service;

@Service
public class JwtService {
    public static final Duration ACCESS_TTL = Duration.ofHours(1);
    public static final Duration REFRESH_TTL = Duration.ofDays(7);
    private static final String ISSUER = "library-seat-app";
    private final JwtEncoder encoder;
    private final NimbusJwtDecoder decoder;

    public JwtService(@Value("${app.jwt.secret}") String secret) {
        byte[] bytes = Base64.getDecoder().decode(secret);
        if (bytes.length < 32) throw new IllegalArgumentException("JWT_SECRET는 Base64로 인코딩한 32바이트 이상 난수여야 합니다.");
        var key = new SecretKeySpec(bytes, "HmacSHA256");
        encoder = new NimbusJwtEncoder(new ImmutableSecret<>(key));
        decoder = NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                new JwtTimestampValidator(Duration.ZERO), new JwtIssuerValidator(ISSUER)));
    }

    public String issue(long memberId, String sessionId, String type, Instant now, Instant expiresAt) {
        var claims = JwtClaimsSet.builder().issuer(ISSUER).subject(Long.toString(memberId))
                .issuedAt(now).expiresAt(expiresAt).id(UUID.randomUUID().toString())
                .claim("sid", sessionId).claim("token_type", type).build();
        return encoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
    }

    public Jwt decode(String token, String type) {
        Jwt jwt = decoder.decode(token);
        try {
            if (!type.equals(jwt.getClaimAsString("token_type"))
                    || jwt.getExpiresAt() == null || jwt.getIssuedAt() == null
                    || jwt.getClaimAsString("sid") == null || Long.parseLong(jwt.getSubject()) <= 0) {
                throw new IllegalArgumentException();
            }
        } catch (RuntimeException e) {
            throw new BadJwtException("유효하지 않은 토큰입니다.");
        }
        return jwt;
    }
}
