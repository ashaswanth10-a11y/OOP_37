package in.bits.campusops.problem;

import in.bits.campusops.catalog.CatalogService;
import in.bits.campusops.catalog.ItService;
import in.bits.campusops.catalog.Priority;
import in.bits.campusops.common.TimeProvider;
import in.bits.campusops.events.IncidentEventPublisher;
import in.bits.campusops.events.IncidentEventType;
import in.bits.campusops.incident.Incident;
import in.bits.campusops.incident.IncidentRepository;
import in.bits.campusops.incident.IncidentStatus;
import in.bits.campusops.settings.SettingsService;
import in.bits.campusops.settings.SystemSettings;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Root-cause detection on the service dependency graph.
 *
 * <p>When a new incident arrives, every service it depends on (and the service itself) is a
 * candidate root. A candidate "explains" the recent open P1-P3 incidents of one category on itself
 * and on everything downstream of it. The most specific candidate (smallest blast radius) that explains enough
 * incidents across at least two services becomes a Problem record, and all those incidents
 * are linked to it. Example: Wi-Fi complaints from three hostels all point at Core Switch B.
 */
@Service
@Transactional
public class CorrelationEngine {

    public record Candidate(ItService root, List<Incident> incidents, int blastRadius, int services) {
    }

    private final IncidentRepository incidents;
    private final ProblemRepository problems;
    private final CatalogService catalog;
    private final SettingsService settings;
    private final IncidentEventPublisher events;
    private final TimeProvider time;

    public CorrelationEngine(IncidentRepository incidents, ProblemRepository problems, CatalogService catalog,
                             SettingsService settings, IncidentEventPublisher events, TimeProvider time) {
        this.incidents = incidents;
        this.problems = problems;
        this.catalog = catalog;
        this.settings = settings;
        this.events = events;
        this.time = time;
    }

    public Optional<ProblemRecord> correlate(Incident trigger) {
        SystemSettings s = settings.get();
        Optional<ProblemRecord> known = existingExplanation(trigger);
        if (known.isPresent()) {
            ProblemRecord p = known.get();
            trigger.linkProblem(p);
            events.publish(IncidentEventType.PROBLEM_LINKED, trigger, null,
                    "Linked to " + p.getNumber() + " (suspected " + p.getRootService().getName() + " failure)");
            return known;
        }
        Instant since = time.now().minusSeconds(s.getCorrelationWindowMinutes() * 60L);
        // incidents an open problem already explains are not evidence for a new hypothesis
        List<Incident> recent = incidents.findByStatusInAndCreatedAtAfter(IncidentStatus.OPEN, since).stream()
                .filter(i -> i.getProblem() == null || !i.getProblem().isOpen())
                .toList();
        Optional<Candidate> best = bestCandidate(trigger, recent, s.getCorrelationMinIncidents());
        if (best.isEmpty()) {
            return Optional.empty();
        }
        Candidate c = best.get();
        Optional<ProblemRecord> existing = problems.findFirstByRootServiceIdAndStatus(c.root().getId(),
                ProblemRecord.Status.OPEN);
        ProblemRecord problem;
        boolean created = existing.isEmpty();
        if (created) {
            problem = problems.save(new ProblemRecord(c.root(), "Suspected " + c.root().getName() + " failure",
                    analysis(c, s.getCorrelationWindowMinutes()), time.now()));
            problem.assignNumber();
        } else {
            problem = existing.get();
            problem.updateAnalysis(analysis(c, s.getCorrelationWindowMinutes()));
        }
        for (Incident i : c.incidents()) {
            if (i.getProblem() != null && (i.getProblem().getId().equals(problem.getId()) || i.getProblem().isOpen())) {
                continue; // already explained by this or another open problem
            }
            i.linkProblem(problem);
            String description = created && i.getId().equals(trigger.getId())
                    ? "New problem " + problem.getNumber() + ": " + c.incidents().size() + " incidents across "
                    + c.services() + " services point at " + c.root().getName()
                    : "Linked to " + problem.getNumber() + " (suspected " + c.root().getName() + " failure)";
            events.publish(IncidentEventType.PROBLEM_LINKED, i, null, description);
        }
        return Optional.of(problem);
    }

