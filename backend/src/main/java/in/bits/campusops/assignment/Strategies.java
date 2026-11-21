package in.bits.campusops.assignment;

import in.bits.campusops.incident.Incident;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.stereotype.Component;

/** The concrete assignment strategies. Each one is a Spring bean found by its key. */
public final class Strategies {

    private Strategies() {
    }

    /** Only technicians with the incident's category skill; least busy first. */
    @Component
    public static class SkillBased implements AssignmentStrategy {
        @Override
        public String key() {
            return "SKILL_BASED";
        }

        @Override
        public String label() {
            return "Skill-based: matching skill, then lowest workload";
        }

        @Override
        public List<Recommendation> rank(Incident incident, List<Candidate> candidates) {
            return candidates.stream()
                    .filter(Candidate::skillMatch)
                    .sorted(Comparator.comparingInt(Candidate::active)
                            .thenComparing(c -> -c.technician().getExperienceYears()))
                    .map(c -> new Recommendation(c.technician(), 1.0 - c.loadRatio(),
                            "Has " + incident.getCategory().getCode() + " skill, " + c.active() + " active incidents"))
                    .toList();
        }
    }

    /** Whoever has the most free capacity, regardless of skill. Good during mass outages. */
    @Component
    public static class LoadBased implements AssignmentStrategy {
        @Override
        public String key() {
            return "LOAD_BASED";
        }

        @Override
        public String label() {
            return "Load-based: most free capacity first";
        }

        @Override
        public List<Recommendation> rank(Incident incident, List<Candidate> candidates) {
            return candidates.stream()
                    .sorted(Comparator.comparingDouble(Candidate::loadRatio)
                            .thenComparing(c -> c.skillMatch() ? 0 : 1))
                    .map(c -> new Recommendation(c.technician(), 1.0 - c.loadRatio(),
                            "%d of %d slots used".formatted(c.active(), c.technician().getMaxConcurrent())))
                    .toList();
        }
    }

    /** Takes turns among skilled technicians (or everyone if nobody is skilled). */
    @Component
    public static class RoundRobin implements AssignmentStrategy, in.bits.campusops.common.DemoResettable {
        private final AtomicLong lastAssignedId = new AtomicLong(0);

        @Override
        public void resetForDemo() {
            lastAssignedId.set(0);
        }

        @Override
        public String key() {
            return "ROUND_ROBIN";
        }

        @Override
        public String label() {
            return "Round robin: take turns within the skilled pool";
        }

        @Override
        public List<Recommendation> rank(Incident incident, List<Candidate> candidates) {
            boolean skilled = AssignmentStrategy.anySkilled(candidates);
            List<Candidate> pool = candidates.stream()
                    .filter(c -> !skilled || c.skillMatch())
                    .sorted(Comparator.comparing(c -> c.technician().getId()))
                    .toList();
            if (pool.isEmpty()) {
                return List.of();
            }
            long last = lastAssignedId.get();
            int start = 0;
            for (int i = 0; i < pool.size(); i++) {
                if (pool.get(i).technician().getId() > last) {
                    start = i;
                    break;
                }
                start = 0;
            }
            List<Recommendation> ordered = new ArrayList<>();
            for (int i = 0; i < pool.size(); i++) {
                Candidate c = pool.get((start + i) % pool.size());
                ordered.add(new Recommendation(c.technician(), 1.0 - (double) i / pool.size(),
                        i == 0 ? "Next in the rotation" : "Position " + (i + 1) + " in the rotation"));
            }
            lastAssignedId.set(ordered.get(0).technician().getId());
            return ordered;
        }
    }

    /** Whoever has fixed the most incidents on this exact service, then most years of experience. */
    @Component
    public static class ExperienceBased implements AssignmentStrategy {
        @Override
        public String key() {
            return "EXPERIENCE_BASED";
        }

        @Override
        public String label() {
            return "Experience-based: most fixes on this service";
        }

        @Override
        public List<Recommendation> rank(Incident incident, List<Candidate> candidates) {
            boolean skilled = AssignmentStrategy.anySkilled(candidates);
            return candidates.stream()
                    .filter(c -> !skilled || c.skillMatch())
                    .sorted(Comparator.comparingInt((Candidate c) -> -c.resolvedOnService())
                            .thenComparing(c -> -c.technician().getExperienceYears()))
                    .map(c -> new Recommendation(c.technician(), Math.min(1.0, c.resolvedOnService() / 5.0),
                            "Fixed %d incidents on %s, %d years experience".formatted(c.resolvedOnService(),
                                    incident.getService().getName(), c.technician().getExperienceYears())))
                    .toList();
        }
    }

    /**
     * Smart recommendation: weighted blend of skill, free capacity, history with the
     * service and seniority. Critical incidents lean harder on experience.
     */
    @Component
    public static class Smart implements AssignmentStrategy {
        @Override
        public String key() {
            return "SMART";
        }

        @Override
        public String label() {
            return "Smart: weighted skill, load, service history and seniority";
        }

        @Override
        public List<Recommendation> rank(Incident incident, List<Candidate> candidates) {
            boolean critical = incident.getPriority() != null && incident.getPriority().isUrgent();
            double wSkill = 0.40;
            double wLoad = critical ? 0.15 : 0.25;
            double wHistory = critical ? 0.28 : 0.20;
            double wSenior = critical ? 0.17 : 0.15;
            return candidates.stream()
                    .map(c -> {
                        double skill = c.skillMatch() ? 1 : 0;
                        double free = 1 - c.loadRatio();
                        double history = Math.min(1.0, c.resolvedOnService() / 5.0);
                        double senior = Math.min(1.0, c.technician().getExperienceYears() / 10.0);
                        double score = wSkill * skill + wLoad * free + wHistory * history + wSenior * senior;
                        String reason = "skill %s, %d/%d busy, %d fixes on this service, %d yrs"
                                .formatted(c.skillMatch() ? "yes" : "no", c.active(),
                                        c.technician().getMaxConcurrent(), c.resolvedOnService(),
                                        c.technician().getExperienceYears());
                        return new Recommendation(c.technician(), Math.round(score * 100) / 100.0, reason);
                    })
                    .sorted(Comparator.comparingDouble(Recommendation::score).reversed())
                    .toList();
        }
    }
}
