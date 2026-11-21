package in.bits.campusops.sla;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import java.time.Instant;

/** A recorded SLA breach, kept even after the incident is closed. */
@Entity
public class SlaViolation {

    public enum Kind { RESPONSE, RESOLUTION }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long incidentId;
    private String incidentNumber;
    private String kind;
    private String priority;
    private String serviceName;
    private String assigneeName;
    private Instant dueAt;
    private Instant detectedAt;

    protected SlaViolation() {
    }

    public SlaViolation(Long incidentId, String incidentNumber, Kind kind, String priority, String serviceName,
                        String assigneeName, Instant dueAt, Instant detectedAt) {
        this.incidentId = incidentId;
        this.incidentNumber = incidentNumber;
        this.kind = kind.name();
        this.priority = priority;
        this.serviceName = serviceName;
        this.assigneeName = assigneeName;
        this.dueAt = dueAt;
        this.detectedAt = detectedAt;
    }

    public Long getId() {
        return id;
    }

    public Long getIncidentId() {
        return incidentId;
    }

    public String getIncidentNumber() {
        return incidentNumber;
    }

    public String getKind() {
        return kind;
    }

    public String getPriority() {
        return priority;
    }

    public String getServiceName() {
        return serviceName;
    }

    public String getAssigneeName() {
        return assigneeName;
    }

    public Instant getDueAt() {
        return dueAt;
    }

    public Instant getDetectedAt() {
        return detectedAt;
    }
}
