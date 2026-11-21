package in.bits.campusops.report;

import in.bits.campusops.catalog.Priority;
import in.bits.campusops.common.TimeProvider;
import in.bits.campusops.incident.Incident;
import in.bits.campusops.incident.IncidentRepository;
import in.bits.campusops.incident.IncidentStatus;
import in.bits.campusops.intelligence.SlaRiskAnalyzer;
import in.bits.campusops.user.Technician;
import in.bits.campusops.user.UserRepository;
import in.bits.campusops.user.UserService;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.TreeMap;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Dashboards and analytics. Computed in memory, which is fine at campus scale (see report for scaling plan). */
@Service
@Transactional(readOnly = true)
public class ReportService {

    private static final ZoneId CAMPUS = ZoneId.of("Asia/Kolkata");

    public record Overview(long total, long open, long criticalOpen, long resolved, long slaBreaches,
                           long breachedOpen, double slaCompliance, double avgResolutionMinutes, double avgRating,
                           long escalated, long unassigned, long problemsOpen) {
    }

    public record TechnicianLoad(Long id, String code, String name, String team, int active, int max,
                                 long resolved30d, double avgRating, boolean available) {
    }

    public record ServiceRow(Long id, String name, long total, long open, long p1, long breaches) {
    }

    public record CategoryRow(String code, String name, String color, long total, long open, double avgMinutes) {
    }

    public record DayPoint(String date, long created, long resolved) {
    }

    public record AtRisk(Long id, String number, String title, String priority, String assignee, String level,
                         double score, String reason, Instant resolutionDue) {
    }

    public record Dashboard(Overview overview, List<TechnicianLoad> technicians, List<ServiceRow> services,
                            List<CategoryRow> categories, Map<String, Long> byPriority, Map<String, Long> byStatus,
                            Map<String, Long> byChannel, List<DayPoint> trend, List<AtRisk> atRisk) {
    }

    private final IncidentRepository incidents;
    private final UserRepository users;
    private final UserService userService;
    private final SlaRiskAnalyzer risk;
    private final in.bits.campusops.problem.ProblemRepository problems;
    private final TimeProvider time;

    public ReportService(IncidentRepository incidents, UserRepository users, UserService userService,
                         SlaRiskAnalyzer risk, in.bits.campusops.problem.ProblemRepository problems, TimeProvider time) {
        this.incidents = incidents;
        this.users = users;
        this.userService = userService;
        this.risk = risk;
        this.problems = problems;
        this.time = time;
    }

