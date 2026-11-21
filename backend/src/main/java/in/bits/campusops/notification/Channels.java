package in.bits.campusops.notification;

import in.bits.campusops.common.TimeProvider;
import in.bits.campusops.notification.NotificationRepositories.EmailOutboxRepository;
import in.bits.campusops.notification.NotificationRepositories.NotificationRepository;
import in.bits.campusops.realtime.SseHub;
import in.bits.campusops.telegram.TelegramAdapter;
import in.bits.campusops.user.User;
import java.util.Map;
import org.springframework.stereotype.Component;

/** Concrete notification channels. */
public final class Channels {

    private Channels() {
    }

    /** Stored notification plus a live push to any open browser tab. Always on. */
    @Component
    public static class InAppChannel implements NotificationChannel {
        private final NotificationRepository repository;
        private final SseHub hub;
        private final TimeProvider time;

        public InAppChannel(NotificationRepository repository, SseHub hub, TimeProvider time) {
            this.repository = repository;
            this.hub = hub;
            this.time = time;
        }

        @Override
        public Type type() {
            return Type.IN_APP;
        }

        @Override
        public boolean supports(User user) {
            return true;
        }

        @Override
        public void send(Message m) {
            Notification n = repository.save(new Notification(m.recipient(), m.title(), m.body(), m.incidentId(),
                    m.incidentNumber(), m.severity(), time.now()));
            hub.toUser(m.recipient().getId(), "notification", Map.of(
                    "id", n.getId(), "title", n.getTitle(), "body", n.getBody(),
                    "severity", n.getSeverity().name(),
                    "incidentId", n.getIncidentId() == null ? "" : n.getIncidentId()));
        }
    }

    /** Email through the {@link EmailGateway} adapter (simulated outbox by default). */
    @Component
    public static class EmailChannel implements NotificationChannel {
        private final EmailGateway gateway;

        public EmailChannel(EmailGateway gateway) {
            this.gateway = gateway;
        }

        @Override
        public Type type() {
            return Type.EMAIL;
        }

        @Override
        public boolean supports(User user) {
            return user.isEmailNotifications();
        }

        @Override
        public void send(Message m) {
            String body = m.body() + (m.incidentNumber() == null ? "" : "\n\nIncident: " + m.incidentNumber())
                    + "\n\n- CampusOps, IT Services";
            gateway.send(m.recipient().getEmail(), "[CampusOps] " + m.title(), body);
        }
    }

    /** Telegram through the bot adapter. Only for users who linked their account. */
    @Component
    public static class TelegramChannel implements NotificationChannel {
        private final TelegramAdapter telegram;

        public TelegramChannel(TelegramAdapter telegram) {
            this.telegram = telegram;
        }

        @Override
        public Type type() {
            return Type.TELEGRAM;
        }

        @Override
        public boolean supports(User user) {
            return telegram.isEnabled() && user.isTelegramLinked() && user.isTelegramNotifications();
        }

        @Override
        public void send(Message m) {
            String icon = switch (m.severity()) {
                case CRITICAL -> "🔴";
                case WARNING -> "🟠";
                case SUCCESS -> "✅";
                case INFO -> "🔵";
            };
            telegram.sendMessage(m.recipient().getTelegramChatId(),
                    icon + " " + m.title() + "\n" + m.body());
        }
    }

    /** Adapter contract for outgoing email; swap in SMTP or SendGrid without touching channels. */
    public interface EmailGateway {
        void send(String to, String subject, String body);
    }

    @Component
    public static class SimulatedEmailGateway implements EmailGateway {
        private final EmailOutboxRepository outbox;
        private final TimeProvider time;

        public SimulatedEmailGateway(EmailOutboxRepository outbox, TimeProvider time) {
            this.outbox = outbox;
            this.time = time;
        }

        @Override
        public void send(String to, String subject, String body) {
            outbox.save(new EmailOutboxMessage(to, subject, body, time.now()));
        }
    }
}
