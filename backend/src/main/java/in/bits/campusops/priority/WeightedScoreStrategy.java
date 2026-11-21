package in.bits.campusops.priority;

import in.bits.campusops.catalog.Priority;
import org.springframework.stereotype.Component;

/**
 * score = impact x urgency x criticality x reach, where
 * reach = 1 + log10(affected users) / 2 (1 user = 1.0, 100 users = 2.0, 1000 users = 2.5).
 * Bands: >= 60 P1, >= 30 P2, >= 10 P3, else P4.
 */
@Component
public class WeightedScoreStrategy implements PriorityStrategy {

    static final double P1_THRESHOLD = 60;
    static final double P2_THRESHOLD = 30;
    static final double P3_THRESHOLD = 10;

    @Override
    public String key() {
        return "WEIGHTED_SCORE";
    }

    @Override
    public String label() {
        return "Weighted score (impact x urgency x criticality x reach)";
    }

    @Override
    public Assessment assess(Input in) {
        int criticality = in.service().getCriticality();
        double reach = 1 + Math.log10(Math.max(1, in.affectedUsers())) / 2;
        double base = in.impact().weight() * in.urgency().weight() * criticality;
        double score = Math.round(base * reach * 10) / 10.0;
        Priority priority = score >= P1_THRESHOLD ? Priority.P1
                : score >= P2_THRESHOLD ? Priority.P2
                : score >= P3_THRESHOLD ? Priority.P3
                : Priority.P4;
        String rationale = "%s impact (%d) x %s urgency (%d) x criticality %d of %s x reach %.2f for %d users = %.1f -> %s"
                .formatted(in.impact(), in.impact().weight(), in.urgency(), in.urgency().weight(), criticality,
                        in.service().getName(), reach, in.affectedUsers(), score, priority);
        return new Assessment(priority, score, rationale);
    }
}
