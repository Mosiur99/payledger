package dev.mosiur.payledger.payments.ledger.service;

import dev.mosiur.payledger.payments.ledger.model.DepositRequest;
import dev.mosiur.payledger.payments.ledger.model.TransactionResponse;

public interface DepositService {

    TransactionResponse deposit(String initiatedBy, DepositRequest request);
}
