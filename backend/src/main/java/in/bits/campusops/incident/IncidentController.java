package in.bits.campusops.incident;

import in.bits.campusops.catalog.CatalogService;
import in.bits.campusops.catalog.ItService;
import in.bits.campusops.catalog.Level;
import in.bits.campusops.catalog.Priority;
import in.bits.campusops.common.PageResponse;
import in.bits.campusops.incident.IncidentViews.Detail;
import in.bits.campusops.incident.IncidentViews.Summary;
import in.bits.campusops.intelligence.SimilarityService;
import in.bits.campusops.intelligence.TriageService;
import in.bits.campusops.settings.SettingsService;
import in.bits.campusops.user.CurrentUser;
import in.bits.campusops.user.User;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/incidents")
@Transactional
public class IncidentController {

    private final IncidentService service;
    private final IncidentViewAssembler views;
    private final CurrentUser currentUser;
    private final TriageService triage;
    private final SimilarityService similarity;
    private final CatalogService catalog;
    private final SettingsService settings;

    public IncidentController(IncidentService service, IncidentViewAssembler views, CurrentUser currentUser,
                              TriageService triage, SimilarityService similarity, CatalogService catalog,
                              SettingsService settings) {
        this.service = service;
        this.views = views;
        this.currentUser = currentUser;
        this.triage = triage;
        this.similarity = similarity;
        this.catalog = catalog;
        this.settings = settings;
    }

    public record ReportRequest(@NotBlank @Size(min = 5, max = 160) String title,
                                @NotBlank @Size(min = 10, max = 4000) String description, Long serviceId,
                                String assetTag, Long categoryId, Level impact, Level urgency,
                                @Min(1) @Max(100000) Integer affectedUsers, @Size(max = 120) String location,
                                Incident.Channel channel, boolean confirmedNew) {
    }

    public record TextRequest(@NotBlank @Size(max = 4000) String text, boolean internal) {
    }

    public record ResolveRequest(@NotBlank @Size(max = 2000) String summary, @Size(max = 1000) String rootCause,
                                 String code) {
    }

    public record VerifyRequest(@Min(1) @Max(5) int rating, @Size(max = 1000) String comment) {
    }

    public record AssignRequest(@NotNull Long technicianId, boolean override) {
    }

    public record PriorityRequest(@NotNull Level impact, @NotNull Level urgency, @Min(1) int affectedUsers,
                                  @NotBlank String reason) {
    }

    public record PreviewRequest(String title, String description, Long serviceId, String assetTag) {
    }

    @PostMapping
    public Detail report(@Valid @RequestBody ReportRequest r) {
        User me = currentUser.get();
        Incident i = service.report(new IncidentService.ReportCommand(r.title(), r.description(), r.serviceId(),
                r.assetTag(), r.categoryId(), r.impact(), r.urgency(), r.affectedUsers(), r.location(), r.channel(),
                r.confirmedNew()), me);
        return views.detail(i, me);
    }

    /** Live help while typing a report: suggested category, similar open incidents, known fixes. */
    @PostMapping("/preview")
    @Transactional(readOnly = true)
    public Map<String, Object> preview(@RequestBody PreviewRequest r) {
        String text = (r.title() == null ? "" : r.title()) + " " + (r.description() == null ? "" : r.description());
        ItService svc = r.assetTag() != null && !r.assetTag().isBlank() ? catalog.requireAsset(r.assetTag()).getService()
                : r.serviceId() == null ? null : catalog.requireService(r.serviceId());
        var category = triage.preview(text, svc)
                .map(s -> Map.of("id", s.category().getId(), "code", s.category().getCode(), "name",
                        s.category().getName(), "confidence", s.confidence(), "reason", s.reason()))
                .orElse(null);
        var similar = text.isBlank() ? List.of() : similarity.findDuplicates(text, svc, null,
                        settings.get().getDuplicateThreshold()).stream()
                .map(m -> Map.of("id", m.incident().getId(), "number", m.incident().getNumber(), "title",
                        m.incident().getTitle(), "status", m.incident().getStatus().name(), "score", m.score(),
                        "reason", m.reason()))
                .toList();
        var fixes = text.isBlank() ? List.of() : similarity.suggestFixes(text, svc, null).stream()
                .map(f -> Map.of("number", f.source().getNumber(), "title", f.source().getTitle(), "resolution",
                        f.resolution(), "similarity", f.similarity()))
                .toList();
        java.util.HashMap<String, Object> out = new java.util.HashMap<>();
        out.put("category", category);
        out.put("similar", similar);
        out.put("fixes", fixes);
        out.put("aiAvailable", triage.aiAvailable());
        return out;
    }

    @GetMapping("/{id}")
    @Transactional(readOnly = true)
    public Detail get(@PathVariable Long id) {
        User me = currentUser.get();
        return views.detail(service.get(id, me), me);
    }

    @GetMapping("/mine")
    @Transactional(readOnly = true)
    public List<Summary> mine() {
        return service.involving(currentUser.get()).stream().map(views::summary).toList();
    }

    @GetMapping("/assigned")
    @PreAuthorize("hasRole('TECHNICIAN')")
    @Transactional(readOnly = true)
    public List<Summary> assigned() {
        return service.assignedTo(currentUser.get()).stream().map(views::summary).toList();
    }

