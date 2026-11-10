package dev.mosiur.payledger.payments.ledger.service;

import dev.mosiur.payledger.payments.ledger.domain.LedgerTransaction;
import dev.mosiur.payledger.payments.ledger.model.Posting;

public interface LedgerPostingService {

    /** Records one balanced transaction. Both accounts must already be locked by the caller. */
    LedgerTransaction post(Posting posting);
}
