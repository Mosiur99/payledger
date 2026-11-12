package dev.mosiur.payledger.notification.notification.service.impl;

import dev.mosiur.payledger.notification.notification.domain.Notification;
import dev.mosiur.payledger.notification.notification.domain.NotificationKind;
import dev.mosiur.payledger.notification.notification.model.TransactionPostedEvent;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Turns one event into the messages people should see. Pure, so it is easy to test. */
final class NotificationComposer {

    static final String SYSTEM_OWNER = "system";

    private NotificationComposer() {
    }

    static List<Notification> compose(UUID eventId, TransactionPostedEvent event, Instant now) {
        String amount = BigDecimal.valueOf(event.amountMinor(), 2).toPlainString() + " " + event.currency();
        String reference = event.reference() == null || event.reference().isBlank()
                ? "" : " Reference: " + event.reference() + ".";
        String from = masked(event.debitAccountId());
        String to = masked(event.creditAccountId());

        List<Notification> notifications = new ArrayList<>();
        if (TransactionPostedEvent.DEPOSIT.equals(event.type())) {
            add(notifications, eventId, event, event.creditOwnerId(), NotificationKind.DEPOSIT_RECEIVED,
                    amount + " was deposited into account " + to + "." + reference, now);
        } else {
            add(notifications, eventId, event, event.debitOwnerId(), NotificationKind.MONEY_SENT,
                    "You sent " + amount + " from account " + from + " to account " + to + "." + reference, now);
            add(notifications, eventId, event, event.creditOwnerId(), NotificationKind.MONEY_RECEIVED,
                    "You received " + amount + " in account " + to + "." + reference, now);
        }
        return notifications;
    }

    private static void add(List<Notification> notifications, UUID eventId, TransactionPostedEvent event,
            String recipient, NotificationKind kind, String message, Instant now) {
        if (!SYSTEM_OWNER.equals(recipient)) {
            notifications.add(new Notification(eventId, recipient, kind, event.transactionId(), message, now));
        }
    }

    private static String masked(UUID accountId) {
        String id = accountId.toString();
        return "…" + id.substring(id.length() - 4);
    }
}
