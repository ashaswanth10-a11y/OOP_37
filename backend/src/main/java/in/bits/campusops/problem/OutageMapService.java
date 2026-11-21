package in.bits.campusops.problem;

import in.bits.campusops.catalog.CatalogService;
import in.bits.campusops.catalog.ItService;
import in.bits.campusops.catalog.Priority;
import in.bits.campusops.incident.Incident;
import in.bits.campusops.incident.IncidentRepository;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Builds the live outage map: every service, its health, and the dependency edges. */
@Service
@Transactional(readOnly = true)
public class OutageMapService {

    public enum Health { OPERATIONAL, DEGRADED, OUTAGE, IMPACTED }

    public record Node(Long id, String code, String name, String type, String location, int criticality,
                       int x, int y, Health health, int openIncidents, String worstPriority, boolean rootCause,
                       String problemNumber) {
    }

    public record Edge(Long from, Long to) {
    }

    /**
     * An open problem. {@code affectedServiceIds} is the root and everything downstream of it;
     * {@code followIncident*} is the oldest open linked incident, the one to say "Me too" to.
     */
    public record ProblemSummary(Long id, String number, String title, String rootService, int incidents,
                                 String detectedAt, Long rootServiceId, List<Long> affectedServiceIds,
                                 Long followIncidentId, String followIncidentNumber) {
    }

    public record OutageMap(List<Node> nodes, List<Edge> edges, List<ProblemSummary> problems) {
    }

    private final CatalogService catalog;
    private final IncidentRepository incidents;
    private final ProblemRepository problems;

    public OutageMapService(CatalogService catalog, IncidentRepository incidents, ProblemRepository problems) {
        this.catalog = catalog;
        this.incidents = incidents;
        this.problems = problems;
    }

    public OutageMap build() {
        List<ItService> services = catalog.allServices();
        Map<Long, List<Incident>> openByService = new HashMap<>();
        for (Incident i : incidents.findOpen()) {
            openByService.computeIfAbsent(i.getService().getId(), k -> new ArrayList<>()).add(i);
        }
        List<ProblemRecord> open = problems.findByStatusOrderByDetectedAtDesc(ProblemRecord.Status.OPEN);
        Map<Long, ProblemRecord> rootOf = new HashMap<>();
        Set<Long> impacted = new HashSet<>();
        for (ProblemRecord p : open) {
            rootOf.put(p.getRootService().getId(), p);
            catalog.downstreamOf(p.getRootService()).forEach(s -> impacted.add(s.getId()));
        }
        List<Node> nodes = new ArrayList<>();
        List<Edge> edges = new ArrayList<>();
        for (ItService s : services) {
            List<Incident> list = openByService.getOrDefault(s.getId(), List.of());
            Priority worst = list.stream().map(Incident::getPriority).filter(p -> p != null)
                    .min(Comparator.naturalOrder()).orElse(null);
            boolean root = rootOf.containsKey(s.getId());
            Health health;
            if (root || (worst != null && worst.isUrgent())) {
                health = Health.OUTAGE;
            } else if (!list.isEmpty()) {
                health = Health.DEGRADED;
            } else if (impacted.contains(s.getId())) {
                health = Health.IMPACTED;
            } else {
                health = Health.OPERATIONAL;
            }
            nodes.add(new Node(s.getId(), s.getCode(), s.getName(), s.getType().name(), s.getLocation(),
                    s.getCriticality(), s.getMapX(), s.getMapY(), health, list.size(),
                    worst == null ? null : worst.name(), root, root ? rootOf.get(s.getId()).getNumber() : null));
            for (ItService up : s.getDependsOn()) {
                edges.add(new Edge(up.getId(), s.getId()));
            }
        }
        List<ProblemSummary> summaries = open.stream().map(this::summarise).toList();
        return new OutageMap(nodes, edges, summaries);
    }

    private ProblemSummary summarise(ProblemRecord p) {
        List<Incident> linked = incidents.findByProblemIdOrderByCreatedAtAsc(p.getId());
        Incident follow = linked.stream().filter(Incident::isOpen).findFirst().orElse(null);
        List<Long> affected = new ArrayList<>();
        affected.add(p.getRootService().getId());
        catalog.downstreamOf(p.getRootService()).forEach(s -> affected.add(s.getId()));
        return new ProblemSummary(p.getId(), p.getNumber(), p.getTitle(), p.getRootService().getName(), linked.size(),
                p.getDetectedAt().toString(), p.getRootService().getId(), affected.stream().distinct().toList(),
                follow == null ? null : follow.getId(), follow == null ? null : follow.getNumber());
    }
}
