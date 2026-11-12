package dev.mosiur.payledger.notification.notification.service.impl;

import dev.mosiur.payledger.notification.notification.domain.Notification;
import dev.mosiur.payledger.notification.notification.model.TransactionPostedEvent;
import dev.mosiur.payledger.notification.notification.repository.NotificationRepository;
import dev.mosiur.payledger.notification.notification.repository.ProcessedEventRepository;
import dev.mosiur.payledger.notification.notification.service.NotificationService;
import java.time.Clock;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NotificationServiceImpl implements NotificationService {

    private final ProcessedEventRepository processedEvents;
    private final NotificationRepository notifications;
    private final Clock clock;
    private final MeterRegistry registry;
    private final Counter duplicates;

    public NotificationServiceImpl(ProcessedEventRepository processedEvents, NotificationRepository notifications,
            Clock clock, MeterRegistry registry) {
        this.processedEvents = processedEvents;
        this.notifications = notifications;
        this.clock = clock;
        this.registry = registry;
        this.duplicates = Counter.builder("payledger.events.duplicates")
                .description("Redelivered events that were ignored")
                .register(registry);
    }

    @Override
    @Transactional
    public boolean handle(UUID eventId, TransactionPostedEvent event) {
        Instant now = clock.instant();
        // Claim the event and write its effects in one transaction: a failure rolls back both, so a retry starts clean.
        if (!processedEvents.markProcessed(eventId, TransactionPostedEvent.EVENT_TYPE, now)) {
            duplicates.increment();
            return false;
        }
        List<Notification> created = notifications.saveAll(NotificationComposer.compose(eventId, event, now));
        created.forEach(notification -> Counter.builder("payledger.notifications.stored")
                .description("Notifications stored, by kind")
                .tag("kind", notification.getKind().name())
                .register(registry)
                .increment());
        return true;
    }
}
