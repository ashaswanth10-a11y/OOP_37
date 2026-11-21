package in.bits.campusops.user;

import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import java.time.Instant;
import java.util.Collection;
import java.util.EnumSet;
import java.util.Set;

/** A team lead is a technician who also receives escalations and can reassign work. */
@Entity
@DiscriminatorValue("TEAM_LEAD")
public class TeamLead extends Technician {

    protected TeamLead() {
    }

    public TeamLead(String code, String fullName, String email, String passwordHash, Instant createdAt,
                    String team, Collection<String> skills, int maxConcurrent, int experienceYears) {
        super(code, fullName, email, passwordHash, createdAt, team, skills, maxConcurrent, experienceYears);
    }

    @Override
    public Role getRole() {
        return Role.TEAM_LEAD;
    }

    @Override
    public Set<Role> grantedRoles() {
        return EnumSet.of(Role.TECHNICIAN, Role.TEAM_LEAD);
    }
}