    /**
     * An open problem already explains this incident if its root is the incident's service or
     * upstream of it and its incidents show the same symptom. The most specific one wins.
     */
    private Optional<ProblemRecord> existingExplanation(Incident trigger) {
        if (trigger.getCategory() == null || trigger.getPriority() == null || trigger.getPriority() == Priority.P4) {
            return Optional.empty();
        }
        Set<ItService> upstream = new LinkedHashSet<>(catalog.upstreamOf(trigger.getService()));
        upstream.add(trigger.getService());
        return problems.findByStatusOrderByDetectedAtDesc(ProblemRecord.Status.OPEN).stream()
                .filter(p -> upstream.contains(p.getRootService()))
                .filter(p -> incidents.findByProblemIdOrderByCreatedAtAsc(p.getId()).stream()
                        .anyMatch(i -> i.getCategory() != null
                                && i.getCategory().getCode().equals(trigger.getCategory().getCode())))
                .min(Comparator.comparingInt(p -> catalog.downstreamOf(p.getRootService()).size()));
    }

    /** Pure graph logic, separated so it can be unit tested without a database. */
    public Optional<Candidate> bestCandidate(Incident trigger, List<Incident> recent, int minIncidents) {
        // low-priority noise (one broken keyboard) never drives an outage hypothesis
        if (trigger.getPriority() == null || trigger.getPriority() == Priority.P4 || trigger.getCategory() == null) {
            return Optional.empty();
        }
        ItService triggerService = trigger.getService();
        String symptom = trigger.getCategory().getCode();
        Set<ItService> roots = new LinkedHashSet<>();
        roots.add(triggerService);
        roots.addAll(catalog.upstreamOf(triggerService));
        // one root cause produces one kind of symptom: only incidents of the trigger's category count
        List<Incident> relevant = recent.stream()
                .filter(i -> i.getPriority() != null && i.getPriority() != Priority.P4)
                .filter(i -> i.getCategory() != null && i.getCategory().getCode().equals(symptom))
                .toList();
        List<Candidate> candidates = new ArrayList<>();
        for (ItService root : roots) {
            Set<ItService> covered = new LinkedHashSet<>(catalog.downstreamOf(root));
            covered.add(root);
            List<Incident> explained = relevant.stream().filter(i -> covered.contains(i.getService())).toList();
            if (explained.stream().noneMatch(i -> i.getId().equals(trigger.getId()))) {
                continue;
            }
            Map<Long, Long> perService = explained.stream()
                    .collect(Collectors.groupingBy(i -> i.getService().getId(), Collectors.counting()));
            int distinctServices = perService.size();
            long busiest = perService.values().stream().mapToLong(Long::longValue).max().orElse(0);
            // spread across services (no one service holds more than half), or a pile-up on one service
            boolean spread = distinctServices >= 2 && busiest * 2 <= explained.size();
            boolean pileUp = distinctServices == 1 && explained.size() >= minIncidents + 2;
            boolean enough = explained.size() >= minIncidents && (spread || pileUp);
            if (enough) {
                candidates.add(new Candidate(root, explained, covered.size(), distinctServices));
            }
        }
        return candidates.stream().min(Comparator.comparingInt(Candidate::blastRadius)
                .thenComparing(c -> -c.incidents().size()));
    }

    private static String analysis(Candidate c, int windowMinutes) {
        String services = c.incidents().stream().map(i -> i.getService().getName()).distinct()
                .collect(Collectors.joining(", "));
        String numbers = c.incidents().stream().map(Incident::getNumber).collect(Collectors.joining(", "));
        return "%d open incidents in the last %d minutes (%s) on %s. All of these services depend on %s, the most "
                .formatted(c.incidents().size(), windowMinutes, numbers, services, c.root().getName())
                + "specific shared dependency, so it is the likely root cause. Blast radius: " + c.blastRadius()
                + " services.";
    }
}
