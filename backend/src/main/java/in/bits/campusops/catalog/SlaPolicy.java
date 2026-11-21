package in.bits.campusops.catalog;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Duration;

/**
 * Response and resolution targets for one priority. A policy with no service is the
 * campus-wide default; a policy tied to a service overrides it (e.g. tighter for ERP).
 */
@Entity
@Table(uniqueConstraints = @UniqueConstraint(columnNames = {"service_id", "priority"}))
public class SlaPolicy {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    private ItService service;

    @Enumerated(EnumType.STRING)
    private Priority priority;

    private int responseMinutes;
    private int resolutionMinutes;

    protected SlaPolicy() {
    }

    public SlaPolicy(ItService service, Priority priority, int responseMinutes, int resolutionMinutes) {
        this.service = service;
        this.priority = priority;
        update(responseMinutes, resolutionMinutes);
    }

    public void update(int responseMinutes, int resolutionMinutes) {
        if (responseMinutes <= 0 || resolutionMinutes <= 0) {
            throw new IllegalArgumentException("SLA targets must be positive");
        }
        if (resolutionMinutes < responseMinutes) {
            throw new IllegalArgumentException("Resolution target cannot be shorter than response target");
        }
        this.responseMinutes = responseMinutes;
        this.resolutionMinutes = resolutionMinutes;
    }

    public String displayName() {
        return (service == null ? "Campus default" : service.getName()) + " / " + priority;
    }

    public Duration responseTarget() {
        return Duration.ofMinutes(responseMinutes);
    }

    public Duration resolutionTarget() {
        return Duration.ofMinutes(resolutionMinutes);
    }

    public Long getId() {
        return id;
    }

    public ItService getService() {
        return service;
    }

    public Priority getPriority() {
        return priority;
    }

    public int getResponseMinutes() {
        return responseMinutes;
    }

    public int getResolutionMinutes() {
        return resolutionMinutes;
    }
}
