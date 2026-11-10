package dev.mosiur.payledger.payments.transfer.service;

import dev.mosiur.payledger.payments.transfer.model.TransferRequest;
import dev.mosiur.payledger.payments.transfer.model.TransferResponse;

public interface TransferService {

    TransferResponse transfer(String ownerId, TransferRequest request);
}
