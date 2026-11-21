package in.bits.campusops.incident;

import in.bits.campusops.catalog.Asset;
import in.bits.campusops.catalog.IncidentCategory;
import in.bits.campusops.catalog.ItService;
import in.bits.campusops.catalog.Level;
import in.bits.campusops.catalog.Priority;
import in.bits.campusops.catalog.SlaPolicy;
import in.bits.campusops.common.Exceptions.InvalidStateTransitionException;
import in.bits.campusops.problem.ProblemRecord;
import in.bits.campusops.user.Technician;
import in.bits.campusops.user.User;
import jakarta.persistence.CascadeType;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.EnumeratedValue;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The aggregate root of the system. State only changes through intention-revealing
 * methods (classify, assignTo, transitionTo...), never through setters, so every
 * rule of the lifecycle is enforced in one place.
 */
@Entity
@Table(indexes = {
        @Index(columnList = "status"),
        @Index(columnList = "createdAt"),
        @Index(columnList = "priority")
})
public class Incident {

    public enum Channel {
        WEB("WEB"), QR("QR"), VOICE("VOICE"), LEGACY("TELEGRAM"), SIMULATION("SIMULATION");

        // The old source stays readable for reports already in the database.
        @EnumeratedValue
        private final String storedValue;

        Channel(String storedValue) {
            this.storedValue = storedValue;
        }
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 16)
    private String number;

    private int sequenceNo;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    private User reporter;

    @Column(nullable = false, length = 160)
    private String title;

    @Column(nullable = false, length = 4000)
    private String description;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    private ItService service;

    @ManyToOne(fetch = FetchType.EAGER)
    private Asset asset;

    private String location;

    @ManyToOne(fetch = FetchType.EAGER)
    private IncidentCategory category;

    /** USER, KEYWORD or AI: who picked the category. */
    private String classifiedBy;
    private Double classificationConfidence;

    @Enumerated(EnumType.STRING)
    private Level impact;

    @Enumerated(EnumType.STRING)
    private Level urgency;

    private int affectedUsers;
    private double priorityScore;

    @Enumerated(EnumType.STRING)
    private Priority priority;

    @Column(length = 500)
    private String priorityRationale;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private IncidentStatus status;

    @Enumerated(EnumType.STRING)
    private Channel channel;

    @ManyToOne(fetch = FetchType.EAGER)
    private Technician assignee;

    private Instant assignedAt;
    private String assignmentStrategy;

    /** 0 = technician, 1 = team lead, 2 = system admin, 3 = IT manager. */
    private int escalationLevel;

    @ManyToOne(fetch = FetchType.EAGER)
    private User escalatedTo;

    @Embedded
    private SlaTracker sla = new SlaTracker();

    @Embedded
    private Resolution resolution;

    @Embedded
    private Feedback feedback;

    @ManyToOne(fetch = FetchType.LAZY)
    private Incident possibleDuplicateOf;

    private Double duplicateScore;

    @ManyToOne(fetch = FetchType.LAZY)
    private ProblemRecord problem;

    @OneToMany(mappedBy = "incident", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("createdAt ASC")
    private List<WorkNote> notes = new ArrayList<>();

    /** People who pressed "Me too": they have the same problem. Drives the affected-user count. */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "incident_supporters", joinColumns = @JoinColumn(name = "incident_id"))
    @Column(name = "user_id")
    private Set<Long> supporterIds = new HashSet<>();

    private Instant createdAt;
    private Instant updatedAt;
    private Instant closedAt;

    @Version
    private long version;

    protected Incident() {
    }

    Incident(int sequenceNo, User reporter, String title, String description, ItService service, Asset asset,
             String location, Level impact, Level urgency, int affectedUsers, Channel channel, Instant now) {
        this.sequenceNo = sequenceNo;
        this.number = "INC-" + sequenceNo;
        this.reporter = reporter;
        this.title = title.trim();
        this.description = description.trim();
        this.service = service;
        this.asset = asset;
        this.location = location;
        this.impact = impact;
        this.urgency = urgency;
        this.affectedUsers = Math.max(1, affectedUsers);
        this.channel = channel;
        this.status = IncidentStatus.NEW;
        this.createdAt = now;
        this.updatedAt = now;
    }

    // ---- lifecycle -----------------------------------------------------------

    public IncidentState state() {
        return IncidentStates.of(status);
    }

    /** The only way the status changes. Illegal moves (e.g. NEW to CLOSED) throw. */
    public void transitionTo(IncidentStatus target, Instant now) {
        IncidentState current = state();
        if (!current.canTransitionTo(target)) {
            String allowed = current.allowedNext().isEmpty() ? "none"
                    : current.allowedNext().stream().map(Enum::name).collect(Collectors.joining(", "));
            throw new InvalidStateTransitionException(
                    number + " cannot move from " + status + " to " + target + ". Allowed next: " + allowed);
        }
        IncidentState next = IncidentStates.of(target);
        next.validateEntry(this);
        this.status = target;
        this.updatedAt = now;
        next.onEnter(this, now);
    }

    public void classify(IncidentCategory category, String classifiedBy, Double confidence, Instant now) {
        this.category = category;
        this.classifiedBy = classifiedBy;
        this.classificationConfidence = confidence;
        if (status == IncidentStatus.NEW) {
            transitionTo(IncidentStatus.CLASSIFIED, now);
        }
        this.updatedAt = now;
    }

