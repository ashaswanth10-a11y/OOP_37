package in.bits.campusops.incident;

import jakarta.persistence.criteria.Predicate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.jpa.domain.Specification;

/** Builds the JPA query for {@link IncidentSearch}. */
public final class IncidentSpecifications {

    private static final ZoneId CAMPUS = ZoneId.of("Asia/Kolkata");

    private IncidentSpecifications() {
    }

    public static Specification<Incident> matching(IncidentSearch s) {
        return (root, query, cb) -> {
            List<Predicate> p = new ArrayList<>();
            if (s.q() != null && !s.q().isBlank()) {
                String like = "%" + s.q().trim().toLowerCase() + "%";
                p.add(cb.or(
                        cb.like(cb.lower(root.get("number")), like),
                        cb.like(cb.lower(root.get("title")), like),
                        cb.like(cb.lower(root.get("description")), like)));
            }
            if (s.status() != null && !s.status().isEmpty()) {
                p.add(root.get("status").in(s.status()));
            }
            if (Boolean.TRUE.equals(s.openOnly())) {
                p.add(root.get("status").in(IncidentStatus.OPEN));
            }
            if (s.priority() != null && !s.priority().isEmpty()) {
                p.add(root.get("priority").in(s.priority()));
            }
            if (s.categoryId() != null) {
                p.add(cb.equal(root.get("category").get("id"), s.categoryId()));
            }
            if (s.serviceId() != null) {
                p.add(cb.equal(root.get("service").get("id"), s.serviceId()));
            }
            if (s.assigneeId() != null) {
                p.add(cb.equal(root.get("assignee").get("id"), s.assigneeId()));
            }
            if (s.reporterId() != null) {
                p.add(cb.equal(root.get("reporter").get("id"), s.reporterId()));
            }
            if (s.slaState() != null) {
                p.add(cb.equal(root.get("sla").get("slaState"), s.slaState()));
            }
            if (s.from() != null) {
                p.add(cb.greaterThanOrEqualTo(root.get("createdAt"), s.from().atStartOfDay(CAMPUS).toInstant()));
            }
            if (s.to() != null) {
                p.add(cb.lessThan(root.get("createdAt"), s.to().plusDays(1).atStartOfDay(CAMPUS).toInstant()));
            }
            if (Boolean.TRUE.equals(s.escalated())) {
                p.add(cb.greaterThan(root.get("escalationLevel"), 0));
            }
            if (Boolean.TRUE.equals(s.unassigned())) {
                p.add(cb.isNull(root.get("assignee")));
            }
            if (s.problemId() != null) {
                p.add(cb.equal(root.get("problem").get("id"), s.problemId()));
            }
            return cb.and(p.toArray(Predicate[]::new));
        };
    }
}
