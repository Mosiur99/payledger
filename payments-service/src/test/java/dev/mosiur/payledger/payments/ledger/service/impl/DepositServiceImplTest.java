package dev.mosiur.payledger.payments.ledger.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import dev.mosiur.payledger.payments.account.domain.Account;
import dev.mosiur.payledger.payments.account.domain.AccountStatus;
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
import dev.mosiur.payledger.payments.ledger.service.LedgerPostingService;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class DepositServiceImplTest {

    private static final Instant NOW = Instant.parse("2026-11-10T08:00:00Z");

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private LedgerPostingService ledgerPostingService;

    @InjectMocks
    private DepositServiceImpl service;

    private Account system;
    private Account ayesha;

    @BeforeEach
    void setUp() {
        system = withId(Account.openUserAccount("system", Currency.BDT, NOW));
        ayesha = withId(Account.openUserAccount("ayesha", Currency.BDT, NOW));
    }

    @Test
    void fundsTheAccountFromTheSystemAccountOfItsCurrency() {
        givenLockableAccounts();
        LedgerTransaction transaction = LedgerTransaction.completed(TransactionType.DEPOSIT, "top-up", "admin", NOW);
        when(ledgerPostingService.post(any())).thenReturn(transaction);

        TransactionResponse response = service.deposit("admin", new DepositRequest(ayesha.getId(), 50_000, "top-up"));

        ArgumentCaptor<Posting> posting = ArgumentCaptor.forClass(Posting.class);
        verify(ledgerPostingService).post(posting.capture());
        assertThat(posting.getValue().debitAccount()).isSameAs(system);
        assertThat(posting.getValue().creditAccount()).isSameAs(ayesha);
        assertThat(posting.getValue().amountMinor()).isEqualTo(50_000);
        assertThat(response.accountId()).isEqualTo(ayesha.getId());
        assertThat(response.currency()).isEqualTo(Currency.BDT);
        assertThat(response.type()).isEqualTo(TransactionType.DEPOSIT);
    }

    @Test
    void rejectsAnUnknownAccount() {
        UUID unknown = UUID.randomUUID();
        when(accountRepository.findCurrency(unknown, AccountType.USER)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.deposit("admin", new DepositRequest(unknown, 100, null)))
                .isInstanceOf(AccountNotFoundException.class);
        verifyNoInteractions(ledgerPostingService);
    }

    @Test
    void rejectsAFrozenAccount() {
        ReflectionTestUtils.setField(ayesha, "status", AccountStatus.FROZEN);
        givenLockableAccounts();

        assertThatThrownBy(() -> service.deposit("admin", new DepositRequest(ayesha.getId(), 100, null)))
                .isInstanceOf(AccountFrozenException.class);
        verifyNoInteractions(ledgerPostingService);
    }

    private void givenLockableAccounts() {
        when(accountRepository.findCurrency(ayesha.getId(), AccountType.USER)).thenReturn(Optional.of(Currency.BDT));
        when(accountRepository.findIdByTypeAndCurrency(AccountType.SYSTEM, Currency.BDT))
                .thenReturn(Optional.of(system.getId()));
        when(accountRepository.lockByIds(system.getId(), ayesha.getId()))
                .thenReturn(Map.of(system.getId(), system, ayesha.getId(), ayesha));
    }

    private static Account withId(Account account) {
        ReflectionTestUtils.setField(account, "id", UUID.randomUUID());
        return account;
    }
}
