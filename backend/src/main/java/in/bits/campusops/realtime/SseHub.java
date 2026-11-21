package in.bits.campusops.realtime;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/** Keeps open server-sent-event streams per user and pushes live updates to them. */
@Component
public class SseHub {

    private final Map<Long, List<SseEmitter>> emitters = new ConcurrentHashMap<>();

    public SseEmitter subscribe(Long userId) {
        SseEmitter emitter = new SseEmitter(30 * 60 * 1000L);
        emitters.computeIfAbsent(userId, k -> new CopyOnWriteArrayList<>()).add(emitter);
        Runnable remove = () -> emitters.getOrDefault(userId, List.of()).remove(emitter);
        emitter.onCompletion(remove);
        emitter.onTimeout(remove);
        emitter.onError(e -> remove.run());
        send(emitter, "hello", Map.of("ok", true));
        return emitter;
    }

    public void toUser(Long userId, String event, Object data) {
        emitters.getOrDefault(userId, List.of()).forEach(e -> send(e, event, data));
    }

    public void broadcast(String event, Object data) {
        emitters.values().forEach(list -> list.forEach(e -> send(e, event, data)));
    }

    public int connections() {
        return emitters.values().stream().mapToInt(List::size).sum();
    }

    /** Keeps proxies from closing idle streams. */
    @Scheduled(fixedRate = 25_000)
    void heartbeat() {
        broadcast("ping", Map.of());
    }

    private void send(SseEmitter emitter, String event, Object data) {
        try {
            emitter.send(SseEmitter.event().name(event).data(data, MediaType.APPLICATION_JSON));
        } catch (IOException | IllegalStateException ex) {
            emitter.completeWithError(ex);
        }
    }
}
