package dev.mosiur.payledger.payments.account.controller;

import static org.assertj.core.api.Assertions.assertThat;

import dev.mosiur.payledger.payments.PostgresTestConfiguration;
import dev.mosiur.payledger.payments.account.model.AccountResponse;
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
class AccountApiIT {

    private static final String ACCOUNTS = "/api/v1/accounts";

    @LocalServerPort
    private int port;

    private RestTestClient client;

    @BeforeEach
    void setUp() {
        client = RestTestClient.bindToServer().baseUrl("http://localhost:" + port).build();
    }

    @Test
    void opensAnAccountAndReadsItBack() {
        String owner = uniqueOwner();

        AccountResponse opened = open(owner, "{\"currency\":\"BDT\"}")
                .expectStatus().isCreated()
                .expectHeader().valueMatches("Location", ACCOUNTS + "/[0-9a-f-]{36}")
                .expectBody(AccountResponse.class)
                .returnResult().getResponseBody();

        assertThat(opened).isNotNull();
        client.get().uri(ACCOUNTS + "/" + opened.id())
                .header(AccountController.OWNER_HEADER, owner)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.currency").isEqualTo("BDT")
                .jsonPath("$.status").isEqualTo("ACTIVE")
                .jsonPath("$.balanceMinor").isEqualTo(0);
    }

    @Test
    void listsOnlyTheCallersAccounts() {
        String ayesha = uniqueOwner();
        String rahim = uniqueOwner();
        open(ayesha, "{\"currency\":\"BDT\"}").expectStatus().isCreated();
        open(ayesha, "{\"currency\":\"EUR\"}").expectStatus().isCreated();
        open(rahim, "{\"currency\":\"USD\"}").expectStatus().isCreated();

        client.get().uri(ACCOUNTS)
                .header(AccountController.OWNER_HEADER, ayesha)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.length()").isEqualTo(2)
                .jsonPath("$[0].currency").isEqualTo("BDT")
                .jsonPath("$[1].currency").isEqualTo("EUR");
    }

    @Test
    void hidesAnotherOwnersAccount() {
        AccountResponse ayeshas = open(uniqueOwner(), "{\"currency\":\"BDT\"}")
                .expectBody(AccountResponse.class)
                .returnResult().getResponseBody();

        assertThat(ayeshas).isNotNull();
        client.get().uri(ACCOUNTS + "/" + ayeshas.id())
                .header(AccountController.OWNER_HEADER, uniqueOwner())
                .exchange()
                .expectStatus().isNotFound();
    }

    @Test
    void rejectsAMissingOrUnsupportedCurrency() {
        open(uniqueOwner(), "{}").expectStatus().isBadRequest();
        open(uniqueOwner(), "{\"currency\":\"GBP\"}").expectStatus().isBadRequest();
    }

    @Test
    void rejectsARequestWithoutAnOwner() {
        client.post().uri(ACCOUNTS)
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"currency\":\"BDT\"}")
                .exchange()
                .expectStatus().isBadRequest();

        client.get().uri(ACCOUNTS)
                .header(AccountController.OWNER_HEADER, " ")
                .exchange()
                .expectStatus().isBadRequest();
    }

    private RestTestClient.ResponseSpec open(String owner, String json) {
        return client.post().uri(ACCOUNTS)
                .header(AccountController.OWNER_HEADER, owner)
                .contentType(MediaType.APPLICATION_JSON)
                .body(json)
                .exchange();
    }

    private static String uniqueOwner() {
        return "user-" + UUID.randomUUID();
    }
}
