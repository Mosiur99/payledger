package dev.mosiur.payledger.payments.ledger.model;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record DepositRequest(
        @NotNull UUID accountId,
        @Positive @Max(MAX_AMOUNT_MINOR) long amountMinor,
        @Size(max = 140) String reference) {

    public static final long MAX_AMOUNT_MINOR = 1_000_000_000L;
}
