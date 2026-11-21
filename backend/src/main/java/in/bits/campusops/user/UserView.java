package in.bits.campusops.user;

import java.util.List;
import java.util.Set;

/** What the API exposes about a user. Never includes the password hash. */
public record UserView(Long id, String code, String fullName, String email, Role role, Set<Role> roles,
                       String department, String location, String team, List<String> skills,
                       Integer maxConcurrent, Integer experienceYears, Boolean available,
                       Integer activeIncidents, boolean telegramLinked, boolean emailNotifications,
                       boolean telegramNotifications, boolean active) {

    public static UserView of(User u) {
        return of(u, null);
    }

    public static UserView of(User u, Integer activeIncidents) {
        String department = null;
        String location = null;
        String team = null;
        List<String> skills = null;
        Integer max = null;
        Integer exp = null;
        Boolean available = null;
        if (u instanceof Employee e) {
            department = e.getDepartment();
            location = e.getLocation();
        }
        if (u instanceof Technician t) {
            team = t.getTeam();
            skills = t.getSkills().stream().sorted().toList();
            max = t.getMaxConcurrent();
            exp = t.getExperienceYears();
            available = t.isAvailable();
        }
        return new UserView(u.getId(), u.getCode(), u.getFullName(), u.getEmail(), u.getRole(), u.grantedRoles(),
                department, location, team, skills, max, exp, available, activeIncidents, u.isTelegramLinked(),
                u.isEmailNotifications(), u.isTelegramNotifications(), u.isActive());
    }
}
