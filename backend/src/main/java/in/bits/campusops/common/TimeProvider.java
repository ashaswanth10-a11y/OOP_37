package in.bits.campusops.common;

import java.time.Clock;
import java.time.Instant;
import java.util.function.Supplier;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/** Supplies server time and supports timestamped seed data and tests. */
@Component
public class TimeProvider {

    private final Clock clock;
    private final ThreadLocal<Instant> pinned = new ThreadLocal<>();

    @Autowired
    public TimeProvider() {
        this(Clock.systemUTC());
    }

    public TimeProvider(Clock clock) {
        this.clock = clock;
    }

    public Instant now() {
        Instant p = pinned.get();
        return p != null ? p : clock.instant();
    }

    /** Runs {@code action} with "now" pinned to {@code when} on this thread (used to seed history). */
    public <T> T at(Instant when, Supplier<T> action) {
        pinned.set(when);
        try {
            return action.get();
        } finally {
            pinned.remove();
        }
    }
}
