package dev.mosiur.payledger.payments.idempotency;

import static org.assertj.core.api.Assertions.assertThat;

import dev.mosiur.payledger.payments.PostgresTestConfiguration;
import dev.mosiur.payledger.payments.account.model.AccountResponse;
import dev.mosiur.payledger.payments.idempotency.web.IdempotencyFilter;
import dev.mosiur.payledger.payments.support.TestJwtConfiguration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.client.EntityExchangeResult;
import org.springframework.test.web.servlet.client.RestTestClient;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import({PostgresTestConfiguration.class, TestJwtConfiguration.class})
class IdempotencyApiIT {

    @LocalServerPort
    private int port;

    @Autowired
    private JdbcClient jdbc;

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
    void aRetriedTransferRunsOnceAndReturnsTheSameResponse() {
        String key = UUID.randomUUID().toString();

        EntityExchangeResult<String> first = transfer(key, 30_000).expectStatus().isCreated()
                .expectBody(String.class).returnResult();
        EntityExchangeResult<String> retry = transfer(key, 30_000).expectStatus().isCreated()
                .expectHeader().valueEquals(IdempotencyFilter.REPLAYED_HEADER, "true")
                .expectBody(String.class).returnResult();

        assertThat(retry.getResponseBody()).isEqualTo(first.getResponseBody());
        assertThat(first.getResponseHeaders().getFirst(IdempotencyFilter.REPLAYED_HEADER)).isNull();
        assertThat(transfersFrom(from)).isEqualTo(1);
        assertThat(balance(from)).isEqualTo(70_000);
    }

    @Test
    void reusingAKeyForADifferentTransferIsRefused() {
        String key = UUID.randomUUID().toString();
        transfer(key, 30_000).expectStatus().isCreated();

        transfer(key, 31_000).expectStatus().isEqualTo(422)
                .expectHeader().contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .expectBody().jsonPath("$.title").isEqualTo("Idempotency key reused");

        assertThat(transfersFrom(from)).isEqualTo(1);
    }

    @Test
    void aTransferWithoutAKeyIsRefused() {
        client.post().uri("/api/v1/transfers")
                .header(HttpHeaders.AUTHORIZATION, TestJwtConfiguration.user(ayesha))
                .contentType(MediaType.APPLICATION_JSON)
                .body(transferBody(100))
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody().jsonPath("$.title").isEqualTo("Idempotency key required");

        assertThat(transfersFrom(from)).isZero();
    }

    @Test
    void aBusinessRejectionIsAlsoReplayed() {
        String key = UUID.randomUUID().toString();
        transfer(key, 200_000).expectStatus().isEqualTo(422);

        transfer(key, 200_000).expectStatus().isEqualTo(422)
                .expectHeader().valueEquals(IdempotencyFilter.REPLAYED_HEADER, "true");
    }

    @Test
    void keysBelongToTheCallerWhoSentThem() {
        String sharedKey = UUID.randomUUID().toString();
        String rahim = "rahim-" + UUID.randomUUID();
        UUID rahimsAccount = openAccount(rahim);
        deposit(rahimsAccount, 5_000);

        transfer(sharedKey, 1_000).expectStatus().isCreated();
        client.post().uri("/api/v1/transfers")
                .header(HttpHeaders.AUTHORIZATION, TestJwtConfiguration.user(rahim))
                .header(IdempotencyFilter.KEY_HEADER, sharedKey)
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"fromAccountId\":\"" + rahimsAccount + "\",\"toAccountId\":\"" + from + "\",\"amountMinor\":1}")
                .exchange()
                .expectStatus().isCreated()
                .expectHeader().doesNotExist(IdempotencyFilter.REPLAYED_HEADER);

        assertThat(transfersFrom(rahimsAccount)).isEqualTo(1);
    }

    @Test
    void parallelDuplicatesProduceExactlyOneTransfer() throws Exception {
        String key = UUID.randomUUID().toString();
        List<Callable<Integer>> attempts = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            attempts.add(() -> transfer(key, 10_000).returnResult(String.class).getStatus().value());
        }

        List<Integer> statuses = new ArrayList<>();
        try (ExecutorService pool = Executors.newFixedThreadPool(10)) {
            for (Future<Integer> status : pool.invokeAll(attempts)) {
                statuses.add(status.get());
            }
        }

        assertThat(statuses).containsOnly(201, 409);
        assertThat(transfersFrom(from)).isEqualTo(1);
        assertThat(balance(from)).isEqualTo(90_000);
    }

    private RestTestClient.ResponseSpec transfer(String key, long amount) {
        return client.post().uri("/api/v1/transfers")
                .header(HttpHeaders.AUTHORIZATION, TestJwtConfiguration.user(ayesha))
                .header(IdempotencyFilter.KEY_HEADER, key)
                .contentType(MediaType.APPLICATION_JSON)
                .body(transferBody(amount))
                .exchange();
    }

    private String transferBody(long amount) {
        return "{\"fromAccountId\":\"" + from + "\",\"toAccountId\":\"" + to + "\",\"amountMinor\":" + amount + "}";
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
