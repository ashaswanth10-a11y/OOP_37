package in.bits.campusops.user;

import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import java.time.Instant;

/** A student, faculty or staff member who reports problems. */
@Entity
@DiscriminatorValue("EMPLOYEE")
public class Employee extends User {

    private String department;
    /** Hostel room, lab or office, used to pre-fill reports. */
    private String location;

    protected Employee() {
    }

    public Employee(String code, String fullName, String email, String passwordHash, Instant createdAt,
                    String department, String location) {
        super(code, fullName, email, passwordHash, createdAt);
        this.department = department;
        this.location = location;
    }

    @Override
    public Role getRole() {
        return Role.EMPLOYEE;
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
