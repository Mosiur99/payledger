package dev.mosiur.payledger.payments.account.service;

import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.CONFLICT)
public class AccountFrozenException extends RuntimeException {

    public AccountFrozenException(UUID id) {
        super("Account " + id + " is frozen");
    }
}
