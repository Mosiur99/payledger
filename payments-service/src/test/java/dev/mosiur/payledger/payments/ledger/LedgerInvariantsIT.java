package dev.mosiur.payledger.payments.ledger;

import static org.assertj.core.api.Assertions.assertThat;

import dev.mosiur.payledger.payments.PostgresTestConfiguration;
import dev.mosiur.payledger.payments.account.domain.Currency;
import dev.mosiur.payledger.payments.account.model.AccountResponse;
import dev.mosiur.payledger.payments.account.model.OpenAccountRequest;
import dev.mosiur.payledger.payments.account.service.AccountService;
import dev.mosiur.payledger.payments.ledger.model.DepositRequest;
import dev.mosiur.payledger.payments.ledger.service.DepositService;
import dev.mosiur.payledger.payments.support.LedgerAssertions;
import dev.mosiur.payledger.payments.support.TestJwtConfiguration;
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
@Import({PostgresTestConfiguration.class, TestJwtConfiguration.class})
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
        LedgerAssertions.assertLedgerIsConsistent(jdbc);
    }
}
