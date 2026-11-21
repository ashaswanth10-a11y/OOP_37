package in.bits.campusops.user;

import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import java.time.Instant;

/** A system administrator: configures services, SLAs, categories and users. */
@Entity
@DiscriminatorValue("ADMIN")
public class Administrator extends User {

    protected Administrator() {
    }

    public Administrator(String code, String fullName, String email, String passwordHash, Instant createdAt) {
        super(code, fullName, email, passwordHash, createdAt);
    }

    @Override
    public Role getRole() {
        return Role.SYSTEM_ADMIN;
    }
}
