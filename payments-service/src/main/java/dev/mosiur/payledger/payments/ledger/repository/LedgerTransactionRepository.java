package dev.mosiur.payledger.payments.ledger.repository;

import dev.mosiur.payledger.payments.ledger.domain.LedgerTransaction;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LedgerTransactionRepository extends JpaRepository<LedgerTransaction, UUID> {
}
