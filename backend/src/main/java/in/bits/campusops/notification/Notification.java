package in.bits.campusops.notification;

import in.bits.campusops.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;

/** An in-app notification shown in the bell menu. */
@Entity
@Table(indexes = @Index(columnList = "recipient_id"))
public class Notification {

    public enum Severity { INFO, SUCCESS, WARNING, CRITICAL }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private User recipient;

    private String title;

    @Column(length = 1000)
    private String body;

    private Long incidentId;
    private String incidentNumber;

    @Enumerated(EnumType.STRING)
    private Severity severity;

    private boolean readFlag;
    private Instant createdAt;

    protected Notification() {
    }

    public Notification(User recipient, String title, String body, Long incidentId, String incidentNumber,
                        Severity severity, Instant createdAt) {
        this.recipient = recipient;
        this.title = title;
        this.body = body;
        this.incidentId = incidentId;
        this.incidentNumber = incidentNumber;
        this.severity = severity;
        this.createdAt = createdAt;
    }

    public void markRead() {
        this.readFlag = true;
    }

    public Long getId() {
        return id;
    }

    public User getRecipient() {
        return recipient;
    }

    public String getTitle() {
        return title;
    }

    public String getBody() {
        return body;
    }

    public Long getIncidentId() {
        return incidentId;
    }

    public String getIncidentNumber() {
        return incidentNumber;
    }

    public Severity getSeverity() {
        return severity;
    }

    public boolean isRead() {
        return readFlag;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
