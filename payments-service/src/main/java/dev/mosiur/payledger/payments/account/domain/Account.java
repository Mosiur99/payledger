package dev.mosiur.payledger.payments.account.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.UuidGenerator;

@Entity
@Table(name = "accounts")
public class Account {

    @Id
    @GeneratedValue
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID id;

    @Column(name = "owner_id", nullable = false, updatable = false, length = 64)
    private String ownerId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 3)
    private Currency currency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 16)
    private AccountType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private AccountStatus status;

    @Column(name = "balance_minor", nullable = false)
    private long balanceMinor;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Account() {
    }

    private Account(String ownerId, Currency currency, AccountType type, Instant createdAt) {
        this.ownerId = ownerId;
        this.currency = currency;
        this.type = type;
        this.status = AccountStatus.ACTIVE;
        this.balanceMinor = 0;
        this.createdAt = createdAt;
    }

    public static Account openUserAccount(String ownerId, Currency currency, Instant createdAt) {
        return new Account(ownerId, currency, AccountType.USER, createdAt);
    }

    public void debit(long amountMinor) {
        balanceMinor = Math.subtractExact(balanceMinor, requirePositive(amountMinor));
    }

    public void credit(long amountMinor) {
        balanceMinor = Math.addExact(balanceMinor, requirePositive(amountMinor));
    }

    public boolean isActive() {
        return status == AccountStatus.ACTIVE;
    }

    private static long requirePositive(long amountMinor) {
        if (amountMinor <= 0) {
            throw new IllegalArgumentException("Amount must be positive: " + amountMinor);
        }
        return amountMinor;
    }

    public UUID getId() {
        return id;
    }

    public String getOwnerId() {
        return ownerId;
    }

    public Currency getCurrency() {
        return currency;
    }

    public AccountType getType() {
        return type;
    }

    public AccountStatus getStatus() {
        return status;
    }

    public long getBalanceMinor() {
        return balanceMinor;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
