package dev.mosiur.payledger.payments.transfer.service.impl;

import dev.mosiur.payledger.payments.account.domain.Account;
import dev.mosiur.payledger.payments.account.repository.AccountRepository;
import dev.mosiur.payledger.payments.account.service.AccountFrozenException;
import dev.mosiur.payledger.payments.account.service.AccountNotFoundException;
import dev.mosiur.payledger.payments.ledger.domain.LedgerTransaction;
import dev.mosiur.payledger.payments.ledger.domain.TransactionType;
import dev.mosiur.payledger.payments.ledger.model.Posting;
import dev.mosiur.payledger.payments.ledger.service.CurrencyMismatchException;
import dev.mosiur.payledger.payments.ledger.service.LedgerPostingService;
import dev.mosiur.payledger.payments.transfer.model.TransferRequest;
import dev.mosiur.payledger.payments.transfer.model.TransferResponse;
import dev.mosiur.payledger.payments.transfer.service.SameAccountTransferException;
import dev.mosiur.payledger.payments.transfer.service.TransferService;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TransferServiceImpl implements TransferService {

    private final AccountRepository accountRepository;
    private final LedgerPostingService ledgerPostingService;

    public TransferServiceImpl(AccountRepository accountRepository, LedgerPostingService ledgerPostingService) {
        this.accountRepository = accountRepository;
        this.ledgerPostingService = ledgerPostingService;
    }

    @Override
    @Transactional
    public TransferResponse transfer(String ownerId, TransferRequest request) {
        if (request.fromAccountId().equals(request.toAccountId())) {
            throw new SameAccountTransferException(request.fromAccountId());
        }

        // Nothing is read before locking, so every check below sees the committed, current state.
        Map<UUID, Account> locked = accountRepository.lockByIds(request.fromAccountId(), request.toAccountId());
        Account from = locked.get(request.fromAccountId());
        Account to = locked.get(request.toAccountId());

        if (from == null || !from.getOwnerId().equals(ownerId) || !from.isUserAccount()) {
            throw new AccountNotFoundException(request.fromAccountId());
        }
        if (to == null || !to.isUserAccount()) {
            throw new AccountNotFoundException(request.toAccountId());
        }
        if (!from.isActive()) {
            throw new AccountFrozenException(from.getId());
        }
        if (!to.isActive()) {
            throw new AccountFrozenException(to.getId());
        }
        if (from.getCurrency() != to.getCurrency()) {
            throw new CurrencyMismatchException(from.getCurrency(), to.getCurrency());
        }

        LedgerTransaction transaction = ledgerPostingService.post(new Posting(
                TransactionType.TRANSFER, from, to, request.amountMinor(), request.reference(), ownerId));
        return TransferResponse.of(transaction, request, from.getCurrency());
    }
}
