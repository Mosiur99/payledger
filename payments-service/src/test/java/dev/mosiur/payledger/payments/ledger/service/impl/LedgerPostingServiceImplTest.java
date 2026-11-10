package dev.mosiur.payledger.payments.ledger.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import dev.mosiur.payledger.payments.account.domain.Account;
import dev.mosiur.payledger.payments.account.domain.Currency;
import dev.mosiur.payledger.payments.ledger.domain.EntryDirection;
import dev.mosiur.payledger.payments.ledger.domain.LedgerEntry;
import dev.mosiur.payledger.payments.ledger.domain.LedgerTransaction;
import dev.mosiur.payledger.payments.ledger.domain.TransactionStatus;
import dev.mosiur.payledger.payments.ledger.domain.TransactionType;
import dev.mosiur.payledger.payments.ledger.model.Posting;
import dev.mosiur.payledger.payments.ledger.repository.LedgerEntryRepository;
import dev.mosiur.payledger.payments.ledger.repository.LedgerTransactionRepository;
import dev.mosiur.payledger.payments.ledger.service.CurrencyMismatchException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class LedgerPostingServiceImplTest {

    private static final Instant NOW = Instant.parse("2026-11-10T08:00:00Z");

    @Mock
    private LedgerTransactionRepository transactionRepository;

    @Mock
    private LedgerEntryRepository entryRepository;

    private LedgerPostingServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new LedgerPostingServiceImpl(transactionRepository, entryRepository, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void writesOneDebitAndOneCreditOfTheSameAmount() {
        when(transactionRepository.save(any(LedgerTransaction.class))).thenAnswer(call -> call.getArgument(0));
        Account system = account(Currency.BDT);
        Account ayesha = account(Currency.BDT);

        LedgerTransaction transaction = service.post(posting(system, ayesha, 50_000));

        assertThat(transaction.getType()).isEqualTo(TransactionType.DEPOSIT);
        assertThat(transaction.getStatus()).isEqualTo(TransactionStatus.COMPLETED);
        assertThat(transaction.getCreatedAt()).isEqualTo(NOW);
        assertThat(transaction.getInitiatedBy()).isEqualTo("admin");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<LedgerEntry>> entries = ArgumentCaptor.forClass(List.class);
        verify(entryRepository).saveAll(entries.capture());
        assertThat(entries.getValue())
                .extracting(LedgerEntry::getAccountId, LedgerEntry::getDirection, LedgerEntry::getAmountMinor)
                .containsExactly(
                        tuple(system.getId(), EntryDirection.DEBIT, 50_000L),
                        tuple(ayesha.getId(), EntryDirection.CREDIT, 50_000L));
    }

    @Test
    void movesBothBalances() {
        when(transactionRepository.save(any(LedgerTransaction.class))).thenAnswer(call -> call.getArgument(0));
        Account system = account(Currency.BDT);
        Account ayesha = account(Currency.BDT);

        service.post(posting(system, ayesha, 50_000));

        assertThat(system.getBalanceMinor()).isEqualTo(-50_000);
        assertThat(ayesha.getBalanceMinor()).isEqualTo(50_000);
    }

    @Test
    void refusesToMixCurrencies() {
        Account bdt = account(Currency.BDT);
        Account eur = account(Currency.EUR);

        assertThatThrownBy(() -> service.post(posting(bdt, eur, 100)))
                .isInstanceOf(CurrencyMismatchException.class);
        assertThat(bdt.getBalanceMinor()).isZero();
        verifyNoInteractions(transactionRepository, entryRepository);
    }

    @Test
    void refusesToPostToTheSameAccount() {
        Account ayesha = account(Currency.BDT);

        assertThatThrownBy(() -> service.post(posting(ayesha, ayesha, 100)))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(transactionRepository, entryRepository);
    }

    private static Posting posting(Account debit, Account credit, long amount) {
        return new Posting(TransactionType.DEPOSIT, debit, credit, amount, "top-up", "admin");
    }

    private static Account account(Currency currency) {
        Account account = Account.openUserAccount("owner", currency, NOW);
        ReflectionTestUtils.setField(account, "id", UUID.randomUUID());
        return account;
    }
}
