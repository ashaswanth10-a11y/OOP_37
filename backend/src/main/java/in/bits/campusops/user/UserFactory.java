package in.bits.campusops.user;

import in.bits.campusops.common.Exceptions.InvalidInputException;
import in.bits.campusops.common.TimeProvider;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Factory pattern: the only place that decides which concrete {@link User} subclass to
 * build for a role, hashes the password and hands out the human-readable code (S101, T201...).
 */
@Component
public class UserFactory {

    private final PasswordEncoder passwordEncoder;
    private final TimeProvider time;
    private final UserRepository users;
    private final AtomicInteger sequence = new AtomicInteger(-1);

    public UserFactory(PasswordEncoder passwordEncoder, TimeProvider time, UserRepository users) {
        this.passwordEncoder = passwordEncoder;
        this.time = time;
        this.users = users;
    }

    public record Spec(Role role, String fullName, String email, String rawPassword,
                       String department, String location, String team, List<String> skills,
                       Integer maxConcurrent, Integer experienceYears) {
    }

    public User create(Spec spec) {
        if (spec.rawPassword() == null || spec.rawPassword().length() < 8) {
            throw new InvalidInputException("Password must be at least 8 characters");
        }
        String hash = passwordEncoder.encode(spec.rawPassword());
        Instant now = time.now();
        String code = nextCode(spec.role());
        List<String> skills = spec.skills() == null ? List.of() : spec.skills();
        int max = spec.maxConcurrent() == null ? 5 : spec.maxConcurrent();
        int exp = spec.experienceYears() == null ? 1 : spec.experienceYears();
        return switch (spec.role()) {
            case STUDENT, FACULTY, EMPLOYEE -> new CampusReporter(spec.role(), code, spec.fullName(), spec.email(), hash, now,
                    spec.department(), spec.location());
            case TECHNICIAN -> new Technician(code, spec.fullName(), spec.email(), hash, now,
                    spec.team(), skills, max, exp);
            case TEAM_LEAD -> new TeamLead(code, spec.fullName(), spec.email(), hash, now,
                    spec.team(), skills, max, exp);
            case SYSTEM_ADMIN -> new Administrator(code, spec.fullName(), spec.email(), hash, now);
            case IT_MANAGER -> new ItManager(code, spec.fullName(), spec.email(), hash, now);
        };
    }

    private String nextCode(Role role) {
        sequence.compareAndSet(-1, (int) users.count());
        String prefix = switch (role) {
            case STUDENT -> "S";
            case FACULTY -> "F";
            case EMPLOYEE -> "E";
            case TECHNICIAN -> "T";
            case TEAM_LEAD -> "L";
            case SYSTEM_ADMIN -> "A";
            case IT_MANAGER -> "M";
        };
        return prefix + (101 + sequence.getAndIncrement());
    }
}
