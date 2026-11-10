package dev.mosiur.payledger.payments.account.repository;

import dev.mosiur.payledger.payments.account.domain.Account;
import dev.mosiur.payledger.payments.account.domain.AccountType;
import dev.mosiur.payledger.payments.account.domain.Currency;
import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface AccountRepository extends JpaRepository<Account, UUID> {

    List<Account> findByOwnerIdOrderByCreatedAtAsc(String ownerId);

    Optional<Account> findByIdAndOwnerId(UUID id, String ownerId);

    @Query("select a.currency from Account a where a.id = :id and a.type = :type")
    Optional<Currency> findCurrency(UUID id, AccountType type);

    @Query("select a.id from Account a where a.type = :type and a.currency = :currency")
    Optional<UUID> findIdByTypeAndCurrency(AccountType type, Currency currency);

    /**
     * Locks rows in one global order (by id) so two transactions touching the same
     * accounts can never wait on each other in a cycle.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Account a where a.id in :ids order by a.id")
    List<Account> lockAllInIdOrder(Collection<UUID> ids);
}
