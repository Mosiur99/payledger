package dev.mosiur.payledger.payments.account.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;

class AccountTest {

    private final Account account = Account.openUserAccount("ayesha", Currency.BDT, Instant.EPOCH);

    @Test
    void creditAndDebitMoveTheBalance() {
        account.credit(50_000);
        account.debit(20_000);

        assertThat(account.getBalanceMinor()).isEqualTo(30_000);
    }

    @Test
    void systemAccountMayGoNegative() {
        Account system = Account.openUserAccount("system", Currency.BDT, Instant.EPOCH);
        ReflectionTestUtils.setField(system, "type", AccountType.SYSTEM);

        system.debit(50_000);

        assertThat(system.getBalanceMinor()).isEqualTo(-50_000);
    }

    @ParameterizedTest
    @ValueSource(longs = {0, -1})
    void rejectsNonPositiveAmounts(long amount) {
        assertThatThrownBy(() -> account.credit(amount)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> account.debit(amount)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void failsInsteadOfOverflowing() {
        account.credit(Long.MAX_VALUE);

        assertThatThrownBy(() -> account.credit(1)).isInstanceOf(ArithmeticException.class);
    }

    @Test
    void userAccountCannotSpendMoreThanItsBalance() {
        account.credit(1_000);

        assertThatThrownBy(() -> account.debit(1_001)).isInstanceOf(InsufficientFundsException.class);
        assertThat(account.getBalanceMinor()).isEqualTo(1_000);
    }

    @Test
    void userAccountCanSpendItsWholeBalance() {
        account.credit(1_000);
        account.debit(1_000);

        assertThat(account.getBalanceMinor()).isZero();
    }

    @Test
    void newAccountIsActive() {
        assertThat(account.isActive()).isTrue();
    }
}
