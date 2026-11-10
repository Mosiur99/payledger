package dev.mosiur.payledger.payments.transfer.controller;

import static org.assertj.core.api.Assertions.assertThat;

import dev.mosiur.payledger.payments.PostgresTestConfiguration;
import dev.mosiur.payledger.payments.account.model.AccountResponse;
import dev.mosiur.payledger.payments.common.web.CallerHeader;
import dev.mosiur.payledger.payments.idempotency.web.IdempotencyFilter;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.client.RestTestClient;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(PostgresTestConfiguration.class)
class TransferApiIT {

    @LocalServerPort
    private int port;

    private RestTestClient client;

    private String ayesha;
    private String rahim;

    @BeforeEach
    void setUp() {
        client = RestTestClient.bindToServer().baseUrl("http://localhost:" + port).build();
        ayesha = "ayesha-" + UUID.randomUUID();
        rahim = "rahim-" + UUID.randomUUID();
    }

    @Test
    void movesMoneyAndRecordsItOnBothStatements() {
        UUID from = openAccount(ayesha, "BDT");
        UUID to = openAccount(rahim, "BDT");
        deposit(from, 100_000);

        transfer(ayesha, from, to, 50_000)
                .expectStatus().isCreated()
                .expectBody()
                .jsonPath("$.status").isEqualTo("COMPLETED")
                .jsonPath("$.currency").isEqualTo("BDT")
                .jsonPath("$.amountMinor").isEqualTo(50_000)
                .jsonPath("$.reference").isEqualTo("Rent");

        statement(ayesha, from)
                .jsonPath("$.balanceMinor").isEqualTo(50_000)
                .jsonPath("$.entries[0].type").isEqualTo("TRANSFER")
                .jsonPath("$.entries[0].direction").isEqualTo("DEBIT");
        statement(rahim, to)
                .jsonPath("$.balanceMinor").isEqualTo(50_000)
                .jsonPath("$.entries[0].type").isEqualTo("TRANSFER")
                .jsonPath("$.entries[0].direction").isEqualTo("CREDIT");
    }

    @Test
    void refusesToOverdrawAndChangesNothing() {
        UUID from = openAccount(ayesha, "BDT");
        UUID to = openAccount(rahim, "BDT");
        deposit(from, 10_000);

        transfer(ayesha, from, to, 10_001)
                .expectStatus().isEqualTo(422)
                .expectHeader().contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .expectBody()
                .jsonPath("$.type").isEqualTo("/problems/insufficient-funds")
                .jsonPath("$.availableMinor").isEqualTo(10_000)
                .jsonPath("$.requestedMinor").isEqualTo(10_001);

        statement(ayesha, from).jsonPath("$.balanceMinor").isEqualTo(10_000);
        statement(rahim, to)
                .jsonPath("$.balanceMinor").isEqualTo(0)
                .jsonPath("$.entries.length()").isEqualTo(0);
    }

    @Test
    void refusesDifferentCurrencies() {
        UUID from = openAccount(ayesha, "BDT");
        UUID to = openAccount(rahim, "EUR");
        deposit(from, 10_000);

        transfer(ayesha, from, to, 100).expectStatus().isEqualTo(422);
    }

    @Test
    void refusesToSpendFromSomeoneElsesAccount() {
        UUID from = openAccount(ayesha, "BDT");
        UUID to = openAccount(rahim, "BDT");
        deposit(from, 10_000);

        transfer(rahim, from, to, 100).expectStatus().isNotFound();
        statement(ayesha, from).jsonPath("$.balanceMinor").isEqualTo(10_000);
    }

    @Test
    void refusesAnUnknownDestinationOrTheSameAccount() {
        UUID from = openAccount(ayesha, "BDT");
        deposit(from, 10_000);

        transfer(ayesha, from, UUID.randomUUID(), 100).expectStatus().isNotFound();
        transfer(ayesha, from, from, 100).expectStatus().isEqualTo(422);
    }

    @Test
    void rejectsInvalidRequests() {
        UUID from = openAccount(ayesha, "BDT");
        UUID to = openAccount(rahim, "BDT");

        transfer(ayesha, from, to, 0).expectStatus().isBadRequest();
        client.post().uri("/api/v1/transfers")
                .header(CallerHeader.NAME, ayesha)
                .header(IdempotencyFilter.KEY_HEADER, UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"fromAccountId\":\"" + from + "\",\"amountMinor\":100}")
                .exchange()
                .expectStatus().isBadRequest();
    }

    private UUID openAccount(String owner, String currency) {
        AccountResponse account = client.post().uri("/api/v1/accounts")
                .header(CallerHeader.NAME, owner)
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"currency\":\"" + currency + "\"}")
                .exchange()
                .expectStatus().isCreated()
                .expectBody(AccountResponse.class)
                .returnResult().getResponseBody();
        assertThat(account).isNotNull();
        return account.id();
    }

    private void deposit(UUID accountId, long amount) {
        client.post().uri("/api/v1/deposits")
                .header(CallerHeader.NAME, "admin")
                .header(IdempotencyFilter.KEY_HEADER, UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"accountId\":\"" + accountId + "\",\"amountMinor\":" + amount + "}")
                .exchange()
                .expectStatus().isCreated();
    }

    private RestTestClient.ResponseSpec transfer(String caller, UUID from, UUID to, long amount) {
        return client.post().uri("/api/v1/transfers")
                .header(CallerHeader.NAME, caller)
                .header(IdempotencyFilter.KEY_HEADER, UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"fromAccountId\":\"" + from + "\",\"toAccountId\":\"" + to
                        + "\",\"amountMinor\":" + amount + ",\"reference\":\"Rent\"}")
                .exchange();
    }

    private RestTestClient.BodyContentSpec statement(String owner, UUID accountId) {
        return client.get().uri("/api/v1/accounts/" + accountId + "/statement")
                .header(CallerHeader.NAME, owner)
                .exchange()
                .expectStatus().isOk()
                .expectBody();
    }
}
