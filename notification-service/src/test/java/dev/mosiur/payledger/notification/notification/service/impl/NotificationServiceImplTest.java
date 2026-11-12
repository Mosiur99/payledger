package dev.mosiur.payledger.notification.notification.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.mosiur.payledger.notification.notification.domain.Notification;
import dev.mosiur.payledger.notification.notification.model.TransactionPostedEvent;
import dev.mosiur.payledger.notification.notification.repository.NotificationRepository;
import dev.mosiur.payledger.notification.notification.repository.ProcessedEventRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class NotificationServiceImplTest {

    private static final Instant NOW = Instant.parse("2026-11-12T08:00:00Z");
    private static final UUID EVENT = UUID.randomUUID();

    @Mock
    private ProcessedEventRepository processedEvents;

    @Mock
    private NotificationRepository notifications;

    private NotificationServiceImpl service;
    private SimpleMeterRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
        service = new NotificationServiceImpl(processedEvents, notifications, Clock.fixed(NOW, ZoneOffset.UTC), registry);
    }

    @Test
    void storesNotificationsForANewEvent() {
        when(processedEvents.markProcessed(EVENT, TransactionPostedEvent.EVENT_TYPE, NOW)).thenReturn(true);
        when(notifications.saveAll(anyList())).thenAnswer(call -> call.getArgument(0));

        assertThat(service.handle(EVENT, transfer())).isTrue();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Notification>> saved = ArgumentCaptor.forClass(List.class);
        verify(notifications).saveAll(saved.capture());
        assertThat(saved.getValue()).hasSize(2);
        assertThat(registry.get("payledger.notifications.stored").tag("kind", "MONEY_SENT").counter().count()).isEqualTo(1);
        assertThat(registry.get("payledger.notifications.stored").tag("kind", "MONEY_RECEIVED").counter().count()).isEqualTo(1);
    }

    @Test
    void ignoresAnEventThatWasAlreadyProcessed() {
        when(processedEvents.markProcessed(eq(EVENT), eq(TransactionPostedEvent.EVENT_TYPE), eq(NOW))).thenReturn(false);

        assertThat(service.handle(EVENT, transfer())).isFalse();

        verify(notifications, never()).saveAll(anyList());
        assertThat(registry.get("payledger.events.duplicates").counter().count()).isEqualTo(1);
    }

    private static TransactionPostedEvent transfer() {
        return new TransactionPostedEvent(UUID.randomUUID(), "TRANSFER", UUID.randomUUID(), "ayesha",
                UUID.randomUUID(), "rahim", 100, "BDT", null, NOW);
    }
}
