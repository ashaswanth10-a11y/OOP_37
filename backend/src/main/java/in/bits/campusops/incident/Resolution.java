package in.bits.campusops.incident;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

/** What fixed the incident. Becomes part of the knowledge base for later suggestions. */
@Embeddable
public class Resolution {

    @Column(length = 2000)
    private String resolutionSummary;

    @Column(length = 1000)
    private String rootCause;

    private String resolutionCode;
    private String resolvedByName;

    protected Resolution() {
    }

    public Resolution(String summary, String rootCause, String code, String resolvedByName) {
        if (summary == null || summary.isBlank()) {
            throw new IllegalArgumentException("Describe how the incident was resolved");
        }
        this.resolutionSummary = summary.trim();
        this.rootCause = rootCause;
        this.resolutionCode = code;
        this.resolvedByName = resolvedByName;
    }

    public boolean isPresent() {
        return resolutionSummary != null && !resolutionSummary.isBlank();
    }

    public String getResolutionSummary() {
        return resolutionSummary;
    }

    public String getRootCause() {
        return rootCause;
    }

    public String getResolutionCode() {
        return resolutionCode;
    }

    public String getResolvedByName() {
        return resolvedByName;
    }
}
