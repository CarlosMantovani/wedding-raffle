package com.weddingraffle.rifa.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import com.weddingraffle.rifa.config.AppProperties;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

class JwtServiceTests {

    private static final String SECRET = "01234567890123456789012345678901";

    @Test
    void generatesTokenWithAdminClaims() {
        JwtService jwtService = new JwtService(jwtEncoder(), appProperties());
        TestingAuthenticationToken authentication = new TestingAuthenticationToken("admin", "password", "ROLE_MASTER");

        String token = jwtService.generateToken(authentication);
        Jwt jwt = jwtDecoder().decode(token);

        assertThat(jwt.getSubject()).isEqualTo("admin");
        assertThat(jwt.getClaimAsString("iss")).isEqualTo("raffle-api-test");
        assertThat(jwt.getClaimAsStringList("roles")).containsExactly("MASTER");
        assertThat(jwt.getIssuedAt()).isNotNull();
        assertThat(jwt.getExpiresAt()).isNotNull();
        assertThat(jwtService.expiresInSeconds()).isEqualTo(3600);
    }

    private NimbusJwtEncoder jwtEncoder() {
        return new NimbusJwtEncoder(new ImmutableSecret<>(secretKey()));
    }

    private NimbusJwtDecoder jwtDecoder() {
        return NimbusJwtDecoder.withSecretKey(secretKey())
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
    }

    private SecretKeySpec secretKey() {
        return new SecretKeySpec(SECRET.getBytes(), "HmacSHA256");
    }

    private AppProperties appProperties() {
        return new AppProperties(
                "http://localhost:5173",
                new AppProperties.Jwt(SECRET, 3600, "raffle-api-test"),
                new AppProperties.Raffle(null, "00000", "99999"),
                new AppProperties.MercadoPago(
                        "token",
                        "http://localhost:8080/payments/webhook",
                        "",
                        "http://localhost:5173/payment-return/success",
                        "http://localhost:5173/payment-return/failure",
                        "http://localhost:5173/payment-return/pending",
                        new AppProperties.Retry(3, 500, 2)));
    }
}
