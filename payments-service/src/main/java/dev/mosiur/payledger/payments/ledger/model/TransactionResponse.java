package dev.mosiur.payledger.payments.ledger.model;

import dev.mosiur.payledger.payments.account.domain.Currency;
import dev.mosiur.payledger.payments.ledger.domain.LedgerTransaction;
import dev.mosiur.payledger.payments.ledger.domain.TransactionStatus;
import dev.mosiur.payledger.payments.ledger.domain.TransactionType;
import java.time.Instant;
import java.util.UUID;

public record TransactionResponse(
        UUID id,
        TransactionType type,
        TransactionStatus status,
        UUID accountId,
        long amountMinor,
        Currency currency,
        String reference,
        Instant createdAt) {

    public static TransactionResponse of(
            LedgerTransaction transaction, UUID accountId, long amountMinor, Currency currency) {
        return new TransactionResponse(
                transaction.getId(),
                transaction.getType(),
                transaction.getStatus(),
                accountId,
                amountMinor,
                currency,
                transaction.getReference(),
                transaction.getCreatedAt());
    }
}
