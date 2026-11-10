package dev.mosiur.payledger.payments.ledger.model;

import dev.mosiur.payledger.payments.account.domain.Account;
import dev.mosiur.payledger.payments.ledger.domain.TransactionType;

public record Posting(
        TransactionType type,
        Account debitAccount,
        Account creditAccount,
        long amountMinor,
        String reference,
        String initiatedBy) {
}