    public void applyPriority(Priority priority, double score, String rationale, SlaPolicy policy, Instant now) {
        this.priority = priority;
        this.priorityScore = score;
        this.priorityRationale = rationale;
        if (policy != null) {
            Instant start = sla.isStarted() ? sla.getSlaStart() : createdAt;
            sla.start(policy, start);
        }
        this.updatedAt = now;
    }

    public void reassess(Level impact, Level urgency, int affectedUsers) {
        this.impact = impact;
        this.urgency = urgency;
        this.affectedUsers = Math.max(1, affectedUsers);
    }

    public void assignTo(Technician technician, String strategy, Instant now) {
        if (!state().allowsAssignment()) {
            throw new InvalidStateTransitionException(number + " is " + status + " and cannot be assigned");
        }
        this.assignee = technician;
        this.assignedAt = now;
        this.assignmentStrategy = strategy;
        if (status == IncidentStatus.CLASSIFIED || status == IncidentStatus.REOPENED) {
            transitionTo(IncidentStatus.ASSIGNED, now);
        }
        this.updatedAt = now;
    }

    public void resolve(Resolution resolution, Instant now) {
        this.resolution = resolution;
        transitionTo(IncidentStatus.RESOLVED, now);
    }

    public void verify(Feedback feedback, Instant now) {
        this.feedback = feedback;
        transitionTo(IncidentStatus.VERIFIED, now);
    }

    public void escalate(int level, User to, Instant now) {
        if (level <= escalationLevel) {
            return;
        }
        this.escalationLevel = level;
        this.escalatedTo = to;
        this.updatedAt = now;
    }

    public WorkNote addNote(User author, WorkNote.Type type, String text, boolean internal, Instant now) {
        WorkNote note = new WorkNote(this, author, type, text.trim(), internal, now);
        notes.add(note);
        this.updatedAt = now;
        return note;
    }

    public void flagPossibleDuplicate(Incident original, double score) {
        this.possibleDuplicateOf = original;
        this.duplicateScore = score;
    }

    /**
     * Records that another person has the same problem. Returns false if they already said so.
     * The affected-user count never drops below reporter + supporters.
     */
    public boolean addSupporter(User user, Instant now) {
        if (isReportedBy(user)) {
            throw new InvalidStateTransitionException("You reported " + number + " yourself");
        }
        if (!isOpen()) {
            throw new InvalidStateTransitionException(number + " is no longer open");
        }
        boolean added = supporterIds.add(user.getId());
        if (added) {
            this.affectedUsers = Math.max(affectedUsers, supporterIds.size() + 1);
            this.updatedAt = now;
        }
        return added;
    }

    public int getSupporterCount() {
        return supporterIds.size();
    }

    /** Ids of the people who said "Me too" (read-only view). */
    public Set<Long> getSupporterIds() {
        return java.util.Collections.unmodifiableSet(supporterIds);
    }

    public boolean isSupportedBy(User user) {
        return supporterIds.contains(user.getId());
    }

    public void linkProblem(ProblemRecord problem) {
        this.problem = problem;
    }

    // hooks used by the state objects
    SlaTracker sla() {
        return sla;
    }

    void recordClosure(Instant now) {
        this.closedAt = now;
    }

    // ---- queries -------------------------------------------------------------

    public boolean isOpen() {
        return status.isOpen();
    }

    public boolean isReportedBy(User user) {
        return reporter.getId().equals(user.getId());
    }

    public boolean isAssignedTo(User user) {
        return assignee != null && assignee.getId().equals(user.getId());
    }

    public String text() {
        return title + " " + description;
    }

    public Long getId() {
        return id;
    }

    public String getNumber() {
        return number;
    }

    public int getSequenceNo() {
        return sequenceNo;
    }

    public User getReporter() {
        return reporter;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public ItService getService() {
        return service;
    }

    public Asset getAsset() {
        return asset;
    }

    public String getLocation() {
        return location;
    }

    public IncidentCategory getCategory() {
        return category;
    }

    public String getClassifiedBy() {
        return classifiedBy;
    }

    public Double getClassificationConfidence() {
        return classificationConfidence;
    }

    public Level getImpact() {
        return impact;
    }

    public Level getUrgency() {
        return urgency;
    }

    public int getAffectedUsers() {
        return affectedUsers;
    }

    public double getPriorityScore() {
        return priorityScore;
    }

    public Priority getPriority() {
        return priority;
    }

    public String getPriorityRationale() {
        return priorityRationale;
    }

    public IncidentStatus getStatus() {
        return status;
    }

    public Channel getChannel() {
        return channel;
    }

    public Technician getAssignee() {
        return assignee;
    }

    public Instant getAssignedAt() {
        return assignedAt;
    }

    public String getAssignmentStrategy() {
        return assignmentStrategy;
    }

    public int getEscalationLevel() {
        return escalationLevel;
    }

    public User getEscalatedTo() {
        return escalatedTo;
    }

    public SlaTracker getSla() {
        return sla;
    }

    public Resolution getResolution() {
        return resolution;
    }

    public Feedback getFeedback() {
        return feedback;
    }

    public Incident getPossibleDuplicateOf() {
        return possibleDuplicateOf;
    }

    public Double getDuplicateScore() {
        return duplicateScore;
    }

    public ProblemRecord getProblem() {
        return problem;
    }

    public List<WorkNote> getNotes() {
        return Collections.unmodifiableList(notes);
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Instant getClosedAt() {
        return closedAt;
    }
}
