package in.bits.campusops.incident;

import static in.bits.campusops.incident.Fixtures.T0;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import in.bits.campusops.common.Exceptions.InvalidStateTransitionException;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** State pattern: legal and illegal lifecycle moves. */
class IncidentLifecycleTest {

    @Test
    @DisplayName("TC01 NEW cannot jump straight to CLOSED")
    void newCannotBeClosed() {
        Incident i = Fixtures.incident(Fixtures.service("ERP", 5), in.bits.campusops.catalog.Level.HIGH,
                in.bits.campusops.catalog.Level.HIGH, 10);
        assertThatThrownBy(() -> i.transitionTo(IncidentStatus.CLOSED, T0))
                .isInstanceOf(InvalidStateTransitionException.class)
                .hasMessageContaining("cannot move from NEW to CLOSED");
        assertThat(i.getStatus()).isEqualTo(IncidentStatus.NEW);
    }

    @Test
    @DisplayName("TC02 Full happy path NEW -> ... -> CLOSED with SLA clock updates")
    void happyPath() {
        Incident i = Fixtures.classifiedIncident();
        assertThat(i.getStatus()).isEqualTo(IncidentStatus.CLASSIFIED);

        i.assignTo(Fixtures.technician(7, java.util.List.of("NETWORK"), 5, 3), "TEST", T0.plusSeconds(60));
        assertThat(i.getStatus()).isEqualTo(IncidentStatus.ASSIGNED);

        Instant ack = T0.plusSeconds(5 * 60);
        i.transitionTo(IncidentStatus.ACKNOWLEDGED, ack);
        assertThat(i.getSla().getRespondedAt()).isEqualTo(ack);
        assertThat(i.getSla().isResponseBreached()).isFalse();

        i.transitionTo(IncidentStatus.IN_PROGRESS, ack.plusSeconds(60));
        i.resolve(new Resolution("Restarted the access switch", "Switch hung", "FIXED", "Tech 7"), ack.plusSeconds(1800));
        assertThat(i.getStatus()).isEqualTo(IncidentStatus.RESOLVED);
        assertThat(i.getSla().getSlaState()).isEqualTo(SlaState.MET);

        i.verify(new Feedback(5, "thanks"), ack.plusSeconds(3600));
        i.transitionTo(IncidentStatus.CLOSED, ack.plusSeconds(3700));
        assertThat(i.getStatus()).isEqualTo(IncidentStatus.CLOSED);
        assertThat(i.getClosedAt()).isNotNull();
        assertThat(i.state().allowedNext()).isEmpty();
    }

    @Test
    @DisplayName("TC03 Resolving without resolution details is rejected")
    void resolveNeedsDetails() {
        Incident i = Fixtures.classifiedIncident();
        i.assignTo(Fixtures.technician(7, java.util.List.of("NETWORK"), 5, 3), "TEST", T0);
        i.transitionTo(IncidentStatus.ACKNOWLEDGED, T0);
        i.transitionTo(IncidentStatus.IN_PROGRESS, T0);
        assertThatThrownBy(() -> i.transitionTo(IncidentStatus.RESOLVED, T0))
                .isInstanceOf(InvalidStateTransitionException.class)
                .hasMessageContaining("resolution");
    }

    @Test
    @DisplayName("TC04 A closed incident cannot be reassigned")
    void closedCannotBeAssigned() {
        Incident i = Fixtures.classifiedIncident();
        i.transitionTo(IncidentStatus.CANCELLED, T0);
        assertThatThrownBy(() -> i.assignTo(Fixtures.technician(8, java.util.List.of(), 5, 1), "TEST", T0))
                .isInstanceOf(InvalidStateTransitionException.class);
    }

    @Test
    @DisplayName("TC05 Reopened incident goes back to ASSIGNED and the SLA clock resumes")
    void reopen() {
        Incident i = Fixtures.classifiedIncident();
        i.assignTo(Fixtures.technician(7, java.util.List.of("NETWORK"), 5, 3), "TEST", T0);
        i.transitionTo(IncidentStatus.ACKNOWLEDGED, T0);
        i.transitionTo(IncidentStatus.IN_PROGRESS, T0);
        i.resolve(new Resolution("fixed", null, null, "x"), T0.plusSeconds(60));
        i.transitionTo(IncidentStatus.REOPENED, T0.plusSeconds(120));
        assertThat(i.getSla().getResolvedAt()).isNull();
        i.assignTo(i.getAssignee(), "REOPEN", T0.plusSeconds(130));
        assertThat(i.getStatus()).isEqualTo(IncidentStatus.ASSIGNED);
    }
}
