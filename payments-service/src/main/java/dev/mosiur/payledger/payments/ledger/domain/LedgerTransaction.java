package dev.mosiur.payledger.payments.ledger.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.UuidGenerator;

@Entity
@Immutable
@Table(name = "transactions")
public class LedgerTransaction {

    @Id
    @GeneratedValue
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private TransactionType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private TransactionStatus status;

    @Column(length = 140)
    private String reference;

    @Column(name = "initiated_by", nullable = false, length = 64)
    private String initiatedBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected LedgerTransaction() {
    }

    private LedgerTransaction(TransactionType type, String reference, String initiatedBy, Instant createdAt) {
        this.type = type;
        this.status = TransactionStatus.COMPLETED;
        this.reference = reference;
        this.initiatedBy = initiatedBy;
        this.createdAt = createdAt;
    }

    public static LedgerTransaction completed(
            TransactionType type, String reference, String initiatedBy, Instant createdAt) {
        return new LedgerTransaction(type, reference, initiatedBy, createdAt);
    }

    public UUID getId() {
        return id;
    }

    public TransactionType getType() {
        return type;
    }

    public TransactionStatus getStatus() {
        return status;
    }

    public String getReference() {
        return reference;
    }

    public String getInitiatedBy() {
        return initiatedBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
