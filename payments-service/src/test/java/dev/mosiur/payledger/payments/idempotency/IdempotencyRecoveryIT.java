package dev.mosiur.payledger.payments.idempotency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

import dev.mosiur.payledger.payments.PostgresTestConfiguration;
import dev.mosiur.payledger.payments.account.model.AccountResponse;
import dev.mosiur.payledger.payments.idempotency.service.IdempotencyService;
import dev.mosiur.payledger.payments.idempotency.web.IdempotencyFilter;
import dev.mosiur.payledger.payments.support.TestJwtConfiguration;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.client.RestTestClient;

/** What happens to a key when the request that claimed it fails halfway or dies. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import({PostgresTestConfiguration.class, TestJwtConfiguration.class})
class IdempotencyRecoveryIT {

    @LocalServerPort
    private int port;

    @Autowired
    private JdbcClient jdbc;

    @MockitoSpyBean
    private IdempotencyService idempotencyService;

    private RestTestClient client;
    private String ayesha;
    private UUID from;
    private UUID to;

    @BeforeEach
    void setUp() {
        client = RestTestClient.bindToServer().baseUrl("http://localhost:" + port).build();
        ayesha = "ayesha-" + UUID.randomUUID();
        from = openAccount(ayesha);
        to = openAccount("rahim-" + UUID.randomUUID());
        deposit(from, 100_000);
    }

    @Test
    void ifTheResponseCannotBeStoredTheTransferRollsBackAndTheRetryRunsOnce() {
        String key = UUID.randomUUID().toString();
        doThrow(new IllegalStateException("connection lost")).doCallRealMethod()
                .when(idempotencyService).complete(any(), any(), any());

        transfer(key).expectStatus().isEqualTo(500);

        assertThat(balance(from)).isEqualTo(100_000);
        assertThat(transfersFrom(from)).isZero();

        transfer(key).expectStatus().isCreated();

        assertThat(balance(from)).isEqualTo(90_000);
        assertThat(transfersFrom(from)).isEqualTo(1);
    }

    @Test
    void aKeyLeftUnfinishedByADeadRequestIsTakenOverByTheRetry() {
        String key = UUID.randomUUID().toString();
        claim(key, Instant.now().minus(IdempotencyService.ABANDONED_AFTER).minusSeconds(5));

        transfer(key).expectStatus().isCreated();
        transfer(key).expectStatus().isCreated().expectHeader().valueEquals(IdempotencyFilter.REPLAYED_HEADER, "true");

        assertThat(transfersFrom(from)).isEqualTo(1);
    }

    @Test
    void aRecentlyClaimedKeyIsStillInProgress() {
        String key = UUID.randomUUID().toString();
        claim(key, Instant.now());

        transfer(key).expectStatus().isEqualTo(409);

        assertThat(transfersFrom(from)).isZero();
    }

    /** Leaves the row an earlier attempt of the same request would have left if it died before finishing. */
    private void claim(String key, Instant claimedAt) {
        jdbc.sql("""
                INSERT INTO idempotency_keys (owner_id, idempotency_key, request_hash, created_at)
                VALUES (?, ?, ?, ?)
                """).params(ayesha, key, hashOf(transferBody()), Timestamp.from(claimedAt)).update();
    }

    private static String hashOf(String body) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update("POST /api/v1/transfers\n".getBytes(StandardCharsets.UTF_8));
            digest.update(body.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest.digest());
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private RestTestClient.ResponseSpec transfer(String key) {
        return client.post().uri("/api/v1/transfers")
                .header(HttpHeaders.AUTHORIZATION, TestJwtConfiguration.user(ayesha))
                .header(IdempotencyFilter.KEY_HEADER, key)
                .contentType(MediaType.APPLICATION_JSON)
                .body(transferBody())
                .exchange();
    }

    private String transferBody() {
        return "{\"fromAccountId\":\"" + from + "\",\"toAccountId\":\"" + to + "\",\"amountMinor\":10000}";
    }

    private UUID openAccount(String owner) {
        AccountResponse account = client.post().uri("/api/v1/accounts")
                .header(HttpHeaders.AUTHORIZATION, TestJwtConfiguration.user(owner))
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"currency\":\"BDT\"}")
                .exchange()
                .expectStatus().isCreated()
                .expectBody(AccountResponse.class)
                .returnResult().getResponseBody();
        assertThat(account).isNotNull();
        return account.id();
    }

    private void deposit(UUID accountId, long amount) {
        client.post().uri("/api/v1/deposits")
                .header(HttpHeaders.AUTHORIZATION, TestJwtConfiguration.admin("admin"))
                .header(IdempotencyFilter.KEY_HEADER, UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"accountId\":\"" + accountId + "\",\"amountMinor\":" + amount + "}")
                .exchange()
                .expectStatus().isCreated();
    }

    private long transfersFrom(UUID accountId) {
        return jdbc.sql("""
                SELECT count(*) FROM ledger_entries e JOIN transactions t ON t.id = e.transaction_id
                WHERE e.account_id = ? AND e.direction = 'DEBIT' AND t.type = 'TRANSFER'
                """).param(accountId).query(Long.class).single();
    }

    private long balance(UUID accountId) {
        return jdbc.sql("SELECT balance_minor FROM accounts WHERE id = ?").param(accountId).query(Long.class).single();
    }
}
