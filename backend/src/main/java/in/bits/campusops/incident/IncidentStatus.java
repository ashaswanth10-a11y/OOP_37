package in.bits.campusops.incident;

import java.util.EnumSet;
import java.util.Set;

public enum IncidentStatus {
    NEW, CLASSIFIED, ASSIGNED, ACKNOWLEDGED, IN_PROGRESS, AWAITING_INFO, RESOLVED, VERIFIED, CLOSED,
    REOPENED, CANCELLED;

    /** Statuses in which somebody still has to act on the incident. */
    public static final Set<IncidentStatus> OPEN =
            EnumSet.of(NEW, CLASSIFIED, ASSIGNED, ACKNOWLEDGED, IN_PROGRESS, AWAITING_INFO, REOPENED);

    /** Statuses that count against a technician's workload. */
    public static final Set<IncidentStatus> ACTIVE_WORK = EnumSet.of(ASSIGNED, ACKNOWLEDGED, IN_PROGRESS, AWAITING_INFO);

    /** Statuses after a fix was delivered. */
    public static final Set<IncidentStatus> FIXED = EnumSet.of(RESOLVED, VERIFIED, CLOSED);

    public boolean isOpen() {
        return OPEN.contains(this);
    }
}
