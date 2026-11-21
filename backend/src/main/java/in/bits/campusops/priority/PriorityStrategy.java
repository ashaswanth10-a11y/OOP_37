package in.bits.campusops.priority;

import in.bits.campusops.catalog.ItService;
import in.bits.campusops.catalog.Level;
import in.bits.campusops.catalog.Priority;

/** Strategy pattern: interchangeable ways of turning impact and urgency into a priority. */
public interface PriorityStrategy {

    record Input(Level impact, Level urgency, ItService service, int affectedUsers) {
    }

    record Assessment(Priority priority, double score, String rationale) {
    }

    /** Stable key stored in settings, e.g. WEIGHTED_SCORE. */
    String key();

    String label();

    Assessment assess(Input input);
}
