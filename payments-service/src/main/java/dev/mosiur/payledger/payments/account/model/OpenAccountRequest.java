package dev.mosiur.payledger.payments.account.model;

import dev.mosiur.payledger.payments.account.domain.Currency;
import jakarta.validation.constraints.NotNull;

public record OpenAccountRequest(@NotNull Currency currency) {
}
