package in.bits.campusops.audit;

import in.bits.campusops.events.IncidentEvent;
import in.bits.campusops.incident.Incident;
import in.bits.campusops.user.User;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/** Observer: writes every incident event to the audit trail, in the same transaction. */
@Component
public class AuditListener {

    private final AuditRepository audit;

    public AuditListener(AuditRepository audit) {
        this.audit = audit;
    }

    @EventListener
    @Order(0)
    public void on(IncidentEvent event) {
        Incident i = event.incident();
        User actor = event.actor();
        audit.save(new AuditRecord(event.at(),
                actor == null ? null : actor.getId(),
                actor == null ? "CampusOps (automatic)" : actor.getFullName(),
                actor == null ? "SYSTEM" : actor.getCode(),
                event.type(), i.getId(), i.getNumber(), event.fromStatus(), event.toStatus(), event.description(),
                i.getPriority() == null ? null : i.getPriority().name(),
                i.getAssignee() == null ? null : i.getAssignee().getFullName(),
                i.getCategory() == null ? null : i.getCategory().getName(),
                i.getSla().getSlaState() == null ? null : i.getSla().getSlaState().name(),
                i.getEscalationLevel(),
                i.getSla().isStarted() ? i.getSla().resolutionUsed(event.at()) : null));
    }
}
