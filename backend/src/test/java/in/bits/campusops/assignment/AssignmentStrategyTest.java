package in.bits.campusops.assignment;

import static org.assertj.core.api.Assertions.assertThat;

import in.bits.campusops.assignment.AssignmentStrategy.Candidate;
import in.bits.campusops.incident.Fixtures;
import in.bits.campusops.incident.Incident;
import in.bits.campusops.user.Technician;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Polymorphism: the same candidates ranked by interchangeable strategies. */
class AssignmentStrategyTest {

    private final Technician netBusy = Fixtures.technician(1, List.of("NETWORK"), 5, 9);
    private final Technician netFree = Fixtures.technician(2, List.of("NETWORK"), 5, 2);
    private final Technician desktop = Fixtures.technician(3, List.of("HARDWARE"), 5, 4);
    private final Incident incident = Fixtures.classifiedIncident();

    private List<Candidate> candidates() {
        return List.of(new Candidate(netBusy, 4, 6, true), new Candidate(netFree, 1, 0, true),
                new Candidate(desktop, 0, 0, false));
    }

    @Test
    @DisplayName("TC14 Skill-based picks the least busy technician with the matching skill")
    void skillBased() {
        assertThat(new Strategies.SkillBased().select(incident, candidates()).orElseThrow().technician())
                .isEqualTo(netFree);
    }

    @Test
    @DisplayName("TC15 Load-based ignores skills and picks the most free capacity")
    void loadBased() {
        assertThat(new Strategies.LoadBased().select(incident, candidates()).orElseThrow().technician())
                .isEqualTo(desktop);
    }

    @Test
    @DisplayName("TC16 Experience-based picks whoever fixed this service most often")
    void experienceBased() {
        assertThat(new Strategies.ExperienceBased().select(incident, candidates()).orElseThrow().technician())
                .isEqualTo(netBusy);
    }

    @Test
    @DisplayName("TC17 Round robin rotates through the skilled pool")
    void roundRobin() {
        Strategies.RoundRobin rr = new Strategies.RoundRobin();
        Technician first = rr.select(incident, candidates()).orElseThrow().technician();
        Technician second = rr.select(incident, candidates()).orElseThrow().technician();
        Technician third = rr.select(incident, candidates()).orElseThrow().technician();
        assertThat(first).isNotEqualTo(second);
        assertThat(third).isEqualTo(first);
        assertThat(List.of(first, second)).doesNotContain(desktop);
    }

    @Test
    @DisplayName("TC18 No candidate means no assignment (incident stays queued)")
    void nobody() {
        assertThat(new Strategies.SkillBased().select(incident, List.of(new Candidate(desktop, 0, 0, false)))).isEmpty();
    }

    @Test
    @DisplayName("TC19 Workload boundary: a technician at max capacity cannot take more")
    void workloadBoundary() {
        assertThat(netBusy.canTakeMore(4)).isTrue();
        assertThat(netBusy.canTakeMore(5)).isFalse();
        netFree.setAvailable(false);
        assertThat(netFree.canTakeMore(0)).isFalse();
    }
}
