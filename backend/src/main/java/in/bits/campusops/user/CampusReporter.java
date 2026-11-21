package in.bits.campusops.user;

import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import java.time.Instant;

/** A student, faculty member or employee who uses the campus help desk. */
@Entity
// Keep the discriminator used by existing CampusOps databases.
@DiscriminatorValue("EMPLOYEE")
public class CampusReporter extends User {

    @Enumerated(EnumType.STRING)
    private Role campusRole;

    private String department;
    /** Hostel room, lab or office, used to pre-fill reports. */
    private String location;

    protected CampusReporter() {
    }

    public CampusReporter(Role campusRole, String code, String fullName, String email, String passwordHash, Instant createdAt,
                    String department, String location) {
        super(code, fullName, email, passwordHash, createdAt);
        if (!campusRole.isCampusReporter()) {
            throw new IllegalArgumentException("A campus reporting role is required");
        }
        this.campusRole = campusRole;
        this.department = department;
        this.location = location;
    }

    @Override
    public Role getRole() {
        return campusRole == null ? Role.STUDENT : campusRole;
    }

    public void moveTo(String department, String location) {
        this.department = department;
        this.location = location;
    }

    public String getDepartment() {
        return department;
    }

    public String getLocation() {
        return location;
    }
}
