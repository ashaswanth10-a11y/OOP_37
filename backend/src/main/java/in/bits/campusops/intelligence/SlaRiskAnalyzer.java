package in.bits.campusops.intelligence;

import in.bits.campusops.common.TimeProvider;
import in.bits.campusops.incident.Incident;
import in.bits.campusops.incident.IncidentRepository;
import in.bits.campusops.incident.IncidentStatus;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Predicts whether an open incident will miss its resolution deadline, before it does.
 * Projected finish = now + (typical fix time for this category/priority - time spent)
 * x workload factor of the assignee; an unassigned incident adds a queue delay.
 * Risk = projected position inside the SLA window (over 1.0 means it will breach).
 */
@Service
@Transactional(readOnly = true)
public class SlaRiskAnalyzer implements in.bits.campusops.common.DemoResettable {

    public enum Level { LOW, MEDIUM, HIGH }

    @Override
    public void resetForDemo() {
        typicalCache = Map.of();
        cachedAt = Instant.EPOCH;
    }

    public record Risk(Level level, double score, String reason) {
    }

    private final IncidentRepository incidents;
    private final TimeProvider time;
    private volatile Map<String, Duration> typicalCache = Map.of();
    private volatile Instant cachedAt = Instant.EPOCH;

    public SlaRiskAnalyzer(IncidentRepository incidents, TimeProvider time) {
        this.incidents = incidents;
        this.time = time;
    }

    public Risk assess(Incident incident, Map<Long, Integer> workload) {
        Instant now = time.now();
        var sla = incident.getSla();
        if (!incident.isOpen() || !sla.isStarted()) {
            return new Risk(Level.LOW, 0, "Not open");
        }
        if (sla.isResolutionBreached()) {
            return new Risk(Level.HIGH, sla.resolutionUsed(now), "Already past the resolution deadline");
        }
        Duration window = Duration.between(sla.getSlaStart(), sla.getResolutionDue());
        Duration spent = Duration.between(sla.getSlaStart(), now);
        Duration typical = typicalFixTime(incident, window);
        Duration remainingWork = typical.minus(spent).isNegative() ? typical.dividedBy(4) : typical.minus(spent);
        double factor = 1.0;
        String why;
        if (incident.getAssignee() == null) {
            factor += 0.5;
            why = "unassigned";
        } else {
            int load = workload.getOrDefault(incident.getAssignee().getId(), 1);
            factor += 0.15 * Math.max(0, load - 1);
            why = incident.getAssignee().getFullName() + " has " + load + " active";
        }
        if (incident.getStatus() == IncidentStatus.AWAITING_INFO) {
            factor += 0.25;
            why += ", waiting on reporter";
        }
        long projectedMillis = spent.toMillis() + (long) (remainingWork.toMillis() * factor);
        double score = window.isZero() ? 1 : (double) projectedMillis / window.toMillis();
        score = Math.round(score * 100) / 100.0;
        Level level = score >= 1.0 ? Level.HIGH : score >= 0.75 ? Level.MEDIUM : Level.LOW;
        String reason = "Typical fix %s, %s used, %s".formatted(SimilarityService.humanize(typical),
                Math.round(sla.resolutionUsed(now) * 100) + "%", why);
        return new Risk(level, score, reason);
    }

    /** Median resolution time of past incidents in the same category and priority. */
    private Duration typicalFixTime(Incident incident, Duration fallbackWindow) {
        Map<String, Duration> cache = typicalCache;
        if (Duration.between(cachedAt, time.now()).toMinutes() > 5 || cache.isEmpty()) {
            cache = rebuild();
        }
        String key = key(incident.getCategory() == null ? null : incident.getCategory().getCode(),
                incident.getPriority() == null ? null : incident.getPriority().name());
        Duration typical = cache.get(key);
        return typical != null ? typical : fallbackWindow.dividedBy(2);
    }

    private synchronized Map<String, Duration> rebuild() {
        Map<String, List<Long>> samples = new HashMap<>();
        for (Incident i : incidents.findByStatusIn(IncidentStatus.FIXED)) {
            if (i.getSla().getResolvedAt() == null || i.getCategory() == null || i.getPriority() == null) {
                continue;
            }
            long minutes = Duration.between(i.getCreatedAt(), i.getSla().getResolvedAt()).toMinutes();
            samples.computeIfAbsent(key(i.getCategory().getCode(), i.getPriority().name()), k -> new java.util.ArrayList<>())
                    .add(minutes);
        }
        Map<String, Duration> result = new HashMap<>();
        samples.forEach((k, list) -> {
            list.sort(Long::compare);
            result.put(k, Duration.ofMinutes(list.get(list.size() / 2)));
        });
        typicalCache = result;
        cachedAt = time.now();
        return result;
    }

    private static String key(String category, String priority) {
        return category + "/" + priority;
    }
}
