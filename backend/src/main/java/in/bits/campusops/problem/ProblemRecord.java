package in.bits.campusops.problem;

import in.bits.campusops.catalog.ItService;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import java.time.Instant;

/**
 * An ITIL "problem": one underlying cause behind several incidents, e.g. "Core Switch B
 * failure" behind 12 hostel Wi-Fi complaints. Created automatically by correlation.
 */
@Entity
public class ProblemRecord {

    public enum Status { OPEN, RESOLVED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, length = 16)
    private String number;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    private ItService rootService;

    @Column(nullable = false)
    private String title;

    @Column(length = 2000)
    private String analysis;

    @Enumerated(EnumType.STRING)
    private Status status = Status.OPEN;

    private Instant detectedAt;
    private Instant resolvedAt;

    @Column(length = 2000)
    private String resolutionSummary;

    protected ProblemRecord() {
    }

    public ProblemRecord(ItService rootService, String title, String analysis, Instant detectedAt) {
        this.rootService = rootService;
        this.title = title;
        this.analysis = analysis;
        this.detectedAt = detectedAt;
    }

    public void assignNumber() {
        this.number = "PRB-" + (100 + id);
    }

    public void updateAnalysis(String analysis) {
        this.analysis = analysis;
    }

    public void resolve(String summary, Instant now) {
        this.status = Status.RESOLVED;
        this.resolutionSummary = summary;
        this.resolvedAt = now;
    }

    public boolean isOpen() {
        return status == Status.OPEN;
    }

    public Long getId() {
        return id;
    }

    public String getNumber() {
        return number;
    }

    public ItService getRootService() {
        return rootService;
    }

    public String getTitle() {
        return title;
    }

    public String getAnalysis() {
        return analysis;
    }

    public Status getStatus() {
        return status;
    }

    public Instant getDetectedAt() {
        return detectedAt;
    }

    public Instant getResolvedAt() {
        return resolvedAt;
    }

    public String getResolutionSummary() {
        return resolutionSummary;
    }
}
