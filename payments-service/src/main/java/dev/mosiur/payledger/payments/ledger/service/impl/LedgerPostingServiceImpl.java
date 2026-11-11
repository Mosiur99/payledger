package dev.mosiur.payledger.payments.ledger.service.impl;

import dev.mosiur.payledger.payments.account.domain.Account;
import dev.mosiur.payledger.payments.ledger.domain.LedgerEntry;
import dev.mosiur.payledger.payments.ledger.domain.LedgerTransaction;
import dev.mosiur.payledger.payments.ledger.model.Posting;
import dev.mosiur.payledger.payments.ledger.model.TransactionPostedEvent;
import dev.mosiur.payledger.payments.ledger.repository.LedgerEntryRepository;
import dev.mosiur.payledger.payments.ledger.repository.LedgerTransactionRepository;
import dev.mosiur.payledger.payments.ledger.service.CurrencyMismatchException;
import dev.mosiur.payledger.payments.ledger.service.LedgerPostingService;
import dev.mosiur.payledger.payments.outbox.service.OutboxService;
import java.time.Clock;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LedgerPostingServiceImpl implements LedgerPostingService {

    private final LedgerTransactionRepository transactionRepository;
    private final LedgerEntryRepository entryRepository;
    private final OutboxService outboxService;
    private final Clock clock;

    public LedgerPostingServiceImpl(LedgerTransactionRepository transactionRepository,
            LedgerEntryRepository entryRepository, OutboxService outboxService, Clock clock) {
        this.transactionRepository = transactionRepository;
        this.entryRepository = entryRepository;
        this.outboxService = outboxService;
        this.clock = clock;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public LedgerTransaction post(Posting posting) {
        Account debit = posting.debitAccount();
        Account credit = posting.creditAccount();
        if (debit.getId().equals(credit.getId())) {
            throw new IllegalArgumentException("Debit and credit account must differ");
        }
        if (debit.getCurrency() != credit.getCurrency()) {
            throw new CurrencyMismatchException(debit.getCurrency(), credit.getCurrency());
        }

        debit.debit(posting.amountMinor());
        credit.credit(posting.amountMinor());

        LedgerTransaction transaction = transactionRepository.save(LedgerTransaction.completed(
                posting.type(), posting.reference(), posting.initiatedBy(), clock.instant()));
        entryRepository.saveAll(List.of(
                LedgerEntry.debit(transaction, debit, posting.amountMinor()),
                LedgerEntry.credit(transaction, credit, posting.amountMinor())));
        outboxService.record(TransactionPostedEvent.TOPIC, transaction.getId().toString(),
                TransactionPostedEvent.EVENT_TYPE,
                TransactionPostedEvent.of(transaction, debit, credit, posting.amountMinor()));
        return transaction;
    }
}
