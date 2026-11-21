package in.bits.campusops.events;

import in.bits.campusops.incident.Incident;
import in.bits.campusops.incident.IncidentStatus;
import in.bits.campusops.user.User;
import java.time.Instant;

/**
 * Something that happened to an incident. Published by the domain service and observed
 * by audit, notifications, live updates and correlation (Observer pattern).
 *
 * @param actor who caused it; null means the system did it automatically
 */
public record IncidentEvent(IncidentEventType type, Incident incident, User actor, IncidentStatus fromStatus,
                            IncidentStatus toStatus, String description, Instant at) {

    public boolean isSystem() {
        return actor == null;
    }
}
