package dev.mosiur.payledger.payments.transfer;

import static org.assertj.core.api.Assertions.assertThat;

import dev.mosiur.payledger.payments.PostgresTestConfiguration;
import dev.mosiur.payledger.payments.account.domain.Currency;
import dev.mosiur.payledger.payments.account.domain.InsufficientFundsException;
import dev.mosiur.payledger.payments.account.model.OpenAccountRequest;
import dev.mosiur.payledger.payments.account.service.AccountService;
import dev.mosiur.payledger.payments.ledger.model.DepositRequest;
import dev.mosiur.payledger.payments.ledger.service.DepositService;
import dev.mosiur.payledger.payments.support.LedgerAssertions;
import dev.mosiur.payledger.payments.transfer.model.TransferRequest;
import dev.mosiur.payledger.payments.transfer.service.TransferService;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
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
class ConcurrentTransfersIT {

    private static final int ACCOUNTS = 6;
    private static final int TRANSFERS = 200;
    private static final long OPENING_BALANCE = 100_000;

    @Autowired
    private AccountService accountService;

    @Autowired
    private DepositService depositService;

    @Autowired
    private TransferService transferService;

    @Autowired
    private JdbcClient jdbc;

    private record Holder(String owner, UUID accountId) {
    }

    @Test
    void randomTransfersInBothDirectionsNeverDeadlockOrLoseMoney() throws Exception {
        List<Holder> holders = openFundedAccounts();

        List<Callable<Boolean>> transfers = new ArrayList<>();
        for (int i = 0; i < TRANSFERS; i++) {
            transfers.add(randomTransfer(holders));
        }

        int completed = 0;
        int rejectedForFunds = 0;
        try (ExecutorService pool = Executors.newFixedThreadPool(20)) {
            for (Future<Boolean> result : pool.invokeAll(transfers)) {
                if (get(result)) {
                    completed++;
                } else {
                    rejectedForFunds++;
                }
            }
        }

        assertThat(completed + rejectedForFunds).isEqualTo(TRANSFERS);
        assertThat(completed).isPositive();
        long total = jdbc.sql("SELECT sum(balance_minor) FROM accounts WHERE owner_id LIKE 'race-%'")
                .query(Long.class).single();
        assertThat(total).as("money only moves between these accounts").isEqualTo(ACCOUNTS * OPENING_BALANCE);
        LedgerAssertions.assertLedgerIsConsistent(jdbc);
    }

    private List<Holder> openFundedAccounts() {
        List<Holder> holders = new ArrayList<>();
        for (int i = 0; i < ACCOUNTS; i++) {
            String owner = "race-" + i + "-" + UUID.randomUUID();
            UUID id = accountService.open(owner, new OpenAccountRequest(Currency.BDT)).id();
            depositService.deposit("admin", new DepositRequest(id, OPENING_BALANCE, null));
            holders.add(new Holder(owner, id));
        }
        return holders;
    }

    /** Returns true when the transfer went through, false when it was refused for lack of funds. */
    private Callable<Boolean> randomTransfer(List<Holder> holders) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        Holder from = holders.get(random.nextInt(ACCOUNTS));
        Holder to = holders.get((holders.indexOf(from) + 1 + random.nextInt(ACCOUNTS - 1)) % ACCOUNTS);
        long amount = random.nextLong(1, 40_000);
        return () -> {
            try {
                transferService.transfer(from.owner(), new TransferRequest(from.accountId(), to.accountId(), amount, null));
                return true;
            } catch (InsufficientFundsException e) {
                return false;
            }
        };
    }

    private static boolean get(Future<Boolean> result) throws InterruptedException {
        try {
            return result.get();
        } catch (ExecutionException e) {
            throw new AssertionError("Transfer failed unexpectedly: " + e.getCause(), e.getCause());
        }
    }
}
