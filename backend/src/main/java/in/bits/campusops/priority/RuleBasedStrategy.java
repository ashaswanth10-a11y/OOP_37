package in.bits.campusops.priority;

import in.bits.campusops.catalog.Priority;
import org.springframework.stereotype.Component;

/**
 * ITIL-style impact/urgency matrix, then two business rules:
 * a service with criticality 5 is raised one level, and anything hitting 500+ users is at least P2.
 */
@Component
public class RuleBasedStrategy implements PriorityStrategy {

    private static final Priority[][] MATRIX = {
            // urgency:  LOW          MEDIUM       HIGH         CRITICAL
            /* LOW */ {Priority.P4, Priority.P4, Priority.P3, Priority.P3},
            /* MED */ {Priority.P4, Priority.P3, Priority.P3, Priority.P2},
            /* HIGH*/ {Priority.P3, Priority.P3, Priority.P2, Priority.P1},
            /* CRIT*/ {Priority.P3, Priority.P2, Priority.P1, Priority.P1},
    };

    @Override
    public String key() {
        return "RULE_BASED";
    }

    @Override
    public String label() {
        return "Rule matrix (ITIL impact x urgency + business rules)";
    }

    @Override
    public Assessment assess(Input in) {
        Priority p = MATRIX[in.impact().ordinal()][in.urgency().ordinal()];
        StringBuilder why = new StringBuilder("Matrix ").append(in.impact()).append(" impact / ")
                .append(in.urgency()).append(" urgency = ").append(p);
        if (in.service().getCriticality() == 5 && p != Priority.P1) {
            p = Priority.values()[p.ordinal() - 1];
            why.append("; raised to ").append(p).append(" because ").append(in.service().getName())
                    .append(" is business-critical");
        }
        if (in.affectedUsers() >= 500 && p.ordinal() > Priority.P2.ordinal()) {
            p = Priority.P2;
            why.append("; raised to P2 because ").append(in.affectedUsers()).append(" users are affected");
        }
        double score = (Priority.values().length - p.ordinal()) * 25.0;
        return new Assessment(p, score, why.toString());
    }
}
