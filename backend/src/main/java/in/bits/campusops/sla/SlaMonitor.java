package in.bits.campusops.sla;

import in.bits.campusops.common.TimeProvider;
import in.bits.campusops.events.IncidentEventPublisher;
import in.bits.campusops.events.IncidentEventType;
import in.bits.campusops.incident.Incident;
import in.bits.campusops.incident.IncidentRepository;
import in.bits.campusops.incident.SlaTracker;
import in.bits.campusops.settings.SettingsService;
import in.bits.campusops.settings.SystemSettings;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Background job: checks every open incident's SLA clock, raises warnings, records breaches, escalates. */
@Service
public class SlaMonitor {

    public interface SlaViolationRepository extends JpaRepository<SlaViolation, Long> {
        List<SlaViolation> findAllByOrderByDetectedAtDesc();
    }

    public record SweepResult(int checked, int warnings, int breaches, int escalations) {
    }

    private static final Logger log = LoggerFactory.getLogger(SlaMonitor.class);
    private static final DateTimeFormatter HHMM = DateTimeFormatter.ofPattern("dd MMM HH:mm")
            .withZone(ZoneId.of("Asia/Kolkata"));

    private final IncidentRepository incidents;
    private final SlaViolationRepository violations;
    private final EscalationService escalation;
    private final SettingsService settings;
    private final IncidentEventPublisher events;
    private final TimeProvider time;

    public SlaMonitor(IncidentRepository incidents, SlaViolationRepository violations, EscalationService escalation,
                      SettingsService settings, IncidentEventPublisher events, TimeProvider time) {
        this.incidents = incidents;
        this.violations = violations;
        this.escalation = escalation;
        this.settings = settings;
        this.events = events;
        this.time = time;
    }

    /**
     * Runs through the Spring proxy (the scheduler calls the bean, not "this"), so the
     * transaction is real and the warned/breached flags are saved: each alert fires once.
     */
    @Scheduled(fixedDelayString = "${campusops.sla.monitor-interval-ms:15000}", initialDelay = 3000)
    @Transactional
    public void scheduled() {
        SweepResult r = sweep();
        if (r.warnings() + r.breaches() + r.escalations() > 0) {
            log.info("SLA sweep: {}", r);
        }
    }

    @Transactional
    public SweepResult sweep() {
        SystemSettings s = settings.get();
        Instant now = time.now();
        int warnings = 0;
        int breaches = 0;
        int escalations = 0;
        List<Incident> open = incidents.findOpen();
        for (Incident i : open) {
            for (SlaTracker.Signal signal : i.getSla().evaluate(now, s.getSlaWarningPercent())) {
                switch (signal) {
                    case RESPONSE_WARNING -> {
                        warnings++;
                        events.publish(IncidentEventType.SLA_WARNING, i, null, s.getSlaWarningPercent()
                                + "% of the response time used; respond by " + HHMM.format(i.getSla().getResponseDue()));
                    }
                    case RESOLUTION_WARNING -> {
                        warnings++;
                        events.publish(IncidentEventType.SLA_WARNING, i, null, s.getSlaWarningPercent()
                                + "% of the resolution time used; resolve by " + HHMM.format(i.getSla().getResolutionDue()));
                    }
                    case RESPONSE_BREACHED -> {
                        breaches++;
                        record(i, SlaViolation.Kind.RESPONSE, i.getSla().getResponseDue(), now);
                        events.publish(IncidentEventType.SLA_BREACHED, i, null,
                                "Response target missed (was due " + HHMM.format(i.getSla().getResponseDue()) + ")");
                    }
                    case RESOLUTION_BREACHED -> {
                        breaches++;
                        record(i, SlaViolation.Kind.RESOLUTION, i.getSla().getResolutionDue(), now);
                        events.publish(IncidentEventType.SLA_BREACHED, i, null,
                                "Resolution target missed (was due " + HHMM.format(i.getSla().getResolutionDue()) + ")");
                    }
                }
            }
            if (s.isAutoEscalation() && escalation.check(i)) {
                escalations++;
            }
        }
        return new SweepResult(open.size(), warnings, breaches, escalations);
    }

    private void record(Incident i, SlaViolation.Kind kind, Instant due, Instant now) {
        violations.save(new SlaViolation(i.getId(), i.getNumber(), kind,
                i.getPriority() == null ? null : i.getPriority().name(), i.getService().getName(),
                i.getAssignee() == null ? null : i.getAssignee().getFullName(), due, now));
    }
}
