package dev.mosiur.payledger.notification.notification.consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import dev.mosiur.payledger.notification.KafkaTestConfiguration;
import dev.mosiur.payledger.notification.PostgresTestConfiguration;
import dev.mosiur.payledger.notification.common.config.KafkaErrorHandlingConfig;
import dev.mosiur.payledger.notification.notification.domain.Notification;
import dev.mosiur.payledger.notification.notification.domain.NotificationKind;
import dev.mosiur.payledger.notification.notification.model.TransactionPostedEvent;
import dev.mosiur.payledger.notification.notification.repository.NotificationRepository;
import dev.mosiur.payledger.notification.notification.repository.ProcessedEventRepository;
import dev.mosiur.payledger.notification.notification.service.NotificationService;
import io.micrometer.core.instrument.MeterRegistry;
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
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.testcontainers.kafka.KafkaContainer;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest
@Import({PostgresTestConfiguration.class, KafkaTestConfiguration.class})
class TransactionEventsIT {

    private static final Duration WAIT = Duration.ofSeconds(30);

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    @Autowired
    private KafkaContainer kafka;

    @Autowired
    private JsonMapper jsonMapper;

    @Autowired
    private NotificationRepository notifications;

    @Autowired
    private ProcessedEventRepository processedEvents;

    @MockitoSpyBean
    private NotificationService notificationService;

    @Autowired
    private MeterRegistry registry;

    private KafkaConsumer<String, String> deadLetters;
    private final List<ConsumerRecord<String, String>> deadLettersSeen = new ArrayList<>();

    @BeforeEach
    void watchDeadLetters() {
        deadLetters = new KafkaConsumer<>(Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers(),
                ConsumerConfig.GROUP_ID_CONFIG, "dlt-test-" + UUID.randomUUID(),
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest"),
                new StringDeserializer(), new StringDeserializer());
        deadLetters.subscribe(List.of(KafkaErrorHandlingConfig.DEAD_LETTER_TOPIC));
    }

    @AfterEach
    void close() {
        deadLetters.close();
    }

    @Test
    void notifiesBothSidesOfATransfer() {
        TransactionPostedEvent transfer = transfer("ayesha", "rahim");

        send(UUID.randomUUID(), transfer);

        await().atMost(WAIT).untilAsserted(() -> assertThat(notificationsFor(transfer))
                .extracting(Notification::getRecipientId, Notification::getKind)
                .containsExactlyInAnyOrder(
                        tuple("ayesha", NotificationKind.MONEY_SENT),
                        tuple("rahim", NotificationKind.MONEY_RECEIVED)));
    }

    @Test
    void aRedeliveredEventIsProcessedOnce() {
        double duplicates = registry.get("payledger.events.duplicates").counter().count();
        UUID eventId = UUID.randomUUID();
        TransactionPostedEvent transfer = transfer("ayesha", "rahim");
        String key = transfer.transactionId().toString();

        send(eventId, transfer);
        send(eventId, transfer);
        TransactionPostedEvent marker = transfer("marker-a", "marker-b");
        send(key, UUID.randomUUID(), json(marker));

        // Same key means same partition, so once the marker is done both copies have been consumed.
        await().atMost(WAIT).until(() -> notificationsFor(marker).size() == 2);
        assertThat(notificationsFor(transfer)).hasSize(2);
        verify(notificationService, times(2)).handle(eq(eventId), any());
        assertNoDeadLetter(key);
        assertThat(registry.get("payledger.events.duplicates").counter().count()).isEqualTo(duplicates + 1);
    }

    @Test
    void aPoisonMessageGoesToTheDeadLetterTopicWithoutBlockingThePartition() {
        double deadLettered = deadLetteredCount();
        String key = "poison-" + UUID.randomUUID();
        send(key, UUID.randomUUID(), "{not json");
        TransactionPostedEvent next = transfer("ayesha", "rahim");
        send(key, UUID.randomUUID(), json(next));

        await().atMost(WAIT).until(() -> notificationsFor(next).size() == 2);
        ConsumerRecord<String, String> dead = awaitDeadLetter(key);
        assertThat(dead.value()).isEqualTo("{not json");
        assertThat(header(dead, "kafka_dlt-original-topic")).isEqualTo(TransactionPostedEvent.TOPIC);
        assertThat(header(dead, "kafka_dlt-exception-message")).contains("Payload is not a valid TransactionPosted event");
        assertThat(deadLetteredCount()).isGreaterThanOrEqualTo(deadLettered + 1);
    }

