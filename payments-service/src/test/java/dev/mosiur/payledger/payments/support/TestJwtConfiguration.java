package dev.mosiur.payledger.payments.support;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

/**
 * Signs Keycloak-shaped tokens with a key created for the test run, so API tests exercise the real
 * security filter chain without starting Keycloak. KeycloakSecurityIT covers the real Keycloak.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestJwtConfiguration {

    private static final RSAKey KEY = generateKey();
    private static final NimbusJwtEncoder ENCODER = new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(KEY)));

    @Bean
    JwtDecoder testJwtDecoder() throws JOSEException {
        return NimbusJwtDecoder.withPublicKey(KEY.toRSAPublicKey()).build();
    }

    public static String user(String subject) {
        return bearer(subject, "USER");
    }

    public static String admin(String subject) {
        return bearer(subject, "ADMIN");
    }

    public static String bearer(String subject, String... roles) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("http://test-issuer/realms/payledger")
                .subject(subject)
                .audience(List.of("payledger-api"))
                .issuedAt(now)
                .expiresAt(now.plusSeconds(300))
                .claim("realm_access", Map.of("roles", List.of(roles)))
                .build();
        JwsHeader header = JwsHeader.with(SignatureAlgorithm.RS256).keyId(KEY.getKeyID()).build();
        return "Bearer " + ENCODER.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }

    private static RSAKey generateKey() {
        try {
            return new RSAKeyGenerator(2048).keyID("test-key").generate();
        } catch (JOSEException e) {
            throw new IllegalStateException(e);
        }
    }
}
