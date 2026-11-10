package dev.mosiur.payledger.payments.transfer.service;

import java.util.UUID;

public class SameAccountTransferException extends RuntimeException {

    public SameAccountTransferException(UUID accountId) {
        super("Cannot transfer from account " + accountId + " to itself.");
    }
}
