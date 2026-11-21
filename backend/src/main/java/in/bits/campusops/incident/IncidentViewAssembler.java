package in.bits.campusops.incident;

import in.bits.campusops.assignment.AssignmentService;
import in.bits.campusops.audit.AuditController.AuditView;
import in.bits.campusops.audit.AuditRepository;
import in.bits.campusops.catalog.CatalogViews.CategoryView;
import in.bits.campusops.catalog.CatalogViews.ServiceRef;
import in.bits.campusops.common.TimeProvider;
import in.bits.campusops.incident.IncidentViews.Detail;
import in.bits.campusops.incident.IncidentViews.DuplicateView;
import in.bits.campusops.incident.IncidentViews.FixView;
import in.bits.campusops.incident.IncidentViews.NoteView;
import in.bits.campusops.incident.IncidentViews.PersonRef;
import in.bits.campusops.incident.IncidentViews.ProblemRef;
import in.bits.campusops.incident.IncidentViews.RecommendationView;
import in.bits.campusops.incident.IncidentViews.RiskView;
import in.bits.campusops.incident.IncidentViews.SlaView;
import in.bits.campusops.incident.IncidentViews.Summary;
import in.bits.campusops.intelligence.SimilarityService;
import in.bits.campusops.intelligence.SlaRiskAnalyzer;
import in.bits.campusops.settings.SettingsService;
import in.bits.campusops.user.User;
import in.bits.campusops.user.UserService;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Maps incidents to API views, adding the intelligence panels for staff. */
@Component
@Transactional(readOnly = true)
public class IncidentViewAssembler {

    private final AuditRepository audit;
    private final IncidentPolicy policy;
    private final SimilarityService similarity;
    private final SlaRiskAnalyzer risk;
    private final AssignmentService assignment;
    private final UserService users;
    private final SettingsService settings;
    private final TimeProvider time;

    public IncidentViewAssembler(AuditRepository audit, IncidentPolicy policy, SimilarityService similarity,
                                 SlaRiskAnalyzer risk, AssignmentService assignment, UserService users,
                                 SettingsService settings, TimeProvider time) {
        this.audit = audit;
        this.policy = policy;
        this.similarity = similarity;
        this.risk = risk;
        this.assignment = assignment;
        this.users = users;
        this.settings = settings;
        this.time = time;
    }

    public static PersonRef person(User u) {
        return u == null ? null : new PersonRef(u.getId(), u.getCode(), u.getFullName());
    }

    public Summary summary(Incident i) {
        return new Summary(i.getId(), i.getNumber(), i.getTitle(), i.getStatus().name(),
                i.getPriority() == null ? null : i.getPriority().name(), CategoryView.of(i.getCategory()),
                ServiceRef.of(i.getService()), person(i.getReporter()), person(i.getAssignee()),
                i.getSla().getSlaState() == null ? null : i.getSla().getSlaState().name(),
                i.getSla().getResolutionDue(), i.getCreatedAt(), i.getUpdatedAt(), i.getEscalationLevel(),
                i.getProblem() == null ? null : i.getProblem().getNumber(),
                i.getPossibleDuplicateOf() == null ? null : i.getPossibleDuplicateOf().getNumber(),
                i.getChannel() == null ? null : i.getChannel().name(), i.getLocation(), i.getSupporterCount(),
                i.getAffectedUsers());
    }

    public Detail detail(Incident i, User viewer) {
        boolean staff = viewer.isStaff();
        Map<Long, Integer> load = users.activeLoad();
        SlaTracker sla = i.getSla();
        SlaView slaView = new SlaView(sla.getSlaPolicyName(), sla.getSlaStart(), sla.getResponseDue(),
                sla.getResolutionDue(), sla.getRespondedAt(), sla.getResolvedAt(), sla.isResponseBreached(),
                sla.isResolutionBreached(), sla.getSlaState() == null ? null : sla.getSlaState().name(),
                Math.round(sla.resolutionUsed(time.now()) * 100) / 100.0);
        SlaRiskAnalyzer.Risk r = risk.assess(i, load);

        List<NoteView> notes = i.getNotes().stream()
                .filter(n -> staff || !n.isInternal())
                .map(n -> new NoteView(n.getId(), person(n.getAuthor()),
                        n.getAuthor() == null ? "SYSTEM" : n.getAuthor().getRole().name(), n.getType().name(),
                        n.getText(), n.isInternal(), n.getCreatedAt()))
                .toList();

        List<AuditView> timeline = audit.findByIncidentIdOrderByAtAscIdAsc(i.getId()).stream()
                .map(AuditView::of)
                .filter(a -> staff || !a.description().startsWith("Internal note"))
                .toList();

        List<DuplicateView> similar = List.of();
        List<FixView> fixes = List.of();
        List<RecommendationView> recs = List.of();
        if (staff) {
            similar = similarity.findDuplicates(i.text(), i.getService(), i.getId(),
                            Math.max(0.3, settings.get().getDuplicateThreshold() - 0.1)).stream()
                    .map(m -> new DuplicateView(m.incident().getId(), m.incident().getNumber(), m.incident().getTitle(),
                            m.incident().getStatus().name(), m.score(), m.reason()))
                    .toList();
            if (i.isOpen()) {
                fixes = similarity.suggestFixes(i.text(), i.getService(), i.getId()).stream()
                        .map(f -> new FixView(f.source().getId(), f.source().getNumber(), f.source().getTitle(),
                                f.similarity(), f.resolution(), f.rootCause()))
                        .toList();
                if (i.state().allowsAssignment()) {
                    recs = assignment.recommend(i).stream().limit(4)
                            .map(rec -> new RecommendationView(person(rec.technician()), rec.technician().getTeam(),
                                    rec.score(), rec.reason()))
                            .toList();
                }
            }
        }

        Resolution res = i.getResolution();
        Feedback fb = i.getFeedback();
        Set<String> next = i.state().allowedNext().stream().map(Enum::name).collect(Collectors.toSet());
        return new Detail(summary(i), i.getDescription(), i.getImpact().name(), i.getUrgency().name(),
                i.getAffectedUsers(), i.getPriorityScore(), i.getPriorityRationale(), i.getClassifiedBy(),
                i.getClassificationConfidence(), i.getAsset() == null ? null : i.getAsset().getTag(),
                i.getAssignmentStrategy(), person(i.getEscalatedTo()), slaView,
                new RiskView(r.level().name(), r.score(), r.reason()),
                res == null ? null : res.getResolutionSummary(), res == null ? null : res.getRootCause(),
                res == null ? null : res.getResolutionCode(), res == null ? null : res.getResolvedByName(),
                fb == null ? null : fb.getFeedbackRating(), fb == null ? null : fb.getFeedbackComment(),
                i.getClosedAt(), i.state().describe(), next, policy.allowedActions(viewer, i), notes, timeline,
                similar, fixes, recs,
                i.getProblem() == null ? null : new ProblemRef(i.getProblem().getId(), i.getProblem().getNumber(),
                        i.getProblem().getTitle(), i.getProblem().getStatus().name()),
                i.isSupportedBy(viewer));
    }
}
