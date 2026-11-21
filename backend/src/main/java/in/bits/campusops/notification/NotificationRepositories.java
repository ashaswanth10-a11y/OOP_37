package in.bits.campusops.notification;

import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public final class NotificationRepositories {

    private NotificationRepositories() {
    }

    public interface NotificationRepository extends JpaRepository<Notification, Long> {
        List<Notification> findByRecipientIdOrderByCreatedAtDesc(Long recipientId, Pageable pageable);

        long countByRecipientIdAndReadFlagFalse(Long recipientId);

        List<Notification> findByRecipientIdAndReadFlagFalse(Long recipientId);
    }

    public interface EmailOutboxRepository extends JpaRepository<EmailOutboxMessage, Long> {
        List<EmailOutboxMessage> findAllByOrderBySentAtDesc(Pageable pageable);
    }
}
