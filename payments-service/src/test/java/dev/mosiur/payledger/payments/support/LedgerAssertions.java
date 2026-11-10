package dev.mosiur.payledger.payments.support;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.springframework.jdbc.core.simple.JdbcClient;

/** Checks the double-entry rules directly in the database. */
public final class LedgerAssertions {

    private LedgerAssertions() {
    }

    public static void assertLedgerIsConsistent(JdbcClient jdbc) {
        assertEveryTransactionBalances(jdbc);
        assertBalancesSumToZeroPerCurrency(jdbc);
        assertEveryBalanceMatchesItsEntries(jdbc);
        assertNoUserAccountIsNegative(jdbc);
    }

    private static void assertEveryTransactionBalances(JdbcClient jdbc) {
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
        assertThat(unbalanced).as("transactions whose debits and credits differ").isZero();
    }

    private static void assertBalancesSumToZeroPerCurrency(JdbcClient jdbc) {
        List<Long> totals = jdbc.sql("SELECT sum(balance_minor) FROM accounts GROUP BY currency")
                .query(Long.class).list();
        assertThat(totals).as("sum of balances per currency").containsOnly(0L);
    }

    private static void assertEveryBalanceMatchesItsEntries(JdbcClient jdbc) {
        long mismatched = jdbc.sql("""
                SELECT count(*) FROM accounts a
                WHERE a.balance_minor <> (
                    SELECT coalesce(sum(CASE e.direction WHEN 'CREDIT' THEN e.amount_minor ELSE -e.amount_minor END), 0)
                    FROM ledger_entries e
                    WHERE e.account_id = a.id
                )
                """).query(Long.class).single();
        assertThat(mismatched).as("accounts whose balance differs from their entries").isZero();
    }

    private static void assertNoUserAccountIsNegative(JdbcClient jdbc) {
        long negative = jdbc.sql("SELECT count(*) FROM accounts WHERE type = 'USER' AND balance_minor < 0")
                .query(Long.class).single();
        assertThat(negative).as("user accounts below zero").isZero();
    }
}
