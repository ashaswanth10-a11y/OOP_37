package in.bits.campusops.incident;

import in.bits.campusops.catalog.Asset;
import in.bits.campusops.catalog.ItService;
import in.bits.campusops.catalog.Level;
import in.bits.campusops.common.Exceptions.InvalidInputException;
import in.bits.campusops.common.TimeProvider;
import in.bits.campusops.user.User;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.stereotype.Component;

/**
 * Factory pattern: validates raw report input and builds a NEW incident with the next
 * human-readable number (INC-1001, INC-1002...).
 */
@Component
public class IncidentFactory implements in.bits.campusops.common.DemoResettable {

    private final IncidentRepository incidents;
    private final TimeProvider time;
    private final AtomicInteger sequence = new AtomicInteger(-1);

    public IncidentFactory(IncidentRepository incidents, TimeProvider time) {
        this.incidents = incidents;
        this.time = time;
    }

    public Incident create(User reporter, String title, String description, ItService service, Asset asset,
                           String location, Level impact, Level urgency, Integer affectedUsers,
                           Incident.Channel channel) {
        if (title == null || title.trim().length() < 5) {
            throw new InvalidInputException("Give the problem a short title (at least 5 characters)");
        }
        if (description == null || description.trim().length() < 10) {
            throw new InvalidInputException("Describe the problem in at least 10 characters");
        }
        if (title.length() > 160) {
            throw new InvalidInputException("Keep the title under 160 characters");
        }
        int users = affectedUsers == null ? 1 : affectedUsers;
        if (users < 1 || users > 100_000) {
            throw new InvalidInputException("Affected users must be between 1 and 100000");
        }
        return new Incident(nextSequence(), reporter, title, description, service, asset,
                location, impact == null ? Level.MEDIUM : impact, urgency == null ? Level.MEDIUM : urgency,
                users, channel == null ? Incident.Channel.WEB : channel, time.now());
    }

    @Override
    public synchronized void resetForDemo() {
        sequence.set(-1);
    }

    private synchronized int nextSequence() {
        if (sequence.get() < 0) {
            sequence.set(incidents.maxSequence());
        }
        return sequence.incrementAndGet();
    }
}
