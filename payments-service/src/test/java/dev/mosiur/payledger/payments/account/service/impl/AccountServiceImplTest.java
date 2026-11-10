package dev.mosiur.payledger.payments.account.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.mosiur.payledger.payments.account.domain.Account;
import dev.mosiur.payledger.payments.account.domain.AccountStatus;
import dev.mosiur.payledger.payments.account.domain.AccountType;
import dev.mosiur.payledger.payments.account.domain.Currency;
import dev.mosiur.payledger.payments.account.model.AccountResponse;
import dev.mosiur.payledger.payments.account.model.OpenAccountRequest;
import dev.mosiur.payledger.payments.account.repository.AccountRepository;
import dev.mosiur.payledger.payments.account.service.AccountNotFoundException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AccountServiceImplTest {

    private static final Instant NOW = Instant.parse("2026-11-10T08:00:00Z");

    @Mock
    private AccountRepository accountRepository;

    private AccountServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new AccountServiceImpl(accountRepository, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void opensAnActiveUserAccountWithZeroBalance() {
        when(accountRepository.save(any(Account.class))).thenAnswer(call -> call.getArgument(0));

        AccountResponse response = service.open("ayesha", new OpenAccountRequest(Currency.BDT));

        ArgumentCaptor<Account> saved = ArgumentCaptor.forClass(Account.class);
        verify(accountRepository).save(saved.capture());
        Account account = saved.getValue();
        assertThat(account.getOwnerId()).isEqualTo("ayesha");
        assertThat(account.getCurrency()).isEqualTo(Currency.BDT);
        assertThat(account.getType()).isEqualTo(AccountType.USER);
        assertThat(account.getStatus()).isEqualTo(AccountStatus.ACTIVE);
        assertThat(account.getBalanceMinor()).isZero();
        assertThat(account.getCreatedAt()).isEqualTo(NOW);
        assertThat(response.currency()).isEqualTo(Currency.BDT);
        assertThat(response.balanceMinor()).isZero();
    }

    @Test
    void listsTheOwnersAccounts() {
        when(accountRepository.findByOwnerIdOrderByCreatedAtAsc("ayesha")).thenReturn(List.of(
                Account.openUserAccount("ayesha", Currency.BDT, NOW),
                Account.openUserAccount("ayesha", Currency.EUR, NOW)));

        assertThat(service.listForOwner("ayesha"))
                .extracting(AccountResponse::currency)
                .containsExactly(Currency.BDT, Currency.EUR);
    }

    @Test
    void returnsTheAccountToItsOwner() {
        UUID id = UUID.randomUUID();
        when(accountRepository.findByIdAndOwnerId(id, "ayesha"))
                .thenReturn(Optional.of(Account.openUserAccount("ayesha", Currency.USD, NOW)));

        assertThat(service.getForOwner("ayesha", id).currency()).isEqualTo(Currency.USD);
    }

    @Test
    void reportsAnotherOwnersAccountAsNotFound() {
        UUID id = UUID.randomUUID();
        when(accountRepository.findByIdAndOwnerId(id, "rahim")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getForOwner("rahim", id))
                .isInstanceOf(AccountNotFoundException.class)
                .hasMessageContaining(id.toString());
    }
}
