package in.bits.campusops.user;

/** Roles in escalation order: a higher tier is the next person a stuck incident goes to. */
public enum Role {
    STUDENT(0, "Student"),
    TECHNICIAN(1, "Technician"),
    TEAM_LEAD(2, "Team Lead"),
    SYSTEM_ADMIN(3, "System Administrator"),
    IT_MANAGER(4, "IT Manager");

    private final int tier;
    private final String label;

    Role(int tier, String label) {
        this.tier = tier;
        this.label = label;
    }

    public int tier() {
        return tier;
    }

    public String label() {
        return label;
    }

    public String authority() {
        return "ROLE_" + name();
    }
}
