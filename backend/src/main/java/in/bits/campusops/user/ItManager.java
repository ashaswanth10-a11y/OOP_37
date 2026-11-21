package in.bits.campusops.user;

import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import java.time.Instant;
import java.util.EnumSet;
import java.util.Set;

/** The IT manager is an administrator at the top of the escalation chain. */
@Entity
@DiscriminatorValue("IT_MANAGER")
public class ItManager extends Administrator {

    protected ItManager() {
    }

    public ItManager(String code, String fullName, String email, String passwordHash, Instant createdAt) {
        super(code, fullName, email, passwordHash, createdAt);
    }

    @Override
    public Role getRole() {
        return Role.IT_MANAGER;
    }

    @Override
    public Set<Role> grantedRoles() {
        return EnumSet.of(Role.SYSTEM_ADMIN, Role.IT_MANAGER);
    }
}
