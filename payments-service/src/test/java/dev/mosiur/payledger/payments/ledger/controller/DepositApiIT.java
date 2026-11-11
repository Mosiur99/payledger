package dev.mosiur.payledger.payments.ledger.controller;

import static org.assertj.core.api.Assertions.assertThat;

import dev.mosiur.payledger.payments.PostgresTestConfiguration;
import dev.mosiur.payledger.payments.account.model.AccountResponse;
import dev.mosiur.payledger.payments.idempotency.web.IdempotencyFilter;
import dev.mosiur.payledger.payments.ledger.model.StatementResponse;
import dev.mosiur.payledger.payments.support.TestJwtConfiguration;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.client.RestTestClient;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import({PostgresTestConfiguration.class, TestJwtConfiguration.class})
class DepositApiIT {

    @LocalServerPort
    private int port;

    private RestTestClient client;

    @BeforeEach
    void setUp() {
        client = RestTestClient.bindToServer().baseUrl("http://localhost:" + port).build();
    }

    @Test
    void depositIncreasesTheBalanceAndShowsOnTheStatement() {
        String owner = uniqueOwner();
        UUID accountId = openAccount(owner, "BDT");

        deposit(accountId, 50_000, "Salary top-up")
                .expectStatus().isCreated()
                .expectBody()
                .jsonPath("$.type").isEqualTo("DEPOSIT")
                .jsonPath("$.status").isEqualTo("COMPLETED")
                .jsonPath("$.currency").isEqualTo("BDT")
                .jsonPath("$.amountMinor").isEqualTo(50_000);

        statement(owner, accountId, "")
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.balanceMinor").isEqualTo(50_000)
                .jsonPath("$.entries.length()").isEqualTo(1)
                .jsonPath("$.entries[0].direction").isEqualTo("CREDIT")
                .jsonPath("$.entries[0].type").isEqualTo("DEPOSIT")
                .jsonPath("$.entries[0].reference").isEqualTo("Salary top-up")
                .jsonPath("$.nextBefore").doesNotExist();
    }

    @Test
    void pagesThroughTheStatementNewestFirst() {
        String owner = uniqueOwner();
        UUID accountId = openAccount(owner, "EUR");
        for (int amount = 100; amount <= 500; amount += 100) {
            deposit(accountId, amount, null).expectStatus().isCreated();
        }

        StatementResponse first = statement(owner, accountId, "?limit=2")
                .expectBody(StatementResponse.class).returnResult().getResponseBody();
        assertThat(first).isNotNull();
        assertThat(first.entries()).extracting(e -> e.amountMinor()).containsExactly(500L, 400L);
        assertThat(first.balanceMinor()).isEqualTo(1_500);

        StatementResponse second = statement(owner, accountId, "?limit=2&before=" + first.nextBefore())
                .expectBody(StatementResponse.class).returnResult().getResponseBody();
        assertThat(second).isNotNull();
        assertThat(second.entries()).extracting(e -> e.amountMinor()).containsExactly(300L, 200L);

        StatementResponse last = statement(owner, accountId, "?limit=2&before=" + second.nextBefore())
                .expectBody(StatementResponse.class).returnResult().getResponseBody();
        assertThat(last).isNotNull();
        assertThat(last.entries()).extracting(e -> e.amountMinor()).containsExactly(100L);
        assertThat(last.nextBefore()).isNull();
    }

    @Test
    void rejectsADepositToAnUnknownAccount() {
        deposit(UUID.randomUUID(), 100, null).expectStatus().isNotFound();
    }

    @Test
    void onlyAnAdminCanDeposit() {
        String owner = uniqueOwner();
        UUID accountId = openAccount(owner, "BDT");

        client.post().uri("/api/v1/deposits")
                .header(HttpHeaders.AUTHORIZATION, TestJwtConfiguration.user(owner))
                .header(IdempotencyFilter.KEY_HEADER, UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"accountId\":\"" + accountId + "\",\"amountMinor\":100}")
                .exchange()
                .expectStatus().isForbidden()
                .expectBody()
                .jsonPath("$.type").isEqualTo("/problems/forbidden");

        statement(owner, accountId, "").expectStatus().isOk()
                .expectBody().jsonPath("$.balanceMinor").isEqualTo(0);
    }

    @Test
    void rejectsInvalidAmounts() {
        UUID accountId = openAccount(uniqueOwner(), "BDT");

        deposit(accountId, 0, null).expectStatus().isBadRequest();
        deposit(accountId, -5, null).expectStatus().isBadRequest();
        deposit(accountId, 1_000_000_001L, null).expectStatus().isBadRequest();
    }

    @Test
    void hidesAnotherOwnersStatement() {
        UUID accountId = openAccount(uniqueOwner(), "BDT");

        statement(uniqueOwner(), accountId, "").expectStatus().isNotFound();
    }

    @Test
    void rejectsAnOutOfRangePageSize() {
        String owner = uniqueOwner();
        UUID accountId = openAccount(owner, "BDT");

        statement(owner, accountId, "?limit=0").expectStatus().isBadRequest();
        statement(owner, accountId, "?limit=101").expectStatus().isBadRequest();
    }

    private UUID openAccount(String owner, String currency) {
        AccountResponse account = client.post().uri("/api/v1/accounts")
                .header(HttpHeaders.AUTHORIZATION, TestJwtConfiguration.user(owner))
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"currency\":\"" + currency + "\"}")
                .exchange()
                .expectStatus().isCreated()
                .expectBody(AccountResponse.class)
                .returnResult().getResponseBody();
        assertThat(account).isNotNull();
        return account.id();
    }

    private RestTestClient.ResponseSpec deposit(UUID accountId, long amount, String reference) {
        String referenceJson = reference == null ? "null" : "\"" + reference + "\"";
        return client.post().uri("/api/v1/deposits")
                .header(HttpHeaders.AUTHORIZATION, TestJwtConfiguration.admin("admin"))
                .header(IdempotencyFilter.KEY_HEADER, UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"accountId\":\"" + accountId + "\",\"amountMinor\":" + amount
                        + ",\"reference\":" + referenceJson + "}")
                .exchange();
    }

    private RestTestClient.ResponseSpec statement(String owner, UUID accountId, String query) {
        return client.get().uri("/api/v1/accounts/" + accountId + "/statement" + query)
                .header(HttpHeaders.AUTHORIZATION, TestJwtConfiguration.user(owner))
                .exchange();
    }

    private static String uniqueOwner() {
        return "user-" + UUID.randomUUID();
    }
}
