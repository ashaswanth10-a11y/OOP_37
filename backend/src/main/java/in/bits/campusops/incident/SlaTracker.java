package in.bits.campusops.incident;

import in.bits.campusops.catalog.SlaPolicy;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * The SLA clock of one incident (composition: it lives and dies with its incident).
 * It owns the deadline arithmetic so no other class has to know how breaches work.
 */
@Embeddable
public class SlaTracker {

    public enum Signal { RESPONSE_WARNING, RESPONSE_BREACHED, RESOLUTION_WARNING, RESOLUTION_BREACHED }

    private String slaPolicyName;
    private Instant slaStart;
    private Instant responseDue;
    private Instant resolutionDue;
    private Instant respondedAt;
    private Instant resolvedAt;
    private boolean responseWarned;
    private boolean resolutionWarned;
    private boolean responseBreached;
    private boolean resolutionBreached;

    @Enumerated(EnumType.STRING)
    private SlaState slaState = SlaState.ON_TRACK;

    /** Starts (or restarts after a priority change) the clock from {@code from}. */
    public void start(SlaPolicy policy, Instant from) {
        this.slaPolicyName = policy.displayName();
        this.slaStart = from;
        this.responseDue = from.plus(policy.responseTarget());
        this.resolutionDue = from.plus(policy.resolutionTarget());
        this.responseWarned = false;
        this.resolutionWarned = false;
        refresh();
    }

    public boolean isStarted() {
        return slaStart != null;
    }

    void markResponded(Instant now) {
        if (respondedAt == null) {
            respondedAt = now;
            if (responseDue != null && now.isAfter(responseDue)) {
                responseBreached = true;
            }
        }
        refresh();
    }

    void markResolved(Instant now) {
        markResponded(now);
        resolvedAt = now;
        if (resolutionDue != null && now.isAfter(resolutionDue)) {
            resolutionBreached = true;
        }
        refresh();
    }

    void reopen() {
        resolvedAt = null;
        refresh();
    }

    /**
     * Checks the clock at {@code now} and returns the signals that newly fired.
     * Each warning and breach fires at most once.
     */
    public List<Signal> evaluate(Instant now, int warnPercent) {
        List<Signal> fired = new ArrayList<>();
        if (!isStarted()) {
            return fired;
        }
        if (respondedAt == null) {
            if (!responseBreached && now.isAfter(responseDue)) {
                responseBreached = true;
                fired.add(Signal.RESPONSE_BREACHED);
            } else if (!responseBreached && !responseWarned && fraction(slaStart, responseDue, now) * 100 >= warnPercent) {
                responseWarned = true;
                fired.add(Signal.RESPONSE_WARNING);
            }
        }
        if (resolvedAt == null) {
            if (!resolutionBreached && now.isAfter(resolutionDue)) {
                resolutionBreached = true;
                fired.add(Signal.RESOLUTION_BREACHED);
            } else if (!resolutionBreached && !resolutionWarned
                    && fraction(slaStart, resolutionDue, now) * 100 >= warnPercent) {
                resolutionWarned = true;
                fired.add(Signal.RESOLUTION_WARNING);
            }
        }
        refresh();
        return fired;
    }

    /** How much of the resolution window has been used, 0.0 to 1.0+ (over 1 means breached). */
    public double resolutionUsed(Instant now) {
        if (!isStarted()) {
            return 0;
        }
        Instant end = resolvedAt != null ? resolvedAt : now;
        return fraction(slaStart, resolutionDue, end);
    }

    public Duration resolutionRemaining(Instant now) {
        return resolutionDue == null ? Duration.ZERO : Duration.between(now, resolutionDue);
    }

    private static double fraction(Instant start, Instant due, Instant now) {
        long window = Duration.between(start, due).toMillis();
        if (window <= 0) {
            return 1;
        }
        return (double) Duration.between(start, now).toMillis() / window;
    }

    private void refresh() {
        boolean breached = responseBreached || resolutionBreached;
        if (resolvedAt != null) {
            slaState = breached ? SlaState.BREACHED : SlaState.MET;
        } else if (breached) {
            slaState = SlaState.BREACHED;
        } else if (responseWarned || resolutionWarned) {
            slaState = SlaState.AT_RISK;
        } else {
            slaState = SlaState.ON_TRACK;
        }
    }

    public String getSlaPolicyName() {
        return slaPolicyName;
    }

    public Instant getSlaStart() {
        return slaStart;
    }

    public Instant getResponseDue() {
        return responseDue;
    }

    public Instant getResolutionDue() {
        return resolutionDue;
    }

    public Instant getRespondedAt() {
        return respondedAt;
    }

    public Instant getResolvedAt() {
        return resolvedAt;
    }

    public boolean isResponseBreached() {
        return responseBreached;
    }

    public boolean isResolutionBreached() {
        return resolutionBreached;
    }

    public SlaState getSlaState() {
        return slaState;
    }
}
