package in.bits.campusops.incident;

import in.bits.campusops.catalog.Priority;
import java.time.LocalDate;
import java.util.List;

/**
 * Search criteria. Every field is optional:
 * text/number, status, priority, category, service, technician, SLA state, date range,
 * reporter, escalated only, problem.
 */
public record IncidentSearch(String q, List<IncidentStatus> status, List<Priority> priority, Long categoryId,
                             Long serviceId, Long assigneeId, SlaState slaState, LocalDate from, LocalDate to,
                             Long reporterId, Boolean escalated, Long problemId, Boolean openOnly,
                             Boolean unassigned) {

    public IncidentSearch forReporter(Long reporter) {
        return new IncidentSearch(q, status, priority, categoryId, serviceId, assigneeId, slaState, from, to, reporter,
                escalated, problemId, openOnly, unassigned);
    }
}
