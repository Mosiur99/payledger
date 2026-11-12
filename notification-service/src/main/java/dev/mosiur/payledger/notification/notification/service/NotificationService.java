package dev.mosiur.payledger.notification.notification.service;

import dev.mosiur.payledger.notification.notification.model.TransactionPostedEvent;
import java.util.UUID;

public interface NotificationService {

    /** Creates the notifications for an event once; returns false if the event was already handled. */
    boolean handle(UUID eventId, TransactionPostedEvent event);
}