    @Test
    void aTemporaryFailureIsRetriedAndSucceeds() {
        UUID eventId = UUID.randomUUID();
        TransactionPostedEvent transfer = transfer("ayesha", "rahim");
        doThrow(new QueryTimeoutException("database busy"))
                .doCallRealMethod()
                .when(notificationService).handle(eq(eventId), any());

        send(eventId, transfer);

        await().atMost(WAIT).until(() -> notificationsFor(transfer).size() == 2);
        verify(notificationService, times(2)).handle(eq(eventId), any());
        assertThat(processedEvents.exists(eventId)).isTrue();
        assertNoDeadLetter(transfer.transactionId().toString());
    }

    @Test
    void aFailureThatNeverRecoversIsParkedAfterTheRetries() {
        UUID eventId = UUID.randomUUID();
        TransactionPostedEvent transfer = transfer("ayesha", "rahim");
        doThrow(new QueryTimeoutException("database down")).when(notificationService).handle(eq(eventId), any());

        send(eventId, transfer);

        ConsumerRecord<String, String> dead = awaitDeadLetter(transfer.transactionId().toString());
        verify(notificationService, times(1 + KafkaErrorHandlingConfig.MAX_RETRIES)).handle(eq(eventId), any());
        assertThat(header(dead, "kafka_dlt-exception-message")).contains("database down");
        assertThat(processedEvents.exists(eventId)).isFalse();
        doCallRealMethod().when(notificationService).handle(eq(eventId), any());
    }

    private void send(UUID eventId, TransactionPostedEvent event) {
        send(event.transactionId().toString(), eventId, json(event));
    }

    private void send(String key, UUID eventId, String value) {
        ProducerRecord<String, String> record = new ProducerRecord<>(TransactionPostedEvent.TOPIC, key, value);
        record.headers().add(TransactionEventListener.EVENT_ID_HEADER, eventId.toString().getBytes(StandardCharsets.UTF_8));
        record.headers().add(TransactionEventListener.EVENT_TYPE_HEADER,
                TransactionPostedEvent.EVENT_TYPE.getBytes(StandardCharsets.UTF_8));
        kafkaTemplate.send(record).join();
    }

    private ConsumerRecord<String, String> awaitDeadLetter(String key) {
        await().atMost(WAIT).until(() -> {
            deadLetters.poll(Duration.ofMillis(200)).forEach(deadLettersSeen::add);
            return deadLettersSeen.stream().anyMatch(record -> key.equals(record.key()));
        });
        return deadLettersSeen.stream().filter(record -> key.equals(record.key())).findFirst().orElseThrow();
    }

    private double deadLetteredCount() {
        return registry.get("payledger.events.dead.lettered").counter().count();
    }

    private void assertNoDeadLetter(String key) {
        Instant until = Instant.now().plusSeconds(2);
        while (Instant.now().isBefore(until)) {
            deadLetters.poll(Duration.ofMillis(200)).forEach(deadLettersSeen::add);
        }
        assertThat(deadLettersSeen).noneMatch(record -> key.equals(record.key()));
    }

    private List<Notification> notificationsFor(TransactionPostedEvent event) {
        return notifications.findByTransactionId(event.transactionId());
    }

    private String json(TransactionPostedEvent event) {
        return jsonMapper.writeValueAsString(event);
    }

    private static TransactionPostedEvent transfer(String from, String to) {
        return new TransactionPostedEvent(UUID.randomUUID(), TransactionPostedEvent.TRANSFER,
                UUID.randomUUID(), from, UUID.randomUUID(), to, 40_000, "BDT", "Rent", Instant.now());
    }

    private static String header(ConsumerRecord<?, ?> record, String name) {
        Header header = record.headers().lastHeader(name);
        return header == null ? null : new String(header.value(), StandardCharsets.UTF_8);
    }
}
