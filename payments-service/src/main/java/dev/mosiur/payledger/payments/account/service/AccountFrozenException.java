package dev.mosiur.payledger.payments.account.service;

import java.util.UUID;

public class AccountFrozenException extends RuntimeException {

    public AccountFrozenException(UUID id) {
        super("Account " + id + " is frozen.");
    }
}
