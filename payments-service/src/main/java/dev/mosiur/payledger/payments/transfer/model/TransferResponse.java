package dev.mosiur.payledger.payments.transfer.model;

import dev.mosiur.payledger.payments.account.domain.Currency;
import dev.mosiur.payledger.payments.ledger.domain.LedgerTransaction;
import dev.mosiur.payledger.payments.ledger.domain.TransactionStatus;
import java.time.Instant;
import java.util.UUID;

public record TransferResponse(
        UUID id,
        TransactionStatus status,
        UUID fromAccountId,
        UUID toAccountId,
        long amountMinor,
        Currency currency,
        String reference,
        Instant createdAt) {

    public static TransferResponse of(LedgerTransaction transaction, TransferRequest request, Currency currency) {
        return new TransferResponse(
                transaction.getId(),
                transaction.getStatus(),
                request.fromAccountId(),
                request.toAccountId(),
                request.amountMinor(),
                currency,
                transaction.getReference(),
                transaction.getCreatedAt());
    }
}
