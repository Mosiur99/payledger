package dev.mosiur.payledger.payments.common.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

class KeycloakRealmRoleConverterTest {

    private final KeycloakRealmRoleConverter converter = new KeycloakRealmRoleConverter();

    @Test
    void mapsRealmRolesToSpringRoles() {
        Jwt jwt = token(Map.of("realm_access", Map.of("roles", List.of("USER", "ADMIN"))));

        assertThat(converter.convert(jwt)).extracting(GrantedAuthority::getAuthority)
                .containsExactly("ROLE_USER", "ROLE_ADMIN");
    }

    @Test
    void grantsNothingWhenTheClaimIsMissingOrMalformed() {
        assertThat(converter.convert(token(Map.of("scope", "profile")))).isEmpty();
        assertThat(converter.convert(token(Map.of("realm_access", Map.of("roles", "USER"))))).isEmpty();
        assertThat(converter.convert(token(Map.of("realm_access", Map.of("roles", List.of(42, "USER"))))))
                .extracting(GrantedAuthority::getAuthority)
                .containsExactly("ROLE_USER");
    }

    private static Jwt token(Map<String, Object> claims) {
        return Jwt.withTokenValue("token").header("alg", "RS256").subject("ayesha").claims(c -> c.putAll(claims)).build();
    }
}
