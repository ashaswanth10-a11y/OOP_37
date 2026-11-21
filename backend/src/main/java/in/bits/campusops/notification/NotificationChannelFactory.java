package in.bits.campusops.notification;

import in.bits.campusops.user.User;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/** Factory pattern: hands out the channel objects that apply to a given recipient. */
@Component
public class NotificationChannelFactory {

    private final Map<NotificationChannel.Type, NotificationChannel> byType = new EnumMap<>(NotificationChannel.Type.class);

    public NotificationChannelFactory(List<NotificationChannel> channels) {
        channels.forEach(c -> byType.put(c.type(), c));
    }

    public NotificationChannel create(NotificationChannel.Type type) {
        NotificationChannel channel = byType.get(type);
        if (channel == null) {
            throw new IllegalArgumentException("No channel registered for " + type);
        }
        return channel;
    }

    public List<NotificationChannel> channelsFor(User user) {
        return byType.values().stream().filter(c -> c.supports(user)).toList();
    }
}
