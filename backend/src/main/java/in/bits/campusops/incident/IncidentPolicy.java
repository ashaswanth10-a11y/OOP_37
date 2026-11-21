package in.bits.campusops.incident;

import in.bits.campusops.common.Exceptions.InvalidStateTransitionException;
import in.bits.campusops.common.Exceptions.UnauthorizedOperationException;
import in.bits.campusops.user.Role;
import in.bits.campusops.user.User;
import java.util.EnumSet;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * Who may do what to an incident. Keeps two questions apart: is this person allowed
 * (role / ownership), and does the incident's current state allow it.
 */
@Component
public class IncidentPolicy {

    public enum Action {
        ACKNOWLEDGE, START, REQUEST_INFO, PROVIDE_INFO, RESOLVE, VERIFY, REOPEN, CLOSE, CANCEL,
        ASSIGN, TAKE, CHANGE_PRIORITY, ADD_NOTE
    }

    public boolean canView(User user, Incident incident) {
        return user.isStaff() || incident.isReportedBy(user) || incident.isSupportedBy(user);
    }

    public void requireView(User user, Incident incident) {
        if (!canView(user, incident)) {
            throw new UnauthorizedOperationException("You can only view incidents you reported or said 'me too' to");
        }
    }

    public Set<Action> allowedActions(User user, Incident incident) {
        Set<Action> allowed = EnumSet.noneOf(Action.class);
        for (Action a : Action.values()) {
            if (roleAllows(user, incident, a) && stateAllows(incident, a)) {
                allowed.add(a);
            }
        }
        return allowed;
    }

    public void require(User user, Incident incident, Action action) {
        if (!roleAllows(user, incident, action)) {
            throw new UnauthorizedOperationException("You are not allowed to " + label(action) + " " + incident.getNumber());
        }
        if (!stateAllows(incident, action)) {
            throw new InvalidStateTransitionException("Cannot " + label(action) + " " + incident.getNumber()
                    + " while it is " + incident.getStatus());
        }
    }

    boolean roleAllows(User u, Incident i, Action a) {
        boolean lead = u.hasRole(Role.TEAM_LEAD);
        boolean admin = u.hasRole(Role.SYSTEM_ADMIN);
        boolean supervisor = lead || admin;
        boolean assignee = i.isAssignedTo(u);
        boolean reporter = i.isReportedBy(u);
        boolean technician = u.hasRole(Role.TECHNICIAN);
        return switch (a) {
            case ACKNOWLEDGE, START, REQUEST_INFO, RESOLVE -> assignee || supervisor;
            case PROVIDE_INFO -> reporter;
            case VERIFY, REOPEN -> reporter || admin;
            case CLOSE -> assignee || supervisor;
            case CANCEL -> reporter || admin;
            case ASSIGN, CHANGE_PRIORITY -> supervisor;
            case TAKE -> technician && !assignee;
            case ADD_NOTE -> reporter || u.isStaff();
        };
    }

    boolean stateAllows(Incident i, Action a) {
        IncidentStatus s = i.getStatus();
        return switch (a) {
            case ACKNOWLEDGE -> s == IncidentStatus.ASSIGNED;
            case START -> s == IncidentStatus.ACKNOWLEDGED || s == IncidentStatus.AWAITING_INFO;
            case REQUEST_INFO -> s == IncidentStatus.IN_PROGRESS;
            case PROVIDE_INFO -> s == IncidentStatus.AWAITING_INFO;
            case RESOLVE -> s == IncidentStatus.IN_PROGRESS || s == IncidentStatus.AWAITING_INFO;
            case VERIFY, REOPEN -> s == IncidentStatus.RESOLVED;
            case CLOSE -> s == IncidentStatus.VERIFIED;
            case CANCEL -> s == IncidentStatus.NEW || s == IncidentStatus.CLASSIFIED;
            case ASSIGN -> i.state().allowsAssignment();
            case TAKE -> s == IncidentStatus.CLASSIFIED || s == IncidentStatus.REOPENED;
            case CHANGE_PRIORITY -> i.isOpen();
            case ADD_NOTE -> s != IncidentStatus.CLOSED && s != IncidentStatus.CANCELLED;
        };
    }

    private static String label(Action a) {
        return a.name().toLowerCase().replace('_', ' ');
    }
}
