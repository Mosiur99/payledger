package dev.mosiur.payledger.payments.account.domain;

import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.UNPROCESSABLE_CONTENT)
public class InsufficientFundsException extends RuntimeException {

    public InsufficientFundsException(UUID accountId, long availableMinor, long requestedMinor) {
        super("Account " + accountId + " has " + availableMinor + " available; " + requestedMinor + " requested");
    }
}
