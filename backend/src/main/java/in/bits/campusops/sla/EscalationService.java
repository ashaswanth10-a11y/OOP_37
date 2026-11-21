package in.bits.campusops.sla;

import in.bits.campusops.common.TimeProvider;
import in.bits.campusops.events.IncidentEventPublisher;
import in.bits.campusops.events.IncidentEventType;
import in.bits.campusops.incident.Incident;
import in.bits.campusops.incident.IncidentStatus;
import in.bits.campusops.user.Administrator;
import in.bits.campusops.user.Role;
import in.bits.campusops.user.TeamLead;
import in.bits.campusops.user.User;
import in.bits.campusops.user.UserRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;

/**
 * Automatic escalation: Technician -> Team Lead -> System Administrator -> IT Manager.
 * An urgent incident (P1/P2) that nobody acknowledges climbs one level per response window.
 * Any incident that breaches its resolution target is escalated to at least the team lead,
 * and further the longer it stays overdue.
 */
@Service
public class EscalationService {

    public static final List<String> CHAIN = List.of("Technician", "Team Lead", "System Administrator", "IT Manager");

    private static final Set<IncidentStatus> UNACKNOWLEDGED =
            EnumSet.of(IncidentStatus.CLASSIFIED, IncidentStatus.ASSIGNED, IncidentStatus.REOPENED);

    private final UserRepository users;
    private final IncidentEventPublisher events;
    private final TimeProvider time;

    public EscalationService(UserRepository users, IncidentEventPublisher events, TimeProvider time) {
        this.users = users;
        this.events = events;
        this.time = time;
    }

    /** Returns true if the incident was escalated. */
    public boolean check(Incident incident) {
        var sla = incident.getSla();
        if (!sla.isStarted() || incident.getPriority() == null) {
            return false;
        }
        Instant now = time.now();
        int target = targetLevel(incident, now);
        if (target <= incident.getEscalationLevel()) {
            return false;
        }
        User to = recipientFor(target, incident);
        if (to == null) {
            return false;
        }
        incident.escalate(target, to, now);
        String reason = UNACKNOWLEDGED.contains(incident.getStatus())
                ? "not acknowledged within " + Duration.between(sla.getSlaStart(), sla.getResponseDue()).toMinutes()
                + " min response target"
                : "resolution target missed";
        events.publish(IncidentEventType.ESCALATED, incident, null,
                "Escalated to " + CHAIN.get(target) + " " + to.getCode() + " " + to.getFullName() + " (level " + target
                        + "): " + reason);
        return true;
    }

    int targetLevel(Incident incident, Instant now) {
        var sla = incident.getSla();
        int level = 0;
        if (incident.getPriority().isUrgent() && UNACKNOWLEDGED.contains(incident.getStatus())) {
            Duration step = Duration.between(sla.getSlaStart(), sla.getResponseDue());
            Instant base = incident.getAssignedAt() != null && incident.getStatus() == IncidentStatus.ASSIGNED
                    ? max(incident.getAssignedAt(), sla.getSlaStart()) : sla.getSlaStart();
            long steps = step.isZero() ? 0 : Duration.between(base, now).toMillis() / step.toMillis();
            level = (int) Math.min(3, steps);
        }
        if (sla.isResolutionBreached() && sla.getResolvedAt() == null) {
            Duration window = Duration.between(sla.getSlaStart(), sla.getResolutionDue());
            long overdue = Duration.between(sla.getResolutionDue(), now).toMillis();
            int byOverdue = 1 + (int) Math.min(2, window.isZero() ? 0 : overdue / Math.max(1, window.toMillis() / 2));
            level = Math.max(level, byOverdue);
        }
        return Math.min(3, level);
    }

    private User recipientFor(int level, Incident incident) {
        return switch (level) {
            case 1 -> {
                String team = incident.getAssignee() == null ? null : incident.getAssignee().getTeam();
                List<TeamLead> leads = users.findActiveTechnicians().stream()
                        .filter(t -> t instanceof TeamLead).map(t -> (TeamLead) t).toList();
                yield leads.stream().filter(l -> team != null && team.equals(l.getTeam())).findFirst()
                        .orElse(leads.isEmpty() ? null : leads.get(0));
            }
            case 2 -> admins(Role.SYSTEM_ADMIN);
            case 3 -> admins(Role.IT_MANAGER);
            default -> null;
        };
    }

    private User admins(Role role) {
        return users.findActiveAdministrators().stream().filter(a -> a.getRole() == role).findFirst()
                .map(a -> (Administrator) a).orElse(null);
    }

    private static Instant max(Instant a, Instant b) {
        return a.isAfter(b) ? a : b;
    }
}