    @GetMapping
    @Transactional(readOnly = true)
    public PageResponse<Summary> search(@RequestParam(required = false) String q,
                                        @RequestParam(required = false) List<IncidentStatus> status,
                                        @RequestParam(required = false) List<Priority> priority,
                                        @RequestParam(required = false) Long categoryId,
                                        @RequestParam(required = false) Long serviceId,
                                        @RequestParam(required = false) Long assigneeId,
                                        @RequestParam(required = false) SlaState slaState,
                                        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                        @RequestParam(required = false) Boolean escalated,
                                        @RequestParam(required = false) Long problemId,
                                        @RequestParam(required = false) Boolean openOnly,
                                        @RequestParam(required = false) Boolean unassigned,
                                        @RequestParam(defaultValue = "0") int page,
                                        @RequestParam(defaultValue = "20") int size,
                                        @RequestParam(defaultValue = "createdAt") String sort,
                                        @RequestParam(defaultValue = "desc") String dir) {
        IncidentSearch s = new IncidentSearch(q, status, priority, categoryId, serviceId, assigneeId, slaState, from,
                to, null, escalated, problemId, openOnly, unassigned);
        String sortField = switch (sort) {
            case "priority", "status", "updatedAt", "number" -> sort;
            default -> "createdAt";
        };
        Sort order = Sort.by("asc".equalsIgnoreCase(dir) ? Sort.Direction.ASC : Sort.Direction.DESC, sortField);
        return PageResponse.of(service.search(s, PageRequest.of(page, Math.min(size, 100), order), currentUser.get()),
                views::summary);
    }

    @PostMapping("/{id}/acknowledge")
    public Detail acknowledge(@PathVariable Long id) {
        User me = currentUser.get();
        return views.detail(service.acknowledge(id, me), me);
    }

    @PostMapping("/{id}/start")
    public Detail start(@PathVariable Long id) {
        User me = currentUser.get();
        return views.detail(service.start(id, me), me);
    }

    @PostMapping("/{id}/request-info")
    public Detail requestInfo(@PathVariable Long id, @Valid @RequestBody TextRequest r) {
        User me = currentUser.get();
        return views.detail(service.requestInfo(id, me, r.text()), me);
    }

    @PostMapping("/{id}/provide-info")
    public Detail provideInfo(@PathVariable Long id, @Valid @RequestBody TextRequest r) {
        User me = currentUser.get();
        return views.detail(service.provideInfo(id, me, r.text()), me);
    }

    @PostMapping("/{id}/notes")
    public Detail note(@PathVariable Long id, @Valid @RequestBody TextRequest r) {
        User me = currentUser.get();
        return views.detail(service.addNote(id, me, r.text(), r.internal()), me);
    }

    @PostMapping("/{id}/resolve")
    public Detail resolve(@PathVariable Long id, @Valid @RequestBody ResolveRequest r) {
        User me = currentUser.get();
        return views.detail(service.resolve(id, me, r.summary(), r.rootCause(), r.code()), me);
    }

    @PostMapping("/{id}/verify")
    public Detail verify(@PathVariable Long id, @Valid @RequestBody VerifyRequest r) {
        User me = currentUser.get();
        return views.detail(service.verify(id, me, r.rating(), r.comment()), me);
    }

    @PostMapping("/{id}/reopen")
    public Detail reopen(@PathVariable Long id, @Valid @RequestBody TextRequest r) {
        User me = currentUser.get();
        return views.detail(service.reopen(id, me, r.text()), me);
    }

    @PostMapping("/{id}/close")
    public Detail close(@PathVariable Long id) {
        User me = currentUser.get();
        return views.detail(service.close(id, me), me);
    }

    @PostMapping("/{id}/cancel")
    public Detail cancel(@PathVariable Long id, @RequestBody(required = false) TextRequest r) {
        User me = currentUser.get();
        return views.detail(service.cancel(id, me, r == null ? null : r.text()), me);
    }

    @PostMapping("/{id}/assign")
    public Detail assign(@PathVariable Long id, @Valid @RequestBody AssignRequest r) {
        User me = currentUser.get();
        return views.detail(service.assign(id, me, r.technicianId(), r.override()), me);
    }

    /** Any logged-in person can say they have the same problem; returns the updated summary. */
    @PostMapping("/{id}/me-too")
    public Map<String, Object> meToo(@PathVariable Long id) {
        Incident i = service.meToo(id, currentUser.get());
        return Map.of("summary", views.summary(i), "supporters", i.getSupporterCount(),
                "affectedUsers", i.getAffectedUsers());
    }

    @PostMapping("/{id}/take")
    public Detail take(@PathVariable Long id) {
        User me = currentUser.get();
        return views.detail(service.take(id, me), me);
    }

    @PostMapping("/{id}/priority")
    public Detail priority(@PathVariable Long id, @Valid @RequestBody PriorityRequest r) {
        User me = currentUser.get();
        return views.detail(service.changePriority(id, me, r.impact(), r.urgency(), r.affectedUsers(), r.reason()), me);
    }
}
