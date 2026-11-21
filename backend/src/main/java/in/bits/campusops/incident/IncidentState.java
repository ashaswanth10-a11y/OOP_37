package in.bits.campusops.incident;

import java.time.Instant;
import java.util.Set;

/**
 * State pattern: each lifecycle status is an object that knows where it may go next
 * and what has to happen when an incident enters it.
 */
public interface IncidentState {

    IncidentStatus status();

    Set<IncidentStatus> allowedNext();

    default boolean canTransitionTo(IncidentStatus target) {
        return allowedNext().contains(target);
    }

    /** Whether a technician may be (re)assigned while in this state. */
    default boolean allowsAssignment() {
        return false;
    }

    /** Checks preconditions before entering this state; throws if they are not met. */
    default void validateEntry(Incident incident) {
    }

    /** Side effects of entering this state (SLA clock, timestamps). */
    default void onEnter(Incident incident, Instant now) {
    }

    /** Short human explanation shown in the UI. */
    String describe();
}
