package in.bits.campusops.catalog;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * A campus IT service such as "Hostel Wi-Fi (Gautam Bhawan)" or "ERP Portal".
 * {@code dependsOn} forms the service dependency graph used for blast radius
 * and root-cause detection.
 */
@Entity
public class ItService {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 40)
    private String code;

    @Column(nullable = false)
    private String name;

    @Column(length = 500)
    private String description;

    @Enumerated(EnumType.STRING)
    private ServiceType type;

    /** 1 (nice to have) to 5 (campus stops without it). */
    private int criticality;

    private String location;

    /** Rough number of people who use this service; feeds the priority score. */
    private int userBase;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "default_category_id")
    private IncidentCategory defaultCategory;

    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(name = "service_dependencies",
            joinColumns = @JoinColumn(name = "service_id"),
            inverseJoinColumns = @JoinColumn(name = "depends_on_id"))
    private Set<ItService> dependsOn = new HashSet<>();

    /** Layout hint for the outage map (column, row). */
    private int mapX;
    private int mapY;
    private boolean active = true;

    protected ItService() {
    }

    public ItService(String code, String name, String description, ServiceType type, int criticality,
                     String location, int userBase, IncidentCategory defaultCategory, int mapX, int mapY) {
        this.code = code.toUpperCase();
        this.name = name;
        this.description = description;
        this.type = type;
        setCriticality(criticality);
        this.location = location;
        this.userBase = userBase;
        this.defaultCategory = defaultCategory;
        this.mapX = mapX;
        this.mapY = mapY;
    }

    public void update(String name, String description, ServiceType type, int criticality, String location,
                       int userBase, IncidentCategory defaultCategory, boolean active) {
        this.name = name;
        this.description = description;
        this.type = type;
        setCriticality(criticality);
        this.location = location;
        this.userBase = userBase;
        this.defaultCategory = defaultCategory;
        this.active = active;
    }

    private void setCriticality(int criticality) {
        if (criticality < 1 || criticality > 5) {
            throw new IllegalArgumentException("Criticality must be between 1 and 5");
        }
        this.criticality = criticality;
    }

    public void addDependency(ItService upstream) {
        if (upstream == this) {
            throw new IllegalArgumentException("A service cannot depend on itself");
        }
        dependsOn.add(upstream);
    }

    public void replaceDependencies(Set<ItService> upstream) {
        if (upstream.contains(this)) {
            throw new IllegalArgumentException("A service cannot depend on itself");
        }
        dependsOn.clear();
        dependsOn.addAll(upstream);
    }

    public void moveOnMap(int x, int y) {
        this.mapX = x;
        this.mapY = y;
    }

    public Long getId() {
        return id;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public ServiceType getType() {
        return type;
    }

    public int getCriticality() {
        return criticality;
    }

    public String getLocation() {
        return location;
    }

    public int getUserBase() {
        return userBase;
    }

    public IncidentCategory getDefaultCategory() {
        return defaultCategory;
    }

    public Set<ItService> getDependsOn() {
        return Collections.unmodifiableSet(dependsOn);
    }

    public int getMapX() {
        return mapX;
    }

    public int getMapY() {
        return mapY;
    }

    public boolean isActive() {
        return active;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof ItService other && id != null && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return id == null ? System.identityHashCode(this) : id.hashCode();
    }
}
