package dev.mosiur.payledger.payments.account.domain;

import java.util.UUID;

public class InsufficientFundsException extends RuntimeException {

    private final UUID accountId;
    private final long availableMinor;
    private final long requestedMinor;

    public InsufficientFundsException(UUID accountId, long availableMinor, long requestedMinor) {
        super("Account " + accountId + " has " + availableMinor + " available; " + requestedMinor + " requested.");
        this.accountId = accountId;
        this.availableMinor = availableMinor;
        this.requestedMinor = requestedMinor;
    }

    public UUID getAccountId() {
        return accountId;
    }

    public long getAvailableMinor() {
        return availableMinor;
    }

    public long getRequestedMinor() {
        return requestedMinor;
    }
}
