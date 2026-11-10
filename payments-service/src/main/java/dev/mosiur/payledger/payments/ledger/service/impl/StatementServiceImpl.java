package dev.mosiur.payledger.payments.ledger.service.impl;

import dev.mosiur.payledger.payments.account.domain.Account;
import dev.mosiur.payledger.payments.account.repository.AccountRepository;
import dev.mosiur.payledger.payments.account.service.AccountNotFoundException;
import dev.mosiur.payledger.payments.ledger.model.StatementEntry;
import dev.mosiur.payledger.payments.ledger.model.StatementResponse;
import dev.mosiur.payledger.payments.ledger.repository.LedgerEntryRepository;
import dev.mosiur.payledger.payments.ledger.service.StatementService;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StatementServiceImpl implements StatementService {

    private final AccountRepository accountRepository;
    private final LedgerEntryRepository entryRepository;

    public StatementServiceImpl(AccountRepository accountRepository, LedgerEntryRepository entryRepository) {
        this.accountRepository = accountRepository;
        this.entryRepository = entryRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public StatementResponse statement(String ownerId, UUID accountId, UUID before, int limit) {
        Account account = accountRepository.findByIdAndOwnerId(accountId, ownerId)
                .orElseThrow(() -> new AccountNotFoundException(accountId));

        List<StatementEntry> entries = before == null
                ? entryRepository.findNewestFirst(accountId, Limit.of(limit))
                : entryRepository.findNewestFirstBefore(accountId, before, Limit.of(limit));
        UUID nextBefore = entries.size() == limit ? entries.getLast().entryId() : null;

        return new StatementResponse(
                account.getId(), account.getCurrency(), account.getBalanceMinor(), entries, nextBefore);
    }
}
