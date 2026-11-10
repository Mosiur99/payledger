package dev.mosiur.payledger.payments.ledger.repository;

import dev.mosiur.payledger.payments.ledger.domain.LedgerEntry;
import dev.mosiur.payledger.payments.ledger.model.StatementEntry;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface LedgerEntryRepository extends JpaRepository<LedgerEntry, UUID> {

    String STATEMENT_SELECT = """
            select new dev.mosiur.payledger.payments.ledger.model.StatementEntry(
                e.id, t.id, t.type, t.reference, e.direction, e.amountMinor, e.createdAt)
            from LedgerEntry e join e.transaction t
            """;

    @Query(STATEMENT_SELECT + "where e.accountId = :accountId order by e.id desc")
    List<StatementEntry> findNewestFirst(UUID accountId, Limit limit);

    @Query(STATEMENT_SELECT + "where e.accountId = :accountId and e.id < :before order by e.id desc")
    List<StatementEntry> findNewestFirstBefore(UUID accountId, UUID before, Limit limit);
}
