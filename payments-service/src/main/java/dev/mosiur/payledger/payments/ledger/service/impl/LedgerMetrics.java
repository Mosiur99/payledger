package dev.mosiur.payledger.payments.ledger.service.impl;

import dev.mosiur.payledger.payments.account.domain.Currency;
import dev.mosiur.payledger.payments.ledger.domain.TransactionType;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Business metrics for the ledger. Counted after commit, so rolled-back work never shows up. */
@Component
public class LedgerMetrics {

    static final String TRANSACTIONS = "payledger.ledger.transactions";
    static final String VOLUME = "payledger.ledger.volume";

    private final MeterRegistry registry;

    public LedgerMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    public void recordPosted(TransactionType type, Currency currency, long amountMinor) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            count(type, currency, amountMinor);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                count(type, currency, amountMinor);
            }
        });
    }

    private void count(TransactionType type, Currency currency, long amountMinor) {
        Counter.builder(TRANSACTIONS)
                .description("Committed ledger transactions")
                .tag("type", type.name())
                .tag("currency", currency.name())
                .register(registry)
                .increment();
        Counter.builder(VOLUME)
                .description("Money moved, in the currency's smallest unit")
                .baseUnit("minor")
                .tag("type", type.name())
                .tag("currency", currency.name())
                .register(registry)
                .increment(amountMinor);
    }
}
