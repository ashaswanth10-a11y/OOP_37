package in.bits.campusops.incident;

import static in.bits.campusops.incident.Fixtures.T0;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import in.bits.campusops.catalog.Priority;
import in.bits.campusops.catalog.SlaPolicy;
import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SlaTrackerTest {

    private SlaTracker started() {
        SlaTracker t = new SlaTracker();
        t.start(new SlaPolicy(null, Priority.P1, 5, 30), T0);
        return t;
    }

    @Test
    @DisplayName("TC06 Deadlines are computed from the policy")
    void deadlines() {
        SlaTracker t = started();
        assertThat(t.getResponseDue()).isEqualTo(T0.plus(Duration.ofMinutes(5)));
        assertThat(t.getResolutionDue()).isEqualTo(T0.plus(Duration.ofMinutes(30)));
        assertThat(t.getSlaState()).isEqualTo(SlaState.ON_TRACK);
    }

    @Test
    @DisplayName("TC07 Warning fires once at 75% (boundary), breach fires once after the deadline")
    void warningThenBreach() {
        SlaTracker t = started();
        assertThat(t.evaluate(T0.plusSeconds(224), 75)).isEmpty(); // 74.7% of 5 min
        assertThat(t.evaluate(T0.plusSeconds(225), 75)).containsExactly(SlaTracker.Signal.RESPONSE_WARNING);
        assertThat(t.evaluate(T0.plusSeconds(230), 75)).isEmpty();
        assertThat(t.getSlaState()).isEqualTo(SlaState.AT_RISK);
        assertThat(t.evaluate(T0.plusSeconds(301), 75)).containsExactly(SlaTracker.Signal.RESPONSE_BREACHED);
        assertThat(t.evaluate(T0.plusSeconds(400), 75)).isEmpty();
        assertThat(t.getSlaState()).isEqualTo(SlaState.BREACHED);
    }

    @Test
    @DisplayName("TC08 Resolving late marks the resolution breach")
    void lateResolution() {
        SlaTracker t = started();
        t.markResponded(T0.plusSeconds(60));
        t.markResolved(T0.plus(Duration.ofMinutes(45)));
        assertThat(t.isResolutionBreached()).isTrue();
        assertThat(t.getSlaState()).isEqualTo(SlaState.BREACHED);
    }

    @Test
    @DisplayName("TC09 An SLA policy whose resolution target is shorter than its response target is invalid")
    void invalidPolicy() {
        assertThatThrownBy(() -> new SlaPolicy(null, Priority.P2, 60, 30)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new SlaPolicy(null, Priority.P2, 0, 30)).isInstanceOf(IllegalArgumentException.class);
    }
}
