package in.bits.campusops.common;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.function.Supplier;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Single source of "now" for the whole system. Wrapping the clock lets tests pin
 * time and lets the demo fast-forward SLA clocks without waiting real minutes.
 */
@Component
public class TimeProvider implements DemoResettable {

    private final Clock clock;
    private volatile Duration offset = Duration.ZERO;
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
        return p != null ? p : clock.instant().plus(offset);
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

    public void advance(Duration by) {
        if (by.isNegative()) {
            throw new Exceptions.InvalidInputException("Time can only move forward");
        }
        offset = offset.plus(by);
    }

    public Duration offset() {
        return offset;
    }

    /** Back to real time. */
    @Override
    public void resetForDemo() {
        offset = Duration.ZERO;
    }
}
