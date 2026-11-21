package in.bits.campusops.notification;

import in.bits.campusops.user.User;

/**
 * One way of reaching a person. Adding WhatsApp or SMS later means writing one more
 * implementation; nothing that sends notifications changes.
 */
public interface NotificationChannel {

    enum Type { IN_APP, EMAIL, TELEGRAM }

    record Message(User recipient, String title, String body, Long incidentId, String incidentNumber,
                   Notification.Severity severity) {
    }

    Type type();

    /** Whether this channel can and should reach this user (linked account, preferences). */
    boolean supports(User user);

    void send(Message message);
}
