package dev.mosiur.payledger.payments.common.security;

import static org.assertj.core.api.Assertions.assertThat;

import dev.mosiur.payledger.payments.PostgresTestConfiguration;
import dev.mosiur.payledger.payments.account.model.AccountResponse;
import dev.mosiur.payledger.payments.idempotency.web.IdempotencyFilter;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.client.RestTestClient;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.MountableFile;

/** Runs the real Keycloak realm from deploy/keycloak, so tokens, roles and audience are checked end to end. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(PostgresTestConfiguration.class)
class KeycloakSecurityIT {

    private static final GenericContainer<?> KEYCLOAK = new GenericContainer<>("quay.io/keycloak/keycloak:26.4")
            .withCommand("start-dev", "--import-realm")
            .withCopyFileToContainer(
                    MountableFile.forHostPath(Path.of("../deploy/keycloak/payledger-realm.json")),
                    "/opt/keycloak/data/import/payledger-realm.json")
            .withExposedPorts(8080)
            .waitingFor(Wait.forHttp("/realms/payledger/.well-known/openid-configuration")
                    .forPort(8080)
                    .withStartupTimeout(Duration.ofMinutes(3)));

    static {
        KEYCLOAK.start();
    }

    @DynamicPropertySource
    static void issuer(DynamicPropertyRegistry registry) {
        registry.add("spring.security.oauth2.resourceserver.jwt.issuer-uri", KeycloakSecurityIT::issuerUri);
    }

    @LocalServerPort
    private int port;

    private RestTestClient client;

    @BeforeEach
    void setUp() {
        client = RestTestClient.bindToServer().baseUrl("http://localhost:" + port).build();
    }

    @Test
    void healthIsPublicButTheApiIsNot() {
        client.get().uri("/actuator/health").exchange().expectStatus().isOk();

        client.get().uri("/api/v1/accounts")
                .exchange()
                .expectStatus().isUnauthorized()
                .expectHeader().valueMatches(HttpHeaders.WWW_AUTHENTICATE, "Bearer.*")
                .expectBody()
                .jsonPath("$.type").isEqualTo("/problems/unauthenticated");
    }

    @Test
    void customerUsesTheirOwnAccountsAndCannotSeeOthers() {
        String ayesha = login("ayesha", "ayesha-demo");
        String rahim = login("rahim", "rahim-demo");

        AccountResponse account = openAccount(ayesha);

        client.get().uri("/api/v1/accounts/" + account.id())
                .header(HttpHeaders.AUTHORIZATION, ayesha)
                .exchange()
                .expectStatus().isOk();
        client.get().uri("/api/v1/accounts/" + account.id())
                .header(HttpHeaders.AUTHORIZATION, rahim)
                .exchange()
                .expectStatus().isNotFound()
                .expectBody()
                .jsonPath("$.type").isEqualTo("/problems/account-not-found");
    }

    @Test
    void depositsNeedTheAdminRole() {
        String ayesha = login("ayesha", "ayesha-demo");
        String admin = login("ops-admin", "ops-admin-demo");
        AccountResponse account = openAccount(ayesha);

        deposit(ayesha, account.id())
                .expectStatus().isForbidden()
                .expectBody()
                .jsonPath("$.type").isEqualTo("/problems/forbidden");
        deposit(admin, account.id()).expectStatus().isCreated();

        client.get().uri("/api/v1/accounts/" + account.id())
                .header(HttpHeaders.AUTHORIZATION, ayesha)
                .exchange()
                .expectBody()
                .jsonPath("$.balanceMinor").isEqualTo(25_000);
    }

    @Test
    void adminCannotActAsACustomer() {
        String admin = login("ops-admin", "ops-admin-demo");

        client.get().uri("/api/v1/accounts")
                .header(HttpHeaders.AUTHORIZATION, admin)
                .exchange()
                .expectStatus().isForbidden();
    }

    private AccountResponse openAccount(String bearer) {
        AccountResponse account = client.post().uri("/api/v1/accounts")
                .header(HttpHeaders.AUTHORIZATION, bearer)
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"currency\":\"BDT\"}")
                .exchange()
                .expectStatus().isCreated()
                .expectBody(AccountResponse.class)
                .returnResult().getResponseBody();
        assertThat(account).isNotNull();
        return account;
    }

    private RestTestClient.ResponseSpec deposit(String bearer, UUID accountId) {
        return client.post().uri("/api/v1/deposits")
                .header(HttpHeaders.AUTHORIZATION, bearer)
                .header(IdempotencyFilter.KEY_HEADER, UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"accountId\":\"" + accountId + "\",\"amountMinor\":25000}")
                .exchange();
    }

    private static String login(String username, String password) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "password");
        form.add("client_id", "payledger-cli");
        form.add("username", username);
        form.add("password", password);
        Map<String, Object> token = RestClient.create().post()
                .uri(issuerUri() + "/protocol/openid-connect/token")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .body(new ParameterizedTypeReference<>() {
                });
        assertThat(token).containsKey("access_token");
        return "Bearer " + token.get("access_token");
    }

    private static String issuerUri() {
        return "http://" + KEYCLOAK.getHost() + ":" + KEYCLOAK.getMappedPort(8080) + "/realms/payledger";
    }
}
