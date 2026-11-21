package in.bits.campusops.incident;

import in.bits.campusops.audit.AuditController.AuditView;
import in.bits.campusops.catalog.CatalogViews.CategoryView;
import in.bits.campusops.catalog.CatalogViews.ServiceRef;
import java.time.Instant;
import java.util.List;
import java.util.Set;

public final class IncidentViews {

    private IncidentViews() {
    }

    public record PersonRef(Long id, String code, String name) {
    }

    public record Summary(Long id, String number, String title, String status, String priority, CategoryView category,
                          ServiceRef service, PersonRef reporter, PersonRef assignee, String slaState,
                          Instant resolutionDue, Instant createdAt, Instant updatedAt, int escalationLevel,
                          String problemNumber, String duplicateOf, String channel, String location,
                          int supporters, int affectedUsers) {
    }

    public record SlaView(String policy, Instant start, Instant responseDue, Instant resolutionDue, Instant respondedAt,
                          Instant resolvedAt, boolean responseBreached, boolean resolutionBreached, String state,
                          double used) {
    }

    public record NoteView(Long id, PersonRef author, String authorRole, String type, String text, boolean internal,
                           Instant createdAt) {
    }

    public record DuplicateView(Long id, String number, String title, String status, double score, String reason) {
    }

    public record FixView(Long id, String number, String title, double similarity, String resolution,
                          String rootCause) {
    }

    public record RecommendationView(PersonRef technician, String team, double score, String reason) {
    }

    public record RiskView(String level, double score, String reason) {
    }

    public record ProblemRef(Long id, String number, String title, String status) {
    }

    public record Detail(Summary summary, String description, String impact, String urgency, int affectedUsers,
                         double priorityScore, String priorityRationale, String classifiedBy,
                         Double classificationConfidence, String assetTag, String assignmentStrategy,
                         PersonRef escalatedTo, SlaView sla, RiskView risk, String resolutionSummary,
                         String rootCause, String resolutionCode, String resolvedBy, Integer feedbackRating,
                         String feedbackComment, Instant closedAt, String stateDescription, Set<String> allowedNext,
                         Set<IncidentPolicy.Action> actions, List<NoteView> notes, List<AuditView> timeline,
                         List<DuplicateView> similar, List<FixView> suggestedFixes,
                         List<RecommendationView> recommendations, ProblemRef problem, boolean supportedByMe) {
    }
}
