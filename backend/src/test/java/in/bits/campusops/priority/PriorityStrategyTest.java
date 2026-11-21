package in.bits.campusops.priority;

import static org.assertj.core.api.Assertions.assertThat;

import in.bits.campusops.catalog.ItService;
import in.bits.campusops.catalog.Level;
import in.bits.campusops.catalog.Priority;
import in.bits.campusops.incident.Fixtures;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PriorityStrategyTest {

    private final WeightedScoreStrategy weighted = new WeightedScoreStrategy();
    private final RuleBasedStrategy rules = new RuleBasedStrategy();

    @Test
    @DisplayName("TC10 Payment portal down for 200 users is P1 (problem scenario from the brief)")
    void paymentOutageIsCritical() {
        ItService payment = Fixtures.service("FEE-PORTAL", 5);
        var a = weighted.assess(new PriorityStrategy.Input(Level.HIGH, Level.CRITICAL, payment, 200));
        assertThat(a.priority()).isEqualTo(Priority.P1);
        assertThat(a.rationale()).contains("FEE-PORTAL service");
    }

    @Test
    @DisplayName("TC11 One user with a minor printer issue is P4")
    void minorIsLow() {
        var a = weighted.assess(new PriorityStrategy.Input(Level.LOW, Level.MEDIUM, Fixtures.service("PRINT", 2), 1));
        assertThat(a.priority()).isEqualTo(Priority.P4);
    }

    @Test
    @DisplayName("TC12 Score boundaries: 60 is P1, just below is P2")
    void boundaries() {
        // 3 x 4 x 5 x reach(1 user) = 60.0
        var exactly = weighted.assess(new PriorityStrategy.Input(Level.HIGH, Level.CRITICAL, Fixtures.service("A", 5), 1));
        assertThat(exactly.score()).isEqualTo(60.0);
        assertThat(exactly.priority()).isEqualTo(Priority.P1);
        // 3 x 4 x 4 = 48 -> P2
        var below = weighted.assess(new PriorityStrategy.Input(Level.HIGH, Level.CRITICAL, Fixtures.service("B", 4), 1));
        assertThat(below.priority()).isEqualTo(Priority.P2);
    }

    @Test
    @DisplayName("TC13 Rule matrix: business-critical service and 500+ users raise priority")
    void ruleBased() {
        var normal = rules.assess(new PriorityStrategy.Input(Level.MEDIUM, Level.MEDIUM, Fixtures.service("X", 3), 5));
        assertThat(normal.priority()).isEqualTo(Priority.P3);
        var critical = rules.assess(new PriorityStrategy.Input(Level.MEDIUM, Level.MEDIUM, Fixtures.service("Y", 5), 5));
        assertThat(critical.priority()).isEqualTo(Priority.P2);
        var many = rules.assess(new PriorityStrategy.Input(Level.LOW, Level.LOW, Fixtures.service("Z", 2), 800));
        assertThat(many.priority()).isEqualTo(Priority.P2);
    }
}
