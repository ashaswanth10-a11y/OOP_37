package in.bits.campusops.incident;

import in.bits.campusops.common.Exceptions.InvalidStateTransitionException;
import java.time.Instant;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * The lifecycle graph. One immutable state object per status:
 *
 * <pre>
 * NEW -> CLASSIFIED -> ASSIGNED -> ACKNOWLEDGED -> IN_PROGRESS <-> AWAITING_INFO
 *                                                    |
 *                                                RESOLVED -> VERIFIED -> CLOSED
 *                                                    |
 *                                                REOPENED -> ASSIGNED
 * NEW / CLASSIFIED -> CANCELLED
 * </pre>
 */
public final class IncidentStates {

    private static final Map<IncidentStatus, IncidentState> STATES = new EnumMap<>(IncidentStatus.class);

    static {
        register(new NewState());
        register(new ClassifiedState());
        register(new AssignedState());
        register(new AcknowledgedState());
        register(new InProgressState());
        register(new AwaitingInfoState());
        register(new ResolvedState());
        register(new VerifiedState());
        register(new ClosedState());
        register(new ReopenedState());
        register(new CancelledState());
    }

    private IncidentStates() {
    }

    private static void register(IncidentState state) {
        STATES.put(state.status(), state);
    }

    public static IncidentState of(IncidentStatus status) {
        return STATES.get(status);
    }

    /** Base class holding the transition table so each concrete state only states its rules. */
    abstract static class AbstractState implements IncidentState {
        private final IncidentStatus status;
        private final Set<IncidentStatus> next;

        AbstractState(IncidentStatus status, Set<IncidentStatus> next) {
            this.status = status;
            this.next = Collections.unmodifiableSet(next.isEmpty() ? EnumSet.noneOf(IncidentStatus.class)
                    : EnumSet.copyOf(next));
        }

        @Override
        public IncidentStatus status() {
            return status;
        }

        @Override
        public Set<IncidentStatus> allowedNext() {
            return next;
        }
    }

    static final class NewState extends AbstractState {
        NewState() {
            super(IncidentStatus.NEW, EnumSet.of(IncidentStatus.CLASSIFIED, IncidentStatus.CANCELLED));
        }

        @Override
        public String describe() {
            return "Reported, waiting to be classified";
        }
    }

    static final class ClassifiedState extends AbstractState {
        ClassifiedState() {
            super(IncidentStatus.CLASSIFIED, EnumSet.of(IncidentStatus.ASSIGNED, IncidentStatus.CANCELLED));
        }

        @Override
        public boolean allowsAssignment() {
            return true;
        }

        @Override
        public void validateEntry(Incident incident) {
            if (incident.getCategory() == null) {
                throw new InvalidStateTransitionException("An incident needs a category before it is classified");
            }
        }

        @Override
        public String describe() {
            return "Classified and prioritised, waiting for a technician";
        }
    }

    static final class AssignedState extends AbstractState {
        AssignedState() {
            super(IncidentStatus.ASSIGNED, EnumSet.of(IncidentStatus.ACKNOWLEDGED, IncidentStatus.CANCELLED));
        }

        @Override
        public boolean allowsAssignment() {
            return true;
        }

        @Override
        public void validateEntry(Incident incident) {
            if (incident.getAssignee() == null) {
                throw new InvalidStateTransitionException("Pick a technician before assigning");
            }
        }

        @Override
        public String describe() {
            return "Assigned, waiting for the technician to acknowledge";
        }
    }

    static final class AcknowledgedState extends AbstractState {
        AcknowledgedState() {
            super(IncidentStatus.ACKNOWLEDGED, EnumSet.of(IncidentStatus.IN_PROGRESS));
        }

        @Override
        public boolean allowsAssignment() {
            return true;
        }

        @Override
        public void onEnter(Incident incident, Instant now) {
            incident.sla().markResponded(now);
        }

        @Override
        public String describe() {
            return "Technician has acknowledged it";
        }
    }

    static final class InProgressState extends AbstractState {
        InProgressState() {
            super(IncidentStatus.IN_PROGRESS, EnumSet.of(IncidentStatus.AWAITING_INFO, IncidentStatus.RESOLVED));
        }

        @Override
        public boolean allowsAssignment() {
            return true;
        }

        @Override
        public String describe() {
            return "Being investigated";
        }
    }

    static final class AwaitingInfoState extends AbstractState {
        AwaitingInfoState() {
            super(IncidentStatus.AWAITING_INFO, EnumSet.of(IncidentStatus.IN_PROGRESS, IncidentStatus.RESOLVED));
        }

        @Override
        public boolean allowsAssignment() {
            return true;
        }

        @Override
        public String describe() {
            return "Waiting for more information from the reporter";
        }
    }

    static final class ResolvedState extends AbstractState {
        ResolvedState() {
            super(IncidentStatus.RESOLVED, EnumSet.of(IncidentStatus.VERIFIED, IncidentStatus.REOPENED));
        }

        @Override
        public void validateEntry(Incident incident) {
            if (incident.getResolution() == null || !incident.getResolution().isPresent()) {
                throw new InvalidStateTransitionException("Add resolution details before resolving");
            }
        }

        @Override
        public void onEnter(Incident incident, Instant now) {
            incident.sla().markResolved(now);
        }

        @Override
        public String describe() {
            return "Fixed, waiting for the reporter to confirm";
        }
    }

    static final class VerifiedState extends AbstractState {
        VerifiedState() {
            super(IncidentStatus.VERIFIED, EnumSet.of(IncidentStatus.CLOSED));
        }

        @Override
        public String describe() {
            return "Fix confirmed, ready to close";
        }
    }

    static final class ClosedState extends AbstractState {
        ClosedState() {
            super(IncidentStatus.CLOSED, EnumSet.noneOf(IncidentStatus.class));
        }

        @Override
        public void onEnter(Incident incident, Instant now) {
            incident.recordClosure(now);
        }

        @Override
        public String describe() {
            return "Closed";
        }
    }

    static final class ReopenedState extends AbstractState {
        ReopenedState() {
            super(IncidentStatus.REOPENED, EnumSet.of(IncidentStatus.ASSIGNED));
        }

        @Override
        public boolean allowsAssignment() {
            return true;
        }

        @Override
        public void onEnter(Incident incident, Instant now) {
            incident.sla().reopen();
        }

        @Override
        public String describe() {
            return "The fix did not work; back in the queue";
        }
    }

    static final class CancelledState extends AbstractState {
        CancelledState() {
            super(IncidentStatus.CANCELLED, EnumSet.noneOf(IncidentStatus.class));
        }

        @Override
        public void onEnter(Incident incident, Instant now) {
            incident.recordClosure(now);
        }

        @Override
        public String describe() {
            return "Cancelled by the reporter";
        }
    }
}
