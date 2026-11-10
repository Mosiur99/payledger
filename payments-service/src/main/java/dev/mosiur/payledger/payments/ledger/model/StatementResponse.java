package dev.mosiur.payledger.payments.ledger.model;

import dev.mosiur.payledger.payments.account.domain.Currency;
import java.util.List;
import java.util.UUID;

/** {@code nextBefore} is the cursor for the next (older) page, or null when this is the last page. */
public record StatementResponse(
        UUID accountId,
        Currency currency,
        long balanceMinor,
        List<StatementEntry> entries,
        UUID nextBefore) {
}
