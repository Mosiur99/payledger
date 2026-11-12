package dev.mosiur.payledger.payments.common.observability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.mosiur.payledger.payments.PostgresTestConfiguration;
import dev.mosiur.payledger.payments.account.domain.Currency;
import dev.mosiur.payledger.payments.account.domain.InsufficientFundsException;
import dev.mosiur.payledger.payments.account.model.OpenAccountRequest;
import dev.mosiur.payledger.payments.account.service.AccountService;
import dev.mosiur.payledger.payments.ledger.model.DepositRequest;
import dev.mosiur.payledger.payments.ledger.service.DepositService;
import dev.mosiur.payledger.payments.outbox.repository.OutboxEventRepository;
import dev.mosiur.payledger.payments.support.TestJwtConfiguration;
import dev.mosiur.payledger.payments.transfer.model.TransferRequest;
import dev.mosiur.payledger.payments.transfer.service.TransferService;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.micrometer.metrics.test.autoconfigure.AutoConfigureMetrics;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.client.RestTestClient;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMetrics
@Import({PostgresTestConfiguration.class, TestJwtConfiguration.class})
class ObservabilityIT {

    @LocalServerPort
    private int port;

    @Autowired
    private MeterRegistry registry;

    @Autowired
    private AccountService accountService;

    @Autowired
    private DepositService depositService;

    @Autowired
    private TransferService transferService;

    @Autowired
    private OutboxEventRepository outboxRepository;

    @Autowired
    private TransactionTemplate transactionTemplate;

    private RestTestClient client;
    private String ayesha;
    private UUID from;
    private UUID to;

    @BeforeEach
    void setUp() {
        client = RestTestClient.bindToServer().baseUrl("http://localhost:" + port).build();
        ayesha = "ayesha-" + UUID.randomUUID();
        from = accountService.open(ayesha, new OpenAccountRequest(Currency.EUR)).id();
        to = accountService.open("rahim-" + UUID.randomUUID(), new OpenAccountRequest(Currency.EUR)).id();
        depositService.deposit("admin", new DepositRequest(from, 100_000, null));
    }

    @Test
    void aCommittedTransferMovesTheCounters() {
        double transfers = transfers();
        double volume = transferVolume();

        transferService.transfer(ayesha, new TransferRequest(from, to, 40_000, null));

        assertThat(transfers()).isEqualTo(transfers + 1);
        assertThat(transferVolume()).isEqualTo(volume + 40_000);
    }

    @Test
    void rejectedAndRolledBackTransfersAreNotCounted() {
        double transfers = transfers();

        assertThatThrownBy(() -> transferService.transfer(ayesha, new TransferRequest(from, to, 999_999, null)))
                .isInstanceOf(InsufficientFundsException.class);
        transactionTemplate.executeWithoutResult(status -> {
            transferService.transfer(ayesha, new TransferRequest(from, to, 1_000, null));
            status.setRollbackOnly();
        });

        assertThat(transfers()).isEqualTo(transfers);
    }

    @Test
    void outboxGaugesReportWhatIsWaiting() {
        // The relay is off in tests, so every recorded event is still pending.
        assertThat(registry.get("payledger.outbox.pending").gauge().value())
                .isEqualTo(outboxRepository.countByPublishedAtIsNull())
                .isPositive();
        assertThat(registry.get("payledger.outbox.oldest.pending.age").gauge().value()).isPositive();
    }

    @Test
    void prometheusCanScrapeWithoutAToken() {
        transferService.transfer(ayesha, new TransferRequest(from, to, 1_000, null));
        client.get().uri("/api/v1/accounts")
                .header(HttpHeaders.AUTHORIZATION, TestJwtConfiguration.user(ayesha))
                .exchange()
                .expectStatus().isOk();

        String scrape = client.get().uri("/actuator/prometheus")
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .returnResult().getResponseBody();

        assertThat(scrape)
                .contains("payledger_ledger_transactions_total{currency=\"EUR\",type=\"TRANSFER\"")
                .contains("payledger_ledger_volume_minor_total")
                .contains("payledger_outbox_pending")
                .contains("payledger_outbox_oldest_pending_age_seconds")
                .contains("http_server_requests_seconds_bucket");
    }

    private double transfers() {
        var counter = registry.find("payledger.ledger.transactions").tag("type", "TRANSFER").tag("currency", "EUR").counter();
        return counter == null ? 0 : counter.count();
    }

    private double transferVolume() {
        var counter = registry.find("payledger.ledger.volume").tag("type", "TRANSFER").tag("currency", "EUR").counter();
        return counter == null ? 0 : counter.count();
    }
}
