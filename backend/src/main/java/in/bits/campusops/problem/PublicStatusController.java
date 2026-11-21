package in.bits.campusops.problem;

import java.time.Instant;
import java.util.List;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public campus status page (no login): which services are up, and known problems.
 * Lets students check before reporting, which cuts duplicate reports.
 */
@RestController
public class PublicStatusController {

    private final OutageMapService map;
    private final in.bits.campusops.common.TimeProvider time;

    public PublicStatusController(OutageMapService map, in.bits.campusops.common.TimeProvider time) {
        this.map = map;
        this.time = time;
    }

    /** The server's clock, so every screen's SLA timers agree. */
    @GetMapping("/api/public/time")
    public java.util.Map<String, Object> time() {
        return java.util.Map.of("now", time.now());
    }

    public record ServiceStatus(Long id, String name, String type, String location, String health) {
    }

    /** A known outage: lets the report page and the bot say "this is already being fixed, tap Me too". */
    public record KnownProblem(String number, String title, String rootService, int reports, String since,
                               Long rootServiceId, List<Long> affectedServiceIds, Long followIncidentId,
                               String followIncidentNumber) {
    }

    public record CampusStatus(String overall, Instant generatedAt, List<ServiceStatus> services,
                               List<KnownProblem> problems, List<OutageMapService.Edge> edges,
                               List<OutageMapService.Node> nodes) {
    }

    @GetMapping("/api/public/status")
    @Transactional(readOnly = true)
    public CampusStatus status() {
        var m = map.build();
        List<ServiceStatus> services = m.nodes().stream()
                .map(n -> new ServiceStatus(n.id(), n.name(), n.type(), n.location(), n.health().name())).toList();
        long outages = m.nodes().stream().filter(n -> n.health() == OutageMapService.Health.OUTAGE).count();
        long degraded = m.nodes().stream().filter(n -> n.health() != OutageMapService.Health.OPERATIONAL).count();
        String overall = outages > 0 ? "MAJOR_OUTAGE" : degraded > 0 ? "DEGRADED" : "ALL_OPERATIONAL";
        List<KnownProblem> problems = m.problems().stream()
                .map(p -> new KnownProblem(p.number(), p.title(), p.rootService(), p.incidents(), p.detectedAt(),
                        p.rootServiceId(), p.affectedServiceIds(), p.followIncidentId(), p.followIncidentNumber()))
                .toList();
        // public nodes carry no incident numbers or names, only health
        List<OutageMapService.Node> nodes = m.nodes().stream()
                .map(n -> new OutageMapService.Node(n.id(), n.code(), n.name(), n.type(), n.location(), n.criticality(),
                        n.x(), n.y(), n.health(), n.openIncidents(), n.worstPriority(), n.rootCause(), null))
                .toList();
        return new CampusStatus(overall, time.now(), services, problems, m.edges(), nodes);
    }
}
