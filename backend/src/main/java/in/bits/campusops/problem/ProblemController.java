package in.bits.campusops.problem;

import in.bits.campusops.common.Exceptions.InvalidInputException;
import in.bits.campusops.common.Exceptions.ResourceNotFoundException;
import in.bits.campusops.common.TimeProvider;
import in.bits.campusops.incident.IncidentRepository;
import in.bits.campusops.incident.IncidentService;
import in.bits.campusops.incident.IncidentViewAssembler;
import in.bits.campusops.incident.IncidentViews.Summary;
import in.bits.campusops.user.CurrentUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Transactional
public class ProblemController {

    private final ProblemRepository problems;
    private final IncidentRepository incidents;
    private final IncidentService incidentService;
    private final IncidentViewAssembler views;
    private final OutageMapService map;
    private final CurrentUser currentUser;
    private final TimeProvider time;

    public ProblemController(ProblemRepository problems, IncidentRepository incidents, IncidentService incidentService,
                             IncidentViewAssembler views, OutageMapService map, CurrentUser currentUser,
                             TimeProvider time) {
        this.problems = problems;
        this.incidents = incidents;
        this.incidentService = incidentService;
        this.views = views;
        this.map = map;
        this.currentUser = currentUser;
        this.time = time;
    }

    public record ProblemView(Long id, String number, String title, String analysis, String status, Long rootServiceId,
                              String rootService, Instant detectedAt, Instant resolvedAt, String resolutionSummary,
                              List<Summary> incidents) {
    }

    public record ResolveProblemRequest(@NotBlank String summary) {
    }

    @GetMapping("/api/problems")
    @PreAuthorize("hasAnyRole('TECHNICIAN','SYSTEM_ADMIN')")
    @Transactional(readOnly = true)
    public List<ProblemView> all() {
        return problems.findAllByOrderByDetectedAtDesc().stream().map(this::view).toList();
    }

    @GetMapping("/api/problems/{id}")
    @PreAuthorize("hasAnyRole('TECHNICIAN','SYSTEM_ADMIN')")
    @Transactional(readOnly = true)
    public ProblemView one(@PathVariable Long id) {
        return view(require(id));
    }

    @PostMapping("/api/problems/{id}/resolve")
    @PreAuthorize("hasAnyRole('TEAM_LEAD','SYSTEM_ADMIN')")
    public Map<String, Object> resolve(@PathVariable Long id, @Valid @RequestBody ResolveProblemRequest r) {
        ProblemRecord p = require(id);
        if (!p.isOpen()) {
            throw new InvalidInputException(p.getNumber() + " is already resolved");
        }
        int count = incidentService.resolveLinked(p, r.summary(), currentUser.get());
        p.resolve(r.summary(), time.now());
        return Map.of("problem", view(p), "incidentsResolved", count);
    }

    @GetMapping("/api/outage-map")
    @Transactional(readOnly = true)
    public OutageMapService.OutageMap outageMap() {
        return map.build();
    }

    private ProblemRecord require(Long id) {
        return problems.findById(id).orElseThrow(() -> new ResourceNotFoundException("Problem", id));
    }

    private ProblemView view(ProblemRecord p) {
        return new ProblemView(p.getId(), p.getNumber(), p.getTitle(), p.getAnalysis(), p.getStatus().name(),
                p.getRootService().getId(), p.getRootService().getName(), p.getDetectedAt(), p.getResolvedAt(),
                p.getResolutionSummary(),
                incidents.findByProblemIdOrderByCreatedAtAsc(p.getId()).stream().map(views::summary).toList());
    }
}
