package in.bits.campusops.user;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import java.time.Instant;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/** A support engineer who works on incidents. */
@Entity
@DiscriminatorValue("TECHNICIAN")
public class Technician extends User {

    /** Incident category codes this technician can handle, e.g. NETWORK, DATABASE. */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "technician_skills", joinColumns = @JoinColumn(name = "technician_id"))
    @Column(name = "category_code")
    private Set<String> skills = new HashSet<>();

    private int maxConcurrent = 5;
    private int experienceYears;
    private boolean available = true;
    /** Support team, e.g. "Network Ops". A team lead leads the technicians of their team. */
    private String team;

    protected Technician() {
    }

    public Technician(String code, String fullName, String email, String passwordHash, Instant createdAt,
                      String team, Collection<String> skills, int maxConcurrent, int experienceYears) {
        super(code, fullName, email, passwordHash, createdAt);
        this.team = team;
        this.skills = new HashSet<>(skills);
        this.maxConcurrent = maxConcurrent;
        this.experienceYears = experienceYears;
    }

    @Override
    public Role getRole() {
        return Role.TECHNICIAN;
    }

    public boolean hasSkill(String categoryCode) {
        return skills.contains(categoryCode);
    }

    /** Whether this technician can accept one more incident given their current active count. */
    public boolean canTakeMore(int activeIncidents) {
        return isActive() && available && activeIncidents < maxConcurrent;
    }

    public void updateProfile(String team, Collection<String> skills, int maxConcurrent, int experienceYears) {
        if (maxConcurrent < 1) {
            throw new IllegalArgumentException("A technician must be able to take at least one incident");
        }
        this.team = team;
        this.skills = new HashSet<>(skills);
        this.maxConcurrent = maxConcurrent;
        this.experienceYears = experienceYears;
    }

    public void setAvailable(boolean available) {
        this.available = available;
    }

    public Set<String> getSkills() {
        return Collections.unmodifiableSet(skills);
    }

    public int getMaxConcurrent() {
        return maxConcurrent;
    }

    public int getExperienceYears() {
        return experienceYears;
    }

    public boolean isAvailable() {
        return available;
    }

    public String getTeam() {
        return team;
    }
}
