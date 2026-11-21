package in.bits.campusops.realtime;

import in.bits.campusops.events.IncidentEvent;
import in.bits.campusops.user.CurrentUser;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
public class RealtimeController {

    private final SseHub hub;
    private final CurrentUser currentUser;

    public RealtimeController(SseHub hub, CurrentUser currentUser) {
        this.hub = hub;
        this.currentUser = currentUser;
    }

    @GetMapping(path = "/api/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream() {
        return hub.subscribe(currentUser.id());
    }

    /** Observer: after a change is committed, tell every open dashboard to refresh. */
    @Component
    public static class RealtimeListener {
        private final SseHub hub;

        public RealtimeListener(SseHub hub) {
            this.hub = hub;
        }

        @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
        public void on(IncidentEvent event) {
            hub.broadcast("incident", Map.of(
                    "id", event.incident().getId(),
                    "number", event.incident().getNumber(),
                    "type", event.type().name(),
                    "status", event.incident().getStatus().name()));
        }
    }
}
