package in.bits.campusops.notification;

import in.bits.campusops.incident.Incident;
import in.bits.campusops.user.User;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private final NotificationChannelFactory factory;

    public NotificationService(NotificationChannelFactory factory) {
        this.factory = factory;
    }

    public void notify(User recipient, String title, String body, Incident incident, Notification.Severity severity) {
        if (recipient == null || !recipient.isActive()) {
            return;
        }
        NotificationChannel.Message message = new NotificationChannel.Message(recipient, title, body,
                incident == null ? null : incident.getId(), incident == null ? null : incident.getNumber(), severity);
        for (NotificationChannel channel : factory.channelsFor(recipient)) {
            try {
                channel.send(message);
            } catch (RuntimeException ex) {
                // a failing channel (network down, bot blocked) must never break the incident workflow
                log.warn("{} notification to {} failed: {}", channel.type(), recipient.getCode(), ex.getMessage());
            }
        }
    }

    /** Notifies each distinct user once, even if they appear twice (e.g. reporter is also an admin). */
    public void notifyAll(Collection<? extends User> recipients, String title, String body, Incident incident,
                          Notification.Severity severity) {
        Map<Long, User> unique = new LinkedHashMap<>();
        recipients.stream().filter(u -> u != null).forEach(u -> unique.putIfAbsent(u.getId(), u));
        unique.values().forEach(u -> notify(u, title, body, incident, severity));
    }
}
