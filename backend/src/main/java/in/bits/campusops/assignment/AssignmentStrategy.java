package in.bits.campusops.assignment;

import in.bits.campusops.incident.Incident;
import in.bits.campusops.user.Technician;
import java.util.List;
import java.util.Optional;

/**
 * Strategy pattern: interchangeable rules for picking a technician. The admin switches
 * the active one at runtime; nothing else in the system changes.
 */
public interface AssignmentStrategy {

    /** A technician who could take the incident, with the facts strategies rank on. */
    record Candidate(Technician technician, int active, int resolvedOnService, boolean skillMatch) {
        public double loadRatio() {
            return (double) active / technician.getMaxConcurrent();
        }
    }

    record Recommendation(Technician technician, double score, String reason) {
    }

    String key();

    String label();

    /** Eligible candidates, best first. Empty means nobody suitable. */
    List<Recommendation> rank(Incident incident, List<Candidate> candidates);

    default Optional<Recommendation> select(Incident incident, List<Candidate> candidates) {
        return rank(incident, candidates).stream().findFirst();
    }

    static boolean anySkilled(List<Candidate> candidates) {
        return candidates.stream().anyMatch(Candidate::skillMatch);
    }
}
