package dev.mosiur.payledger.payments.outbox;

import static org.assertj.core.api.Assertions.assertThat;

import dev.mosiur.payledger.payments.PostgresTestConfiguration;
import dev.mosiur.payledger.payments.account.domain.Currency;
import dev.mosiur.payledger.payments.account.model.OpenAccountRequest;
import dev.mosiur.payledger.payments.account.service.AccountService;
import dev.mosiur.payledger.payments.ledger.model.DepositRequest;
import dev.mosiur.payledger.payments.ledger.model.TransactionPostedEvent;
import dev.mosiur.payledger.payments.ledger.service.DepositService;
import dev.mosiur.payledger.payments.outbox.relay.KafkaEventPublisher;
import dev.mosiur.payledger.payments.outbox.repository.OutboxEventRepository;
import dev.mosiur.payledger.payments.support.KafkaTestConfiguration;
import dev.mosiur.payledger.payments.transfer.model.TransferRequest;
import dev.mosiur.payledger.payments.transfer.model.TransferResponse;
import dev.mosiur.payledger.payments.transfer.service.TransferService;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.micrometer.tracing.test.autoconfigure.AutoConfigureTracing;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.kafka.KafkaContainer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest
@AutoConfigureTracing
@Import({PostgresTestConfiguration.class, KafkaTestConfiguration.class})
@TestPropertySource(properties = {
        "payledger.outbox.relay.enabled=true",
        "payledger.outbox.relay.interval=100ms"})
class OutboxKafkaIT {

    private static final Duration QUIET_PERIOD = Duration.ofSeconds(2);

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

    @Autowired
    private KafkaContainer kafka;

    @Autowired
    private JsonMapper jsonMapper;

    @Autowired
    private Tracer tracer;

    private KafkaConsumer<String, String> consumer;
    private final List<ConsumerRecord<String, String>> received = new ArrayList<>();

    @BeforeEach
    void subscribe() {
        consumer = new KafkaConsumer<>(Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers(),
                ConsumerConfig.GROUP_ID_CONFIG, "test-" + UUID.randomUUID(),
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest"),
                new StringDeserializer(), new StringDeserializer());
        consumer.subscribe(List.of(TransactionPostedEvent.TOPIC));
    }

    @AfterEach
    void close() {
        consumer.close();
    }

    @Test
    void publishesEachCommittedTransactionExactlyOnce() {
        String ayesha = "ayesha-" + UUID.randomUUID();
        String rahim = "rahim-" + UUID.randomUUID();
        UUID from = accountService.open(ayesha, new OpenAccountRequest(Currency.BDT)).id();
        UUID to = accountService.open(rahim, new OpenAccountRequest(Currency.BDT)).id();
        UUID depositId = depositService.deposit("admin", new DepositRequest(from, 100_000, "Salary")).id();
        UUID transferId = transferService.transfer(ayesha, new TransferRequest(from, to, 40_000, "Rent")).id();

        List<ConsumerRecord<String, String>> deposits = recordsFor(depositId.toString());
        List<ConsumerRecord<String, String>> transfers = recordsFor(transferId.toString());

        assertThat(deposits).hasSize(1);
        assertThat(transfers).hasSize(1);
        ConsumerRecord<String, String> transfer = transfers.getFirst();
        assertThat(header(transfer, KafkaEventPublisher.EVENT_TYPE_HEADER)).isEqualTo(TransactionPostedEvent.EVENT_TYPE);
        assertThat(header(transfer, KafkaEventPublisher.EVENT_ID_HEADER)).isNotBlank();

        JsonNode payload = jsonMapper.readTree(transfer.value());
        assertThat(payload.get("type").asString()).isEqualTo("TRANSFER");
        assertThat(payload.get("debitAccountId").asString()).isEqualTo(from.toString());
        assertThat(payload.get("debitOwnerId").asString()).isEqualTo(ayesha);
        assertThat(payload.get("creditOwnerId").asString()).isEqualTo(rahim);
        assertThat(payload.get("amountMinor").asLong()).isEqualTo(40_000);
        assertThat(payload.get("currency").asString()).isEqualTo("BDT");
        assertThat(payload.get("reference").asString()).isEqualTo("Rent");

        assertThat(outboxRepository.findByMessageKey(transferId.toString()))
                .singleElement()
                .satisfies(event -> assertThat(event.isPublished()).isTrue());
    }

    @Test
    void theTraceOfTheRequestContinuesIntoKafka() {
        String ayesha = "ayesha-" + UUID.randomUUID();
        UUID from = accountService.open(ayesha, new OpenAccountRequest(Currency.BDT)).id();
        UUID to = accountService.open("rahim-" + UUID.randomUUID(), new OpenAccountRequest(Currency.BDT)).id();
        depositService.deposit("admin", new DepositRequest(from, 10_000, null));

        Span request = tracer.nextSpan().name("POST /api/v1/transfers").start();
        UUID transferId;
        try (Tracer.SpanInScope ignored = tracer.withSpan(request)) {
            transferId = transferService.transfer(ayesha, new TransferRequest(from, to, 1_000, null)).id();
        } finally {
            request.end();
        }

        ConsumerRecord<String, String> record = recordsFor(transferId.toString()).getFirst();
        String traceParent = header(record, "traceparent");
        assertThat(traceParent).isNotNull();
        assertThat(traceParent.split("-")[1]).isEqualTo(request.context().traceId());
        assertThat(outboxRepository.findByMessageKey(transferId.toString()).getFirst().getTraceParent())
                .contains(request.context().traceId());
    }

    @Test
    void publishesNothingWhenTheTransactionRollsBack() {
        String ayesha = "ayesha-" + UUID.randomUUID();
        UUID from = accountService.open(ayesha, new OpenAccountRequest(Currency.BDT)).id();
        UUID to = accountService.open("rahim-" + UUID.randomUUID(), new OpenAccountRequest(Currency.BDT)).id();
        depositService.deposit("admin", new DepositRequest(from, 100_000, null));

        TransferResponse rolledBack = transactionTemplate.execute(status -> {
            TransferResponse response = transferService.transfer(ayesha, new TransferRequest(from, to, 40_000, null));
            status.setRollbackOnly();
            return response;
        });

        UUID marker = depositService.deposit("admin", new DepositRequest(from, 1, "after rollback")).id();

        assertThat(rolledBack).isNotNull();
        assertThat(outboxRepository.findByMessageKey(rolledBack.id().toString())).isEmpty();
        assertThat(recordsFor(marker.toString())).hasSize(1);
        assertThat(matching(rolledBack.id().toString())).isEmpty();
    }

    /** Waits for the key, then keeps polling for a while so a late duplicate would still be caught. */
    private List<ConsumerRecord<String, String>> recordsFor(String key) {
        Instant deadline = Instant.now().plusSeconds(30);
        Instant quietUntil = Instant.MAX;
        while (Instant.now().isBefore(deadline) && Instant.now().isBefore(quietUntil)) {
            pollOnce();
            if (quietUntil.equals(Instant.MAX) && !matching(key).isEmpty()) {
                quietUntil = Instant.now().plus(QUIET_PERIOD);
            }
        }
        return matching(key);
    }

    private void pollOnce() {
        consumer.poll(Duration.ofMillis(200)).forEach(received::add);
    }

    private List<ConsumerRecord<String, String>> matching(String key) {
        return received.stream().filter(record -> key.equals(record.key())).toList();
    }

    private static String header(ConsumerRecord<String, String> record, String name) {
        return new String(record.headers().lastHeader(name).value(), StandardCharsets.UTF_8);
    }
}
