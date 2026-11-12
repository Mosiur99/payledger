package dev.mosiur.payledger.notification.notification.service.impl;

import dev.mosiur.payledger.notification.notification.model.TransactionPostedEvent;
import dev.mosiur.payledger.notification.notification.repository.NotificationRepository;
import dev.mosiur.payledger.notification.notification.repository.ProcessedEventRepository;
import dev.mosiur.payledger.notification.notification.service.NotificationService;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NotificationServiceImpl implements NotificationService {

    private final ProcessedEventRepository processedEvents;
    private final NotificationRepository notifications;
    private final Clock clock;

    public NotificationServiceImpl(ProcessedEventRepository processedEvents, NotificationRepository notifications,
            Clock clock) {
        this.processedEvents = processedEvents;
        this.notifications = notifications;
        this.clock = clock;
    }

    @Override
    @Transactional
    public boolean handle(UUID eventId, TransactionPostedEvent event) {
        Instant now = clock.instant();
        // Claim the event and write its effects in one transaction: a failure rolls back both, so a retry starts clean.
        if (!processedEvents.markProcessed(eventId, TransactionPostedEvent.EVENT_TYPE, now)) {
            return false;
        }
        notifications.saveAll(NotificationComposer.compose(eventId, event, now));
        return true;
    }
}
