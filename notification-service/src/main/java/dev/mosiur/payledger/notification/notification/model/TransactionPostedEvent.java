package dev.mosiur.payledger.notification.notification.model;

import java.time.Instant;
import java.util.UUID;

/**
 * This service's own copy of the payments-service event contract. Services share the JSON shape,
 * not a Java library, so each can be deployed on its own.
 */
public record TransactionPostedEvent(
        UUID transactionId,
        String type,
        UUID debitAccountId,
        String debitOwnerId,
        UUID creditAccountId,
        String creditOwnerId,
        long amountMinor,
        String currency,
        String reference,
        Instant occurredAt) {

    public static final String TOPIC = "payledger.transactions";
    public static final String EVENT_TYPE = "TransactionPosted";
    public static final String DEPOSIT = "DEPOSIT";
    public static final String TRANSFER = "TRANSFER";
}
