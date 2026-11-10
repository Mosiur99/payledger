package dev.mosiur.payledger.payments.ledger;

import static org.assertj.core.api.Assertions.assertThat;

import dev.mosiur.payledger.payments.PostgresTestConfiguration;
import dev.mosiur.payledger.payments.account.domain.Currency;
import dev.mosiur.payledger.payments.account.model.AccountResponse;
import dev.mosiur.payledger.payments.account.model.OpenAccountRequest;
import dev.mosiur.payledger.payments.account.service.AccountService;
import dev.mosiur.payledger.payments.ledger.model.DepositRequest;
import dev.mosiur.payledger.payments.ledger.service.DepositService;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadLocalRandom;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;

@SpringBootTest
@Import(PostgresTestConfiguration.class)
class LedgerInvariantsIT {

    private static final int ACCOUNTS = 4;
    private static final int DEPOSITS = 200;

    @Autowired
    private AccountService accountService;

    @Autowired
    private DepositService depositService;

    @Autowired
    private JdbcClient jdbc;

    @Test
    void concurrentDepositsKeepTheLedgerBalanced() throws Exception {
        List<AccountResponse> accounts = new ArrayList<>();
        for (int i = 0; i < ACCOUNTS; i++) {
            accounts.add(accountService.open("invariant-" + i, new OpenAccountRequest(Currency.BDT)));
        }

        List<Callable<Long>> deposits = new ArrayList<>();
        for (int i = 0; i < DEPOSITS; i++) {
            AccountResponse target = accounts.get(i % ACCOUNTS);
            long amount = ThreadLocalRandom.current().nextLong(1, 100_000);
            deposits.add(() -> {
                depositService.deposit("admin", new DepositRequest(target.id(), amount, null));
                return amount;
            });
        }

        long deposited = 0;
        try (ExecutorService pool = Executors.newFixedThreadPool(16)) {
            for (Future<Long> result : pool.invokeAll(deposits)) {
                deposited += result.get();
            }
        }

        long credited = jdbc.sql("SELECT coalesce(sum(balance_minor), 0) FROM accounts WHERE owner_id LIKE 'invariant-%'")
                .query(Long.class).single();
        assertThat(credited).isEqualTo(deposited);
        assertEveryTransactionBalances();
        assertBalancesSumToZeroPerCurrency();
        assertEveryBalanceMatchesItsEntries();
    }

    private void assertEveryTransactionBalances() {
        long unbalanced = jdbc.sql("""
                SELECT count(*) FROM (
                    SELECT transaction_id
                    FROM ledger_entries
                    GROUP BY transaction_id
                    HAVING sum(CASE direction WHEN 'DEBIT' THEN amount_minor ELSE 0 END)
                        <> sum(CASE direction WHEN 'CREDIT' THEN amount_minor ELSE 0 END)
                        OR count(*) <> 2
                ) bad
                """).query(Long.class).single();
        assertThat(unbalanced).isZero();
    }

    private void assertBalancesSumToZeroPerCurrency() {
        List<Long> totals = jdbc.sql("SELECT sum(balance_minor) FROM accounts GROUP BY currency")
                .query(Long.class).list();
        assertThat(totals).containsOnly(0L);
    }

    private void assertEveryBalanceMatchesItsEntries() {
        long mismatched = jdbc.sql("""
                SELECT count(*) FROM accounts a
                WHERE a.balance_minor <> (
                    SELECT coalesce(sum(CASE e.direction WHEN 'CREDIT' THEN e.amount_minor ELSE -e.amount_minor END), 0)
                    FROM ledger_entries e
                    WHERE e.account_id = a.id
                )
                """).query(Long.class).single();
        assertThat(mismatched).isZero();
    }
}
