package dev.mosiur.payledger.payments.account.model;

import dev.mosiur.payledger.payments.account.domain.Account;
import dev.mosiur.payledger.payments.account.domain.AccountStatus;
import dev.mosiur.payledger.payments.account.domain.Currency;
import java.time.Instant;
import java.util.UUID;

public record AccountResponse(UUID id, Currency currency, AccountStatus status, long balanceMinor, Instant createdAt) {

    public static AccountResponse from(Account account) {
        return new AccountResponse(
                account.getId(),
                account.getCurrency(),
                account.getStatus(),
                account.getBalanceMinor(),
                account.getCreatedAt());
    }
}
