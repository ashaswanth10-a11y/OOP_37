package in.bits.campusops.notification;

import in.bits.campusops.events.IncidentEvent;
import in.bits.campusops.incident.Incident;
import in.bits.campusops.incident.IncidentStatus;
import in.bits.campusops.notification.Notification.Severity;
import in.bits.campusops.user.Role;
import in.bits.campusops.user.TeamLead;
import in.bits.campusops.user.User;
import in.bits.campusops.user.UserRepository;
import java.util.ArrayList;
import java.util.List;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/** Observer: decides who hears about each incident event and what they are told. */
@Component
public class NotificationListener {

    private final NotificationService notifications;
    private final UserRepository users;

    public NotificationListener(NotificationService notifications, UserRepository users) {
        this.notifications = notifications;
        this.users = users;
    }

    @EventListener
    @Order(10)
    public void on(IncidentEvent e) {
        Incident i = e.incident();
        String n = i.getNumber();
        User reporter = i.getReporter();
        User assignee = i.getAssignee();
        switch (e.type()) {
            case CREATED -> notifications.notify(reporter, n + " logged",
                    "We received your report \"" + i.getTitle() + "\". You can track it in CampusOps.", i, Severity.INFO);
            case ASSIGNED -> {
                notifications.notify(assignee, "New incident " + n + " (" + i.getPriority() + ")",
                        i.getTitle() + " on " + i.getService().getName() + ". " + e.description(), i,
                        i.getPriority() != null && i.getPriority().isUrgent() ? Severity.CRITICAL : Severity.INFO);
                if (!e.isSystem() || i.getStatus() == IncidentStatus.ASSIGNED) {
                    notifications.notify(reporter, n + " assigned",
                            assignee.getFullName() + " is looking after your incident.", i, Severity.INFO);
                }
            }
            case ASSIGNMENT_FAILED -> notifications.notifyAll(leads(), n + " is waiting in the queue",
                    "Nobody with capacity could take it automatically. " + e.description(), i, Severity.WARNING);
            case PRIORITY_CHANGED -> notifications.notifyAll(List.of(reporter, assigneeOrNull(i)),
                    n + " priority changed", e.description(), i, Severity.WARNING);
            case INFO_REQUESTED -> notifications.notify(reporter, "We need more information on " + n,
                    e.description(), i, Severity.WARNING);
            case INFO_PROVIDED -> notifications.notify(assignee, "Reporter replied on " + n, e.description(), i,
                    Severity.INFO);
            case RESOLVED -> notifications.notify(reporter, n + " resolved",
                    "Fix: " + i.getResolution().getResolutionSummary()
                            + " Please confirm it works and rate the support.", i, Severity.SUCCESS);
            case REOPENED -> notifications.notify(assignee, n + " reopened", e.description(), i, Severity.WARNING);
            case CLOSED -> notifications.notify(reporter, n + " closed", "Thanks for reporting. This incident is closed.",
                    i, Severity.SUCCESS);
            case SLA_WARNING -> notifications.notifyAll(List.of(assigneeOrNull(i), teamLeadOf(i)),
                    "SLA warning on " + n, e.description(), i, Severity.WARNING);
            case SLA_BREACHED -> {
                List<User> to = new ArrayList<>(List.of(teamLeadOf(i)));
                if (assignee != null) {
                    to.add(assignee);
                }
                to.addAll(admins());
                notifications.notifyAll(to, "SLA breached on " + n, e.description(), i, Severity.CRITICAL);
            }
            case ESCALATED -> notifications.notify(i.getEscalatedTo(), "Escalated to you: " + n,
                    e.description(), i, Severity.CRITICAL);
            case DUPLICATE_DETECTED -> notifications.notify(assigneeOrNull(i), n + " may be a duplicate",
                    e.description(), i, Severity.INFO);
            case PROBLEM_LINKED -> {
                if (e.description() != null && e.description().startsWith("New problem")) {
                    List<User> to = new ArrayList<>(leads());
                    to.addAll(admins());
                    notifications.notifyAll(to, "Possible root cause found", e.description(), i, Severity.CRITICAL);
                }
            }
            default -> {
                // other events are recorded in the audit trail only
            }
        }
    }

    private User assigneeOrNull(Incident i) {
        return i.getAssignee();
    }

    private User teamLeadOf(Incident i) {
        String team = i.getAssignee() == null ? null : i.getAssignee().getTeam();
        List<TeamLead> leads = leads();
        return leads.stream().filter(l -> team != null && team.equals(l.getTeam())).findFirst()
                .orElse(leads.isEmpty() ? null : leads.get(0));
    }

    private List<TeamLead> leads() {
        return users.findActiveTechnicians().stream().filter(t -> t instanceof TeamLead).map(t -> (TeamLead) t)
                .toList();
    }

    private List<User> admins() {
        return users.findActiveAdministrators().stream().filter(a -> a.getRole() == Role.SYSTEM_ADMIN)
                .map(a -> (User) a).toList();
    }
}
