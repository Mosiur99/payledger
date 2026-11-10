package dev.mosiur.payledger.payments.transfer.service.impl;

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
import dev.mosiur.payledger.payments.ledger.model.Posting;
import dev.mosiur.payledger.payments.ledger.service.CurrencyMismatchException;
import dev.mosiur.payledger.payments.ledger.service.LedgerPostingService;
import dev.mosiur.payledger.payments.transfer.model.TransferRequest;
import dev.mosiur.payledger.payments.transfer.model.TransferResponse;
import dev.mosiur.payledger.payments.transfer.service.SameAccountTransferException;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class TransferServiceImplTest {

    private static final Instant NOW = Instant.parse("2026-11-10T08:00:00Z");

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private LedgerPostingService ledgerPostingService;

    @InjectMocks
    private TransferServiceImpl service;

    private final Account ayesha = account("ayesha", Currency.BDT);
    private final Account rahim = account("rahim", Currency.BDT);

    @Test
    void postsATransferFromTheCallersAccount() {
        givenLocked(ayesha, rahim);
        when(ledgerPostingService.post(any()))
                .thenReturn(LedgerTransaction.completed(TransactionType.TRANSFER, "rent", "ayesha", NOW));

        TransferResponse response = service.transfer("ayesha", request(ayesha, rahim));

        ArgumentCaptor<Posting> posting = ArgumentCaptor.forClass(Posting.class);
        verify(ledgerPostingService).post(posting.capture());
        assertThat(posting.getValue().type()).isEqualTo(TransactionType.TRANSFER);
        assertThat(posting.getValue().debitAccount()).isSameAs(ayesha);
        assertThat(posting.getValue().creditAccount()).isSameAs(rahim);
        assertThat(posting.getValue().initiatedBy()).isEqualTo("ayesha");
        assertThat(response.currency()).isEqualTo(Currency.BDT);
        assertThat(response.amountMinor()).isEqualTo(5_000);
    }

    @Test
    void rejectsATransferToTheSameAccountBeforeLocking() {
        assertThatThrownBy(() -> service.transfer("ayesha", request(ayesha, ayesha)))
                .isInstanceOf(SameAccountTransferException.class);
        verifyNoInteractions(accountRepository, ledgerPostingService);
    }

    @Test
    void hidesASourceAccountTheCallerDoesNotOwn() {
        givenLocked(ayesha, rahim);

        assertThatThrownBy(() -> service.transfer("rahim", request(ayesha, rahim)))
                .isInstanceOf(AccountNotFoundException.class)
                .hasMessageContaining(ayesha.getId().toString());
        verifyNoInteractions(ledgerPostingService);
    }

    @Test
    void rejectsAMissingDestination() {
        givenLocked(ayesha);

        assertThatThrownBy(() -> service.transfer("ayesha", request(ayesha, rahim)))
                .isInstanceOf(AccountNotFoundException.class)
                .hasMessageContaining(rahim.getId().toString());
    }

    @Test
    void rejectsASystemAccountAsDestination() {
        ReflectionTestUtils.setField(rahim, "type", AccountType.SYSTEM);
        givenLocked(ayesha, rahim);

        assertThatThrownBy(() -> service.transfer("ayesha", request(ayesha, rahim)))
                .isInstanceOf(AccountNotFoundException.class);
    }

    @Test
    void rejectsAFrozenAccountOnEitherSide() {
        ReflectionTestUtils.setField(rahim, "status", AccountStatus.FROZEN);
        givenLocked(ayesha, rahim);

        assertThatThrownBy(() -> service.transfer("ayesha", request(ayesha, rahim)))
                .isInstanceOf(AccountFrozenException.class);
        verifyNoInteractions(ledgerPostingService);
    }

    @Test
    void rejectsDifferentCurrencies() {
        Account euros = account("rahim", Currency.EUR);
        givenLocked(ayesha, euros);

        assertThatThrownBy(() -> service.transfer("ayesha", request(ayesha, euros)))
                .isInstanceOf(CurrencyMismatchException.class);
        verifyNoInteractions(ledgerPostingService);
    }

    private void givenLocked(Account... accounts) {
        Map<UUID, Account> byId = new HashMap<>();
        for (Account account : accounts) {
            byId.put(account.getId(), account);
        }
        when(accountRepository.lockByIds(any(UUID.class), any(UUID.class))).thenReturn(byId);
    }

    private static TransferRequest request(Account from, Account to) {
        return new TransferRequest(from.getId(), to.getId(), 5_000, "rent");
    }

    private static Account account(String owner, Currency currency) {
        Account account = Account.openUserAccount(owner, currency, NOW);
        ReflectionTestUtils.setField(account, "id", UUID.randomUUID());
        return account;
    }
}
