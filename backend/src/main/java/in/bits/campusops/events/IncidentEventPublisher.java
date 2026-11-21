package in.bits.campusops.events;

import in.bits.campusops.common.TimeProvider;
import in.bits.campusops.incident.Incident;
import in.bits.campusops.incident.IncidentStatus;
import in.bits.campusops.user.User;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/**
 * Subject side of the Observer pattern. Services call this; they never know which
 * observers (audit, notifications, live dashboard) are listening.
 */
@Component
public class IncidentEventPublisher {

    private final ApplicationEventPublisher publisher;
    private final TimeProvider time;

    public IncidentEventPublisher(ApplicationEventPublisher publisher, TimeProvider time) {
        this.publisher = publisher;
        this.time = time;
    }

    public void publish(IncidentEventType type, Incident incident, User actor, String description) {
        publish(type, incident, actor, incident.getStatus(), incident.getStatus(), description);
    }

    public void publish(IncidentEventType type, Incident incident, User actor, IncidentStatus from,
                        IncidentStatus to, String description) {
        publisher.publishEvent(new IncidentEvent(type, incident, actor, from, to, description, time.now()));
    }
}
