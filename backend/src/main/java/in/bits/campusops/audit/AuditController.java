package in.bits.campusops.audit;

import in.bits.campusops.common.PageResponse;
import java.time.Instant;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/audit")
public class AuditController {

    private final AuditRepository audit;

    public AuditController(AuditRepository audit) {
        this.audit = audit;
    }

    public record AuditView(Long id, Instant at, String actorName, String actorCode, String action,
                            Long incidentId, String incidentNumber, String previousState, String newState,
                            String description, String priority, String assignee, String category,
                            String slaState, int escalationLevel, Double slaUsed) {
        public static AuditView of(AuditRecord r) {
            return new AuditView(r.getId(), r.getAt(), r.getActorName(), r.getActorCode(), r.getAction().name(),
                    r.getIncidentId(), r.getIncidentNumber(),
                    r.getPreviousState() == null ? null : r.getPreviousState().name(),
                    r.getNewState() == null ? null : r.getNewState().name(), r.getDescription(), r.getSnapPriority(),
                    r.getSnapAssignee(), r.getSnapCategory(), r.getSnapSlaState(), r.getSnapEscalationLevel(),
                    r.getSnapSlaUsed());
        }
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('SYSTEM_ADMIN','TEAM_LEAD')")
    public PageResponse<AuditView> all(@RequestParam(defaultValue = "0") int page,
                                       @RequestParam(defaultValue = "50") int size) {
        return PageResponse.of(audit.findAllByOrderByAtDescIdDesc(PageRequest.of(page, Math.min(size, 200))),
                AuditView::of);
    }
}
