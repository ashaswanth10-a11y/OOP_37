package in.bits.campusops.settings;

import in.bits.campusops.ai.ClaudeCategoryClassifier;
import in.bits.campusops.ai.GeminiCategoryClassifier;
import in.bits.campusops.assignment.AssignmentService;
import in.bits.campusops.priority.PriorityService;
import in.bits.campusops.realtime.SseHub;
import in.bits.campusops.sla.EscalationService;
import in.bits.campusops.sla.SlaMonitor;
import in.bits.campusops.sla.SlaViolation;
import in.bits.campusops.telegram.TelegramAdapter;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final SettingsService settings;
    private final AssignmentService assignment;
    private final PriorityService priority;
    private final SlaMonitor.SlaViolationRepository violations;
    private final ClaudeCategoryClassifier ai;
    private final GeminiCategoryClassifier gemini;
    private final TelegramAdapter telegram;
    private final SseHub hub;

    public AdminController(SettingsService settings, AssignmentService assignment, PriorityService priority,
                           SlaMonitor.SlaViolationRepository violations, ClaudeCategoryClassifier ai,
                           GeminiCategoryClassifier gemini, TelegramAdapter telegram, SseHub hub) {
        this.settings = settings;
        this.assignment = assignment;
        this.priority = priority;
        this.violations = violations;
        this.ai = ai;
        this.gemini = gemini;
        this.telegram = telegram;
        this.hub = hub;
    }

    public record SettingsRequest(@NotBlank String assignmentStrategy, @NotBlank String priorityStrategy,
                                  int slaWarningPercent, double duplicateThreshold, int correlationWindowMinutes,
                                  int correlationMinIncidents, boolean autoEscalation, boolean aiTriage) {
    }

    public record Option(String key, String label) {
    }

    @GetMapping("/settings")
    @PreAuthorize("hasAnyRole('SYSTEM_ADMIN','TEAM_LEAD')")
    public Map<String, Object> get() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("settings", settings.get());
        out.put("assignmentStrategies", assignment.all().stream().map(s -> new Option(s.key(), s.label())).toList());
        out.put("priorityStrategies", priority.all().stream().map(s -> new Option(s.key(), s.label())).toList());
        out.put("escalationChain", EscalationService.CHAIN);
        out.put("integrations", Map.of("claude", ai.isEnabled(), "gemini", gemini.isEnabled(),
                "geminiModel", gemini.model(), "telegram", telegram.isEnabled(),
                "liveConnections", hub.connections()));
        return out;
    }

    @PutMapping("/settings")
    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    public SystemSettings update(@Valid @RequestBody SettingsRequest r) {
        assignment.require(r.assignmentStrategy());
        priority.require(r.priorityStrategy());
        return settings.update(r.assignmentStrategy(), r.priorityStrategy(), r.slaWarningPercent(),
                r.duplicateThreshold(), r.correlationWindowMinutes(), r.correlationMinIncidents(), r.autoEscalation(),
                r.aiTriage());
    }

    @GetMapping("/sla-violations")
    @PreAuthorize("hasAnyRole('SYSTEM_ADMIN','TEAM_LEAD')")
    public List<SlaViolation> violations() {
        return violations.findAllByOrderByDetectedAtDesc();
    }
}
