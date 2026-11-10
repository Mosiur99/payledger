package dev.mosiur.payledger.payments.account.repository;

import dev.mosiur.payledger.payments.account.domain.Account;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AccountRepository extends JpaRepository<Account, UUID> {

    List<Account> findByOwnerIdOrderByCreatedAtAsc(String ownerId);

    Optional<Account> findByIdAndOwnerId(UUID id, String ownerId);
}
