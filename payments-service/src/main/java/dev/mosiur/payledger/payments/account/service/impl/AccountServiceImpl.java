package dev.mosiur.payledger.payments.account.service.impl;

import dev.mosiur.payledger.payments.account.domain.Account;
import dev.mosiur.payledger.payments.account.model.AccountResponse;
import dev.mosiur.payledger.payments.account.model.OpenAccountRequest;
import dev.mosiur.payledger.payments.account.repository.AccountRepository;
import dev.mosiur.payledger.payments.account.service.AccountNotFoundException;
import dev.mosiur.payledger.payments.account.service.AccountService;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountServiceImpl implements AccountService {

    private final AccountRepository accountRepository;
    private final Clock clock;

    public AccountServiceImpl(AccountRepository accountRepository, Clock clock) {
        this.accountRepository = accountRepository;
        this.clock = clock;
    }

    @Override
    @Transactional
    public AccountResponse open(String ownerId, OpenAccountRequest request) {
        Account account = Account.openUserAccount(ownerId, request.currency(), clock.instant());
        return AccountResponse.from(accountRepository.save(account));
    }

    @Override
    @Transactional(readOnly = true)
    public List<AccountResponse> listForOwner(String ownerId) {
        return accountRepository.findByOwnerIdOrderByCreatedAtAsc(ownerId).stream()
                .map(AccountResponse::from)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public AccountResponse getForOwner(String ownerId, UUID accountId) {
        // Someone else's account is reported as missing, so callers can't probe which ids exist.
        return accountRepository.findByIdAndOwnerId(accountId, ownerId)
                .map(AccountResponse::from)
                .orElseThrow(() -> new AccountNotFoundException(accountId));
    }
}