    public Dashboard dashboard() {
        List<Incident> all = incidents.findAll();
        Instant now = time.now();
        Map<Long, Integer> load = userService.activeLoad();

        List<Incident> fixed = all.stream().filter(i -> IncidentStatus.FIXED.contains(i.getStatus())).toList();
        long breaches = all.stream().filter(this::breached).count();
        long withSla = all.stream().filter(i -> i.getSla().isStarted()).count();
        double compliance = withSla == 0 ? 100 : Math.round((1 - (double) breaches / withSla) * 1000) / 10.0;
        OptionalDouble avgRes = fixed.stream().filter(i -> i.getSla().getResolvedAt() != null)
                .mapToLong(i -> Duration.between(i.getCreatedAt(), i.getSla().getResolvedAt()).toMinutes()).average();
        OptionalDouble avgRating = all.stream().filter(i -> i.getFeedback() != null && i.getFeedback().getFeedbackRating() != null)
                .mapToInt(i -> i.getFeedback().getFeedbackRating()).average();
        Overview overview = new Overview(all.size(),
                all.stream().filter(Incident::isOpen).count(),
                all.stream().filter(i -> i.isOpen() && i.getPriority() == Priority.P1).count(),
                fixed.size(), breaches,
                all.stream().filter(i -> i.isOpen() && breached(i)).count(), compliance,
                Math.round(avgRes.orElse(0) * 10) / 10.0, Math.round(avgRating.orElse(0) * 10) / 10.0,
                all.stream().filter(i -> i.isOpen() && i.getEscalationLevel() > 0).count(),
                all.stream().filter(i -> i.isOpen() && i.getAssignee() == null).count(),
                problems.findByStatusOrderByDetectedAtDesc(in.bits.campusops.problem.ProblemRecord.Status.OPEN).size());

        Instant monthAgo = now.minus(Duration.ofDays(30));
        List<TechnicianLoad> techs = new ArrayList<>();
        for (Technician t : users.findActiveTechnicians()) {
            List<Incident> theirs = all.stream().filter(i -> i.getAssignee() != null && i.getAssignee().getId().equals(t.getId())).toList();
            long resolved30 = theirs.stream().filter(i -> i.getSla().getResolvedAt() != null
                    && i.getSla().getResolvedAt().isAfter(monthAgo)).count();
            double rating = theirs.stream().filter(i -> i.getFeedback() != null && i.getFeedback().getFeedbackRating() != null)
                    .mapToInt(i -> i.getFeedback().getFeedbackRating()).average().orElse(0);
            techs.add(new TechnicianLoad(t.getId(), t.getCode(), t.getFullName(), t.getTeam(),
                    load.getOrDefault(t.getId(), 0), t.getMaxConcurrent(), resolved30, Math.round(rating * 10) / 10.0,
                    t.isAvailable()));
        }
        techs.sort(Comparator.comparingInt(TechnicianLoad::active).reversed());

        Map<Long, List<Incident>> byService = all.stream().collect(Collectors.groupingBy(i -> i.getService().getId()));
        List<ServiceRow> services = byService.values().stream().map(list -> {
            var s = list.get(0).getService();
            return new ServiceRow(s.getId(), s.getName(), list.size(), list.stream().filter(Incident::isOpen).count(),
                    list.stream().filter(i -> i.getPriority() == Priority.P1).count(),
                    list.stream().filter(this::breached).count());
        }).sorted(Comparator.comparingLong(ServiceRow::total).reversed()).toList();

        Map<String, List<Incident>> byCategory = all.stream().filter(i -> i.getCategory() != null)
                .collect(Collectors.groupingBy(i -> i.getCategory().getCode()));
        List<CategoryRow> categories = byCategory.values().stream().map(list -> {
            var c = list.get(0).getCategory();
            double avg = list.stream().filter(i -> i.getSla().getResolvedAt() != null)
                    .mapToLong(i -> Duration.between(i.getCreatedAt(), i.getSla().getResolvedAt()).toMinutes())
                    .average().orElse(0);
            return new CategoryRow(c.getCode(), c.getName(), c.getColor(), list.size(),
                    list.stream().filter(Incident::isOpen).count(), Math.round(avg));
        }).sorted(Comparator.comparingLong(CategoryRow::total).reversed()).toList();

        Map<String, Long> byPriority = new LinkedHashMap<>();
        for (Priority p : Priority.values()) {
            byPriority.put(p.name(), all.stream().filter(i -> i.getPriority() == p).count());
        }
        Map<String, Long> byStatus = new LinkedHashMap<>();
        for (IncidentStatus s : IncidentStatus.values()) {
            long c = all.stream().filter(i -> i.getStatus() == s).count();
            if (c > 0) {
                byStatus.put(s.name(), c);
            }
        }
        Map<String, Long> byChannel = all.stream().filter(i -> i.getChannel() != null)
                .collect(Collectors.groupingBy(i -> i.getChannel().name(), TreeMap::new, Collectors.counting()));

        LocalDate today = LocalDate.ofInstant(now, CAMPUS);
        List<DayPoint> trend = new ArrayList<>();
        for (int d = 13; d >= 0; d--) {
            LocalDate day = today.minusDays(d);
            long created = all.stream().filter(i -> LocalDate.ofInstant(i.getCreatedAt(), CAMPUS).equals(day)).count();
            long resolved = all.stream().filter(i -> i.getSla().getResolvedAt() != null
                    && LocalDate.ofInstant(i.getSla().getResolvedAt(), CAMPUS).equals(day)).count();
            trend.add(new DayPoint(day.toString(), created, resolved));
        }

        List<AtRisk> atRisk = all.stream().filter(Incident::isOpen)
                .map(i -> {
                    var r = risk.assess(i, load);
                    return new AtRisk(i.getId(), i.getNumber(), i.getTitle(),
                            i.getPriority() == null ? null : i.getPriority().name(),
                            i.getAssignee() == null ? null : i.getAssignee().getFullName(), r.level().name(), r.score(),
                            r.reason(), i.getSla().getResolutionDue());
                })
                .filter(a -> !"LOW".equals(a.level()))
                .sorted(Comparator.comparingDouble(AtRisk::score).reversed())
                .limit(8).toList();

        return new Dashboard(overview, techs, services, categories, byPriority, byStatus, byChannel, trend, atRisk);
    }

    private boolean breached(Incident i) {
        return i.getSla().isResponseBreached() || i.getSla().isResolutionBreached();
    }
}
