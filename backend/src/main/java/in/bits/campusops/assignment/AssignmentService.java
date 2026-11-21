package in.bits.campusops.assignment;

import in.bits.campusops.assignment.AssignmentStrategy.Candidate;
import in.bits.campusops.assignment.AssignmentStrategy.Recommendation;
import in.bits.campusops.common.Exceptions.InvalidInputException;
import in.bits.campusops.common.Exceptions.TechnicianUnavailableException;
import in.bits.campusops.common.Exceptions.WorkloadExceededException;
import in.bits.campusops.incident.Incident;
import in.bits.campusops.incident.IncidentRepository;
import in.bits.campusops.incident.IncidentStatus;
import in.bits.campusops.settings.SettingsService;
import in.bits.campusops.user.Technician;
import in.bits.campusops.user.UserRepository;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class AssignmentService {

    private final Map<String, AssignmentStrategy> strategies;
    private final SettingsService settings;
    private final UserRepository users;
    private final IncidentRepository incidents;

    public AssignmentService(List<AssignmentStrategy> strategies, SettingsService settings, UserRepository users,
                             IncidentRepository incidents) {
        this.strategies = strategies.stream()
                .collect(Collectors.toMap(AssignmentStrategy::key, Function.identity()));
        this.settings = settings;
        this.users = users;
        this.incidents = incidents;
    }

    public AssignmentStrategy active() {
        return require(settings.get().getAssignmentStrategy());
    }

    public AssignmentStrategy require(String key) {
        AssignmentStrategy s = strategies.get(key);
        if (s == null) {
            throw new InvalidInputException("Unknown assignment strategy " + key);
        }
        return s;
    }

    public List<AssignmentStrategy> all() {
        return strategies.values().stream().sorted((a, b) -> a.key().compareTo(b.key())).toList();
    }

    /** Picks a technician with the active strategy. Throws if nobody can take it. */
    public Recommendation choose(Incident incident) {
        AssignmentStrategy strategy = active();
        return strategy.select(incident, candidates(incident, true))
                .orElseThrow(() -> new TechnicianUnavailableException(
                        "No available technician can take " + incident.getNumber() + " right now; it stays queued"));
    }

    /** Ranked list for the "who should take this?" panel (always the smart strategy). */
    public List<Recommendation> recommend(Incident incident) {
        return require("SMART").rank(incident, candidates(incident, true));
    }

    /** Checks a manual assignment. {@code override} lets a lead exceed the workload limit. */
    public void checkManual(Technician technician, boolean override) {
        if (!technician.isActive() || !technician.isAvailable()) {
            throw new TechnicianUnavailableException(technician.getFullName() + " is not available");
        }
        int active = activeLoad().getOrDefault(technician.getId(), 0);
        if (!override && active >= technician.getMaxConcurrent()) {
            throw new WorkloadExceededException(technician.getFullName(), active, technician.getMaxConcurrent());
        }
    }

    List<Candidate> candidates(Incident incident, boolean onlyWithCapacity) {
        Map<Long, Integer> load = activeLoad();
        Map<Long, Integer> history = incidents
                .countFixedOnServiceByAssignee(incident.getService().getId(), IncidentStatus.FIXED).stream()
                .collect(Collectors.toMap(r -> (Long) r[0], r -> ((Number) r[1]).intValue()));
        String category = incident.getCategory() == null ? null : incident.getCategory().getCode();
        return users.findActiveTechnicians().stream()
                .filter(t -> !onlyWithCapacity || t.canTakeMore(load.getOrDefault(t.getId(), 0)))
                .filter(t -> incident.getAssignee() == null || !t.getId().equals(incident.getAssignee().getId()))
                .map(t -> new Candidate(t, load.getOrDefault(t.getId(), 0), history.getOrDefault(t.getId(), 0),
                        category != null && t.hasSkill(category)))
                .toList();
    }

    private Map<Long, Integer> activeLoad() {
        return incidents.countActiveByAssignee().stream()
                .collect(Collectors.toMap(r -> (Long) r[0], r -> ((Number) r[1]).intValue()));
    }
}
