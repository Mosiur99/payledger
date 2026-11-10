package dev.mosiur.payledger.payments.ledger.service.impl;

import dev.mosiur.payledger.payments.account.domain.Account;
import dev.mosiur.payledger.payments.account.domain.AccountType;
import dev.mosiur.payledger.payments.account.domain.Currency;
import dev.mosiur.payledger.payments.account.repository.AccountRepository;
import dev.mosiur.payledger.payments.account.service.AccountFrozenException;
import dev.mosiur.payledger.payments.account.service.AccountNotFoundException;
import dev.mosiur.payledger.payments.ledger.domain.LedgerTransaction;
import dev.mosiur.payledger.payments.ledger.domain.TransactionType;
import dev.mosiur.payledger.payments.ledger.model.DepositRequest;
import dev.mosiur.payledger.payments.ledger.model.Posting;
import dev.mosiur.payledger.payments.ledger.model.TransactionResponse;
import dev.mosiur.payledger.payments.ledger.service.DepositService;
import dev.mosiur.payledger.payments.ledger.service.LedgerPostingService;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DepositServiceImpl implements DepositService {

    private final AccountRepository accountRepository;
    private final LedgerPostingService ledgerPostingService;

    public DepositServiceImpl(AccountRepository accountRepository, LedgerPostingService ledgerPostingService) {
        this.accountRepository = accountRepository;
        this.ledgerPostingService = ledgerPostingService;
    }

    @Override
    @Transactional
    public TransactionResponse deposit(String initiatedBy, DepositRequest request) {
        UUID accountId = request.accountId();
        // Read only the currency here: loading the entity before locking it would keep a stale balance in memory.
        Currency currency = accountRepository.findCurrency(accountId, AccountType.USER)
                .orElseThrow(() -> new AccountNotFoundException(accountId));
        UUID systemAccountId = accountRepository.findIdByTypeAndCurrency(AccountType.SYSTEM, currency)
                .orElseThrow(() -> new IllegalStateException("No system account for " + currency));

        Map<UUID, Account> locked = lock(systemAccountId, accountId);
        Account target = locked.get(accountId);
        if (!target.isActive()) {
            throw new AccountFrozenException(accountId);
        }

        LedgerTransaction transaction = ledgerPostingService.post(new Posting(
                TransactionType.DEPOSIT,
                locked.get(systemAccountId),
                target,
                request.amountMinor(),
                request.reference(),
                initiatedBy));
        return TransactionResponse.of(transaction, accountId, request.amountMinor(), currency);
    }

    private Map<UUID, Account> lock(UUID... ids) {
        return accountRepository.lockAllInIdOrder(List.of(ids)).stream()
                .collect(Collectors.toMap(Account::getId, Function.identity()));
    }
}
