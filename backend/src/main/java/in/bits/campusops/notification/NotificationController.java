package in.bits.campusops.notification;

import in.bits.campusops.common.Exceptions.ResourceNotFoundException;
import in.bits.campusops.common.Exceptions.UnauthorizedOperationException;
import in.bits.campusops.notification.NotificationRepositories.EmailOutboxRepository;
import in.bits.campusops.notification.NotificationRepositories.NotificationRepository;
import in.bits.campusops.user.CurrentUser;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private final NotificationRepository notifications;
    private final EmailOutboxRepository outbox;
    private final CurrentUser currentUser;

    public NotificationController(NotificationRepository notifications, EmailOutboxRepository outbox,
                                  CurrentUser currentUser) {
        this.notifications = notifications;
        this.outbox = outbox;
        this.currentUser = currentUser;
    }

    public record NotificationView(Long id, String title, String body, Long incidentId, String incidentNumber,
                                   String severity, boolean read, Instant createdAt) {
        static NotificationView of(Notification n) {
            return new NotificationView(n.getId(), n.getTitle(), n.getBody(), n.getIncidentId(),
                    n.getIncidentNumber(), n.getSeverity().name(), n.isRead(), n.getCreatedAt());
        }
    }

    public record OutboxView(Long id, String to, String subject, String body, Instant sentAt) {
    }

    @GetMapping
    @Transactional(readOnly = true)
    public Map<String, Object> mine() {
        Long me = currentUser.id();
        List<NotificationView> items = notifications
                .findByRecipientIdOrderByCreatedAtDesc(me, PageRequest.of(0, 40)).stream()
                .map(NotificationView::of).toList();
        return Map.of("items", items, "unread", notifications.countByRecipientIdAndReadFlagFalse(me));
    }

    @PostMapping("/{id}/read")
    @Transactional
    public void read(@PathVariable Long id) {
        Notification n = notifications.findById(id).orElseThrow(() -> new ResourceNotFoundException("Notification", id));
        if (!n.getRecipient().getId().equals(currentUser.id())) {
            throw new UnauthorizedOperationException("That notification is not yours");
        }
        n.markRead();
    }

    @PostMapping("/read-all")
    @Transactional
    public void readAll() {
        notifications.findByRecipientIdAndReadFlagFalse(currentUser.id()).forEach(Notification::markRead);
    }

    @GetMapping("/outbox")
    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    @Transactional(readOnly = true)
    public List<OutboxView> outbox() {
        return outbox.findAllByOrderBySentAtDesc(PageRequest.of(0, 100)).stream()
                .map(m -> new OutboxView(m.getId(), m.getToAddress(), m.getSubject(), m.getBody(), m.getSentAt()))
                .toList();
    }
}
