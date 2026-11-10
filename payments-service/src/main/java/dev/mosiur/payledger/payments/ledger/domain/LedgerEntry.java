package dev.mosiur.payledger.payments.ledger.domain;

import dev.mosiur.payledger.payments.account.domain.Account;
import dev.mosiur.payledger.payments.account.domain.Currency;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.UuidGenerator;

@Entity
@Immutable
@Table(name = "ledger_entries")
public class LedgerEntry {

    @Id
    @GeneratedValue
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "transaction_id", nullable = false)
    private LedgerTransaction transaction;

    @Column(name = "account_id", nullable = false)
    private UUID accountId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 6)
    private EntryDirection direction;

    @Column(name = "amount_minor", nullable = false)
    private long amountMinor;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 3)
    private Currency currency;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected LedgerEntry() {
    }

    private LedgerEntry(
            LedgerTransaction transaction, Account account, EntryDirection direction, long amountMinor) {
        this.transaction = transaction;
        this.accountId = account.getId();
        this.direction = direction;
        this.amountMinor = amountMinor;
        this.currency = account.getCurrency();
        this.createdAt = transaction.getCreatedAt();
    }

    public static LedgerEntry debit(LedgerTransaction transaction, Account account, long amountMinor) {
        return new LedgerEntry(transaction, account, EntryDirection.DEBIT, amountMinor);
    }

    public static LedgerEntry credit(LedgerTransaction transaction, Account account, long amountMinor) {
        return new LedgerEntry(transaction, account, EntryDirection.CREDIT, amountMinor);
    }

    public UUID getId() {
        return id;
    }

    public LedgerTransaction getTransaction() {
        return transaction;
    }

    public UUID getAccountId() {
        return accountId;
    }

    public EntryDirection getDirection() {
        return direction;
    }

    public long getAmountMinor() {
        return amountMinor;
    }

    public Currency getCurrency() {
        return currency;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
