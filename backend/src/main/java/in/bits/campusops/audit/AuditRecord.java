package in.bits.campusops.audit;

import in.bits.campusops.events.IncidentEventType;
import in.bits.campusops.incident.IncidentStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * Append-only history entry. Besides the required who/what/when fields it stores a
 * snapshot of the incident after the event, which is what powers the replay slider.
 */
@Entity
@Table(indexes = {@Index(columnList = "incidentId"), @Index(columnList = "at")})
public class AuditRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Instant at;
    private Long actorId;
    private String actorName;
    private String actorCode;

    @Enumerated(EnumType.STRING)
    private IncidentEventType action;

    private Long incidentId;
    private String incidentNumber;

    @Enumerated(EnumType.STRING)
    private IncidentStatus previousState;

    @Enumerated(EnumType.STRING)
    private IncidentStatus newState;

    @Column(length = 2000)
    private String description;

    // snapshot after the event
    private String snapPriority;
    private String snapAssignee;
    private String snapCategory;
    private String snapSlaState;
    private int snapEscalationLevel;
    private Double snapSlaUsed;

    protected AuditRecord() {
    }

    public AuditRecord(Instant at, Long actorId, String actorName, String actorCode, IncidentEventType action,
                       Long incidentId, String incidentNumber, IncidentStatus previousState, IncidentStatus newState,
                       String description, String snapPriority, String snapAssignee, String snapCategory,
                       String snapSlaState, int snapEscalationLevel, Double snapSlaUsed) {
        this.at = at;
        this.actorId = actorId;
        this.actorName = actorName;
        this.actorCode = actorCode;
        this.action = action;
        this.incidentId = incidentId;
        this.incidentNumber = incidentNumber;
        this.previousState = previousState;
        this.newState = newState;
        this.description = description;
        this.snapPriority = snapPriority;
        this.snapAssignee = snapAssignee;
        this.snapCategory = snapCategory;
        this.snapSlaState = snapSlaState;
        this.snapEscalationLevel = snapEscalationLevel;
        this.snapSlaUsed = snapSlaUsed;
    }

    public Long getId() {
        return id;
    }

    public Instant getAt() {
        return at;
    }

    public Long getActorId() {
        return actorId;
    }

    public String getActorName() {
        return actorName;
    }

    public String getActorCode() {
        return actorCode;
    }

    public IncidentEventType getAction() {
        return action;
    }

    public Long getIncidentId() {
        return incidentId;
    }

    public String getIncidentNumber() {
        return incidentNumber;
    }

    public IncidentStatus getPreviousState() {
        return previousState;
    }

    public IncidentStatus getNewState() {
        return newState;
    }

    public String getDescription() {
        return description;
    }

    public String getSnapPriority() {
        return snapPriority;
    }

    public String getSnapAssignee() {
        return snapAssignee;
    }

    public String getSnapCategory() {
        return snapCategory;
    }

    public String getSnapSlaState() {
        return snapSlaState;
    }

    public int getSnapEscalationLevel() {
        return snapEscalationLevel;
    }

    public Double getSnapSlaUsed() {
        return snapSlaUsed;
    }
}
