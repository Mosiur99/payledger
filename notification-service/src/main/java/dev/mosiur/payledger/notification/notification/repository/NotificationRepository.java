package dev.mosiur.payledger.notification.notification.repository;

import dev.mosiur.payledger.notification.notification.domain.Notification;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationRepository extends JpaRepository<Notification, UUID> {

    List<Notification> findByTransactionId(UUID transactionId);
}
