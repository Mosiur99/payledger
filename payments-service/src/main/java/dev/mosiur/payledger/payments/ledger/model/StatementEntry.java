package dev.mosiur.payledger.payments.ledger.model;

import dev.mosiur.payledger.payments.ledger.domain.EntryDirection;
import dev.mosiur.payledger.payments.ledger.domain.TransactionType;
import java.time.Instant;
import java.util.UUID;

public record StatementEntry(
        UUID entryId,
        UUID transactionId,
        TransactionType type,
        String reference,
        EntryDirection direction,
        long amountMinor,
        Instant createdAt) {
}
