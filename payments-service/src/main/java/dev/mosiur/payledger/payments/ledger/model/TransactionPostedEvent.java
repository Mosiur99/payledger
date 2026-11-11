package dev.mosiur.payledger.payments.ledger.model;

import dev.mosiur.payledger.payments.account.domain.Account;
import dev.mosiur.payledger.payments.account.domain.Currency;
import dev.mosiur.payledger.payments.ledger.domain.LedgerTransaction;
import dev.mosiur.payledger.payments.ledger.domain.TransactionType;
import java.time.Instant;
import java.util.UUID;

/** Published to Kafka for every committed ledger transaction. Owner ids let consumers notify the right people. */
public record TransactionPostedEvent(
        UUID transactionId,
        TransactionType type,
        UUID debitAccountId,
        String debitOwnerId,
        UUID creditAccountId,
        String creditOwnerId,
        long amountMinor,
        Currency currency,
        String reference,
        Instant occurredAt) {

    public static final String TOPIC = "payledger.transactions";
    public static final String EVENT_TYPE = "TransactionPosted";

    public static TransactionPostedEvent of(LedgerTransaction transaction, Account debit, Account credit, long amountMinor) {
        return new TransactionPostedEvent(
                transaction.getId(),
                transaction.getType(),
                debit.getId(),
                debit.getOwnerId(),
                credit.getId(),
                credit.getOwnerId(),
                amountMinor,
                debit.getCurrency(),
                transaction.getReference(),
                transaction.getCreatedAt());
    }
}
