package in.bits.campusops.settings;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;

/** Runtime configuration an administrator can change without redeploying. Single row, id 1. */
@Entity
public class SystemSettings {

    @Id
    private Long id = 1L;

    private String assignmentStrategy = "SKILL_BASED";
    private String priorityStrategy = "WEIGHTED_SCORE";
    private int slaWarningPercent = 75;
    private double duplicateThreshold = 0.45;
    private int correlationWindowMinutes = 60;
    private int correlationMinIncidents = 3;
    private boolean autoEscalation = true;
    private boolean aiTriage = true;

    public SystemSettings() {
    }

    public void update(String assignmentStrategy, String priorityStrategy, int slaWarningPercent,
                       double duplicateThreshold, int correlationWindowMinutes, int correlationMinIncidents,
                       boolean autoEscalation, boolean aiTriage) {
        if (slaWarningPercent < 10 || slaWarningPercent > 95) {
            throw new IllegalArgumentException("SLA warning must be between 10% and 95%");
        }
        if (duplicateThreshold < 0.1 || duplicateThreshold > 0.95) {
            throw new IllegalArgumentException("Duplicate threshold must be between 0.1 and 0.95");
        }
        if (correlationWindowMinutes < 5 || correlationWindowMinutes > 24 * 60) {
            throw new IllegalArgumentException("Correlation window must be between 5 minutes and 24 hours");
        }
        if (correlationMinIncidents < 2) {
            throw new IllegalArgumentException("Correlation needs at least 2 incidents");
        }
        this.assignmentStrategy = assignmentStrategy;
        this.priorityStrategy = priorityStrategy;
        this.slaWarningPercent = slaWarningPercent;
        this.duplicateThreshold = duplicateThreshold;
        this.correlationWindowMinutes = correlationWindowMinutes;
        this.correlationMinIncidents = correlationMinIncidents;
        this.autoEscalation = autoEscalation;
        this.aiTriage = aiTriage;
    }

    public Long getId() {
        return id;
    }

    public String getAssignmentStrategy() {
        return assignmentStrategy;
    }

    public String getPriorityStrategy() {
        return priorityStrategy;
    }

    public int getSlaWarningPercent() {
        return slaWarningPercent;
    }

    public double getDuplicateThreshold() {
        return duplicateThreshold;
    }

    public int getCorrelationWindowMinutes() {
        return correlationWindowMinutes;
    }

    public int getCorrelationMinIncidents() {
        return correlationMinIncidents;
    }

    public boolean isAutoEscalation() {
        return autoEscalation;
    }

    public boolean isAiTriage() {
        return aiTriage;
    }
}
