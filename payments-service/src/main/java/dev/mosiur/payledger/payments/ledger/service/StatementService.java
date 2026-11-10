package dev.mosiur.payledger.payments.ledger.service;

import dev.mosiur.payledger.payments.ledger.model.StatementResponse;
import java.util.UUID;

public interface StatementService {

    StatementResponse statement(String ownerId, UUID accountId, UUID before, int limit);
}
