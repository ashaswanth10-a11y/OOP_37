package in.bits.campusops.user;

import in.bits.campusops.common.Exceptions.InvalidInputException;
import in.bits.campusops.common.Exceptions.ResourceNotFoundException;
import in.bits.campusops.incident.IncidentRepository;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class UserService {

    private final UserRepository users;
    private final UserFactory factory;
    private final IncidentRepository incidents;
    private final String allowedDomain;

    public UserService(UserRepository users, UserFactory factory, IncidentRepository incidents,
                       @Value("${campusops.auth.allowed-domain:}") String allowedDomain) {
        this.users = users;
        this.factory = factory;
        this.incidents = incidents;
        this.allowedDomain = allowedDomain;
    }

    public User register(UserFactory.Spec spec) {
        String email = spec.email().trim().toLowerCase();
        if (!allowedDomain.isBlank() && spec.role() == Role.STUDENT && !email.endsWith("@" + allowedDomain)) {
            throw new InvalidInputException("Use your campus email (@" + allowedDomain + ")");
        }
        if (users.existsByEmailIgnoreCase(email)) {
            throw new InvalidInputException("An account with this email already exists");
        }
        return users.save(factory.create(spec));
    }

    @Transactional(readOnly = true)
    public User require(Long id) {
        return users.findById(id).orElseThrow(() -> new ResourceNotFoundException("User", id));
    }

    @Transactional(readOnly = true)
    public Technician requireTechnician(Long id) {
        if (require(id) instanceof Technician t) {
            return t;
        }
        throw new InvalidInputException("User " + id + " is not a technician");
    }

    @Transactional(readOnly = true)
    public List<UserView> listAll() {
        Map<Long, Integer> load = activeLoad();
        return users.findAllOrdered().stream()
                .map(u -> UserView.of(u, u instanceof Technician ? load.getOrDefault(u.getId(), 0) : null))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<UserView> listTechnicians() {
        Map<Long, Integer> load = activeLoad();
        return users.findActiveTechnicians().stream()
                .map(t -> UserView.of(t, load.getOrDefault(t.getId(), 0)))
                .toList();
    }

    /** Active incident count per technician id. */
    @Transactional(readOnly = true)
    public Map<Long, Integer> activeLoad() {
        return incidents.countActiveByAssignee().stream()
                .collect(Collectors.toMap(row -> (Long) row[0], row -> ((Number) row[1]).intValue()));
    }

    public Technician updateTechnician(Long id, String team, List<String> skills, int maxConcurrent,
                                       int experienceYears, boolean available) {
        Technician t = requireTechnician(id);
        try {
            t.updateProfile(team, skills, maxConcurrent, experienceYears);
        } catch (IllegalArgumentException ex) {
            throw new InvalidInputException(ex.getMessage());
        }
        t.setAvailable(available);
        return t;
    }

    public Technician setAvailability(Technician t, boolean available) {
        t.setAvailable(available);
        return t;
    }

    public User setActive(Long id, boolean active) {
        User u = require(id);
        if (active) {
            u.activate();
        } else {
            u.deactivate();
        }
        return u;
    }

    public User updatePreferences(User user, boolean email) {
        User managed = require(user.getId());
        managed.updateNotificationPreferences(email);
        return managed;
    }
}
