package dev.mosiur.payledger.payments.account.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.mosiur.payledger.payments.PostgresTestConfiguration;
import dev.mosiur.payledger.payments.account.domain.Account;
import dev.mosiur.payledger.payments.account.domain.Currency;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(PostgresTestConfiguration.class)
class AccountRepositoryIT {

    private static final Instant T0 = Instant.parse("2026-11-10T08:00:00Z");

    @Autowired
    private AccountRepository repository;

    @Autowired
    private JdbcClient jdbc;

    @Test
    void findsOnlyTheOwnersAccountsOldestFirst() {
        Account later = repository.save(Account.openUserAccount("ayesha", Currency.USD, T0.plusSeconds(60)));
        Account earlier = repository.save(Account.openUserAccount("ayesha", Currency.BDT, T0));
        repository.save(Account.openUserAccount("rahim", Currency.BDT, T0));

        assertThat(repository.findByOwnerIdOrderByCreatedAtAsc("ayesha"))
                .extracting(Account::getId)
                .containsExactly(earlier.getId(), later.getId());
    }

    @Test
    void findsAnAccountByIdOnlyForItsOwner() {
        Account account = repository.save(Account.openUserAccount("ayesha", Currency.BDT, T0));

        assertThat(repository.findByIdAndOwnerId(account.getId(), "ayesha")).contains(account);
        assertThat(repository.findByIdAndOwnerId(account.getId(), "rahim")).isEmpty();
    }

    @Test
    void generatesTimeOrderedIds() {
        Account first = repository.save(Account.openUserAccount("ayesha", Currency.BDT, T0));
        Account second = repository.save(Account.openUserAccount("ayesha", Currency.EUR, T0));

        assertThat(first.getId().version()).isEqualTo(7);
        assertThat(second.getId()).isGreaterThan(first.getId());
    }

    @Test
    void databaseRejectsANegativeUserBalance() {
        assertThatThrownBy(() -> insertAccount("USER", -1))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_accounts_user_balance_non_negative");
    }

    @Test
    void databaseAllowsANegativeSystemBalance() {
        insertAccount("SYSTEM", -50_000);

        assertThat(jdbc.sql("SELECT count(*) FROM accounts WHERE type = 'SYSTEM'").query(Long.class).single())
                .isEqualTo(1);
    }

    private void insertAccount(String type, long balanceMinor) {
        jdbc.sql("""
                INSERT INTO accounts (id, owner_id, currency, type, status, balance_minor, created_at)
                VALUES (?, 'system', 'BDT', ?, 'ACTIVE', ?, now())
                """)
                .params(UUID.randomUUID(), type, balanceMinor)
                .update();
    }
}
