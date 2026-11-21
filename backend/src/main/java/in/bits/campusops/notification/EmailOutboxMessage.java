package in.bits.campusops.notification;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import java.time.Instant;

/** A simulated email: stored instead of sent, and visible to admins in the outbox screen. */
@Entity
public class EmailOutboxMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String toAddress;
    private String subject;

    @Column(length = 4000)
    private String body;

    private Instant sentAt;

    protected EmailOutboxMessage() {
    }

    public EmailOutboxMessage(String toAddress, String subject, String body, Instant sentAt) {
        this.toAddress = toAddress;
        this.subject = subject;
        this.body = body;
        this.sentAt = sentAt;
    }

    public Long getId() {
        return id;
    }

    public String getToAddress() {
        return toAddress;
    }

    public String getSubject() {
        return subject;
    }

    public String getBody() {
        return body;
    }

    public Instant getSentAt() {
        return sentAt;
    }
}
