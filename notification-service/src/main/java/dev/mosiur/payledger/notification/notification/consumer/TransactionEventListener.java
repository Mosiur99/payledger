package dev.mosiur.payledger.notification.notification.consumer;

import dev.mosiur.payledger.notification.notification.model.TransactionPostedEvent;
import dev.mosiur.payledger.notification.notification.service.NotificationService;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

@Component
public class TransactionEventListener {

    public static final String EVENT_ID_HEADER = "eventId";
    public static final String EVENT_TYPE_HEADER = "eventType";

    private static final Set<String> TRANSACTION_TYPES = Set.of(TransactionPostedEvent.DEPOSIT, TransactionPostedEvent.TRANSFER);
    private static final Logger log = LoggerFactory.getLogger(TransactionEventListener.class);

    private final NotificationService notificationService;
    private final JsonMapper jsonMapper;

    public TransactionEventListener(NotificationService notificationService, JsonMapper jsonMapper) {
        this.notificationService = notificationService;
        this.jsonMapper = jsonMapper;
    }

    @KafkaListener(topics = TransactionPostedEvent.TOPIC)
    public void onMessage(ConsumerRecord<String, String> record) {
        String eventType = header(record, EVENT_TYPE_HEADER);
        if (!TransactionPostedEvent.EVENT_TYPE.equals(eventType)) {
            // A newer producer may add event types; skipping them keeps this consumer working until it learns them.
            log.info("Skipping event type {} at offset {}", eventType, record.offset());
            return;
        }
        UUID eventId = eventId(record);
        TransactionPostedEvent event = parse(record.value());
        if (!notificationService.handle(eventId, event)) {
            log.info("Event {} was already processed; ignoring the duplicate", eventId);
        }
    }

    private UUID eventId(ConsumerRecord<String, String> record) {
        String value = header(record, EVENT_ID_HEADER);
        if (value == null) {
            throw new InvalidEventException("Missing " + EVENT_ID_HEADER + " header");
        }
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException e) {
            throw new InvalidEventException("Malformed " + EVENT_ID_HEADER + " header: " + value, e);
        }
    }

    private TransactionPostedEvent parse(String json) {
        TransactionPostedEvent event;
        try {
            event = jsonMapper.readValue(json, TransactionPostedEvent.class);
        } catch (JacksonException e) {
            throw new InvalidEventException("Payload is not a valid TransactionPosted event", e);
        }
        if (event == null || event.transactionId() == null || !TRANSACTION_TYPES.contains(event.type())
                || event.debitAccountId() == null || event.creditAccountId() == null
                || event.debitOwnerId() == null || event.creditOwnerId() == null
                || event.amountMinor() <= 0 || event.currency() == null) {
            throw new InvalidEventException("TransactionPosted event is missing required fields");
        }
        return event;
    }

    private static String header(ConsumerRecord<?, ?> record, String name) {
        Header header = record.headers().lastHeader(name);
        return header == null ? null : new String(header.value(), StandardCharsets.UTF_8);
    }
}
