package in.bits.campusops.incident;

import in.bits.campusops.assignment.AssignmentService;
import in.bits.campusops.assignment.AssignmentStrategy.Recommendation;
import in.bits.campusops.catalog.Asset;
import in.bits.campusops.catalog.CatalogService;
import in.bits.campusops.catalog.IncidentCategory;
import in.bits.campusops.catalog.ItService;
import in.bits.campusops.catalog.Level;
import in.bits.campusops.catalog.SlaPolicy;
import in.bits.campusops.common.Exceptions.DuplicateIncidentException;
import in.bits.campusops.common.Exceptions.InvalidInputException;
import in.bits.campusops.common.Exceptions.MissingSlaConfigurationException;
import in.bits.campusops.common.Exceptions.ResourceNotFoundException;
import in.bits.campusops.common.Exceptions.TechnicianUnavailableException;
import in.bits.campusops.common.TimeProvider;
import in.bits.campusops.events.IncidentEventPublisher;
import in.bits.campusops.events.IncidentEventType;
import in.bits.campusops.incident.IncidentPolicy.Action;
import in.bits.campusops.intelligence.CategoryClassifier;
import in.bits.campusops.intelligence.SimilarityService;
import in.bits.campusops.priority.PriorityService;
import in.bits.campusops.priority.PriorityStrategy;
import in.bits.campusops.problem.CorrelationEngine;
import in.bits.campusops.problem.ProblemRecord;
import in.bits.campusops.settings.SettingsService;
import in.bits.campusops.user.Technician;
import in.bits.campusops.user.User;
import in.bits.campusops.user.UserService;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Application service for the incident lifecycle. Orchestrates the domain objects and
 * strategies; the rules themselves live in {@link Incident}, its states and the policy.
 */
@Service
@Transactional
public class IncidentService {

    private static final DateTimeFormatter HHMM = DateTimeFormatter.ofPattern("dd MMM HH:mm")
            .withZone(ZoneId.of("Asia/Kolkata"));

    public record ReportCommand(String title, String description, Long serviceId, String assetTag, Long categoryId,
                                Level impact, Level urgency, Integer affectedUsers, String location,
                                Incident.Channel channel, boolean confirmedNew) {
    }

    private final IncidentRepository incidents;
    private final IncidentFactory factory;
    private final IncidentPolicy policy;
    private final CatalogService catalog;
    private final PriorityService priority;
    private final AssignmentService assignment;
    private final in.bits.campusops.intelligence.TriageService triage;
    private final SimilarityService similarity;
    private final CorrelationEngine correlation;
    private final SettingsService settings;
    private final UserService users;
    private final IncidentEventPublisher events;
    private final TimeProvider time;

    public IncidentService(IncidentRepository incidents, IncidentFactory factory, IncidentPolicy policy,
                           CatalogService catalog, PriorityService priority, AssignmentService assignment,
                           in.bits.campusops.intelligence.TriageService triage, SimilarityService similarity,
                           CorrelationEngine correlation, SettingsService settings, UserService users,
                           IncidentEventPublisher events, TimeProvider time) {
        this.incidents = incidents;
        this.factory = factory;
        this.policy = policy;
        this.catalog = catalog;
        this.priority = priority;
        this.assignment = assignment;
        this.triage = triage;
        this.similarity = similarity;
        this.correlation = correlation;
        this.settings = settings;
        this.users = users;
        this.events = events;
        this.time = time;
    }

    // ---- reporting: create -> classify -> prioritise -> SLA -> duplicates -> assign -> correlate --------

    public Incident report(ReportCommand cmd, User reporter) {
        Asset asset = cmd.assetTag() == null || cmd.assetTag().isBlank() ? null : catalog.requireAsset(cmd.assetTag());
        ItService service = asset != null ? asset.getService() : catalog.requireService(cmd.serviceId());
        String text = (cmd.title() == null ? "" : cmd.title()) + " " + (cmd.description() == null ? "" : cmd.description());

        List<SimilarityService.Match> duplicates = similarity.findDuplicates(text, service, null,
                settings.get().getDuplicateThreshold());
        Optional<SimilarityService.Match> own = duplicates.stream()
                .filter(m -> m.incident().isReportedBy(reporter) && m.score() >= 0.75).findFirst();
        if (own.isPresent() && !cmd.confirmedNew()) {
            throw new DuplicateIncidentException(own.get().incident().getNumber());
        }

        String location = cmd.location() != null && !cmd.location().isBlank() ? cmd.location()
                : asset != null ? asset.getLocation() : null;
        Incident incident = incidents.save(factory.create(reporter, cmd.title(), cmd.description(), service, asset,
                location, cmd.impact(), cmd.urgency(), cmd.affectedUsers(), asset != null && cmd.channel() == null
                        ? Incident.Channel.QR : cmd.channel()));
        events.publish(IncidentEventType.CREATED, incident, reporter, null, IncidentStatus.NEW,
                incident.getNumber() + " reported by " + reporter.getCode() + " via " + incident.getChannel()
                        + " on " + service.getName());

        classify(incident, cmd.categoryId(), asset);
        prioritise(incident, null, "Priority calculated");

        if (!duplicates.isEmpty()) {
            SimilarityService.Match top = duplicates.get(0);
            incident.flagPossibleDuplicate(top.incident(), top.score());
            events.publish(IncidentEventType.DUPLICATE_DETECTED, incident, null,
                    "Possible duplicate of " + top.incident().getNumber() + " (score " + top.score() + "): "
                            + top.reason());
        }

        autoAssign(incident);
        correlation.correlate(incident);
        return incident;
    }

    private void classify(Incident incident, Long categoryId, Asset asset) {
        IncidentCategory category;
        String by;
        Double confidence;
        String reason;
        if (categoryId != null) {
            category = catalog.requireCategory(categoryId);
            by = "USER";
            confidence = 1.0;
            reason = "chosen by the reporter";
        } else if (asset != null && asset.getCategory() != null) {
            category = asset.getCategory();
            by = "ASSET";
            confidence = 0.95;
            reason = "from asset " + asset.getTag();
        } else {
            Optional<CategoryClassifier.Suggestion> s = triage.suggest(incident.text(), incident.getService());
            if (s.isPresent()) {
                category = s.get().category();
                by = s.get().method();
                confidence = s.get().confidence();
                reason = s.get().reason();
            } else {
                category = incident.getService().getDefaultCategory();
                if (category == null) {
                    category = catalog.activeCategories().stream().findFirst()
                            .orElseThrow(() -> new InvalidInputException("No incident categories are configured"));
                }
                by = "DEFAULT";
                confidence = 0.3;
                reason = "no keywords matched; used the service default";
            }
        }
        IncidentStatus from = incident.getStatus();
        incident.classify(category, by, confidence, time.now());
        events.publish(IncidentEventType.CLASSIFIED, incident, null, from, incident.getStatus(),
                "Classified as " + category.getName() + " by " + by
                        + (confidence == null ? "" : " (" + Math.round(confidence * 100) + "% confident)")
                        + ": " + reason);
    }

    private void prioritise(Incident incident, User actor, String verb) {
        PriorityStrategy.Assessment a = priority.assess(new PriorityStrategy.Input(incident.getImpact(),
                incident.getUrgency(), incident.getService(), incident.getAffectedUsers()));
        SlaPolicy slaPolicy = null;
        String slaText;
        try {
            slaPolicy = catalog.policyFor(incident.getService(), a.priority());
            slaText = "";
        } catch (MissingSlaConfigurationException ex) {
            slaText = " WARNING: " + ex.getMessage();
        }
        incident.applyPriority(a.priority(), a.score(), a.rationale(), slaPolicy, time.now());
        if (slaPolicy != null) {
            slaText = " SLA (" + slaPolicy.displayName() + "): respond by "
                    + HHMM.format(incident.getSla().getResponseDue()) + ", resolve by "
                    + HHMM.format(incident.getSla().getResolutionDue());
        }
        events.publish(actor == null ? IncidentEventType.PRIORITY_CALCULATED : IncidentEventType.PRIORITY_CHANGED,
                incident, actor, verb + ": " + a.priority() + " " + a.priority().label() + " (score " + a.score()
                        + " via " + priority.active().key() + "). " + a.rationale() + "." + slaText);
    }

    private void autoAssign(Incident incident) {
        try {
            Recommendation pick = assignment.choose(incident);
            IncidentStatus from = incident.getStatus();
            incident.assignTo(pick.technician(), assignment.active().key(), time.now());
            events.publish(IncidentEventType.ASSIGNED, incident, null, from, incident.getStatus(),
                    "Assigned to " + pick.technician().getCode() + " " + pick.technician().getFullName() + " by "
                            + assignment.active().key() + " strategy: " + pick.reason());
        } catch (TechnicianUnavailableException ex) {
            events.publish(IncidentEventType.ASSIGNMENT_FAILED, incident, null, ex.getMessage());
        }
    }

    // ---- technician workflow ----------------------------------------------------

    public Incident acknowledge(Long id, User user) {
        Incident i = require(id);
        policy.require(user, i, Action.ACKNOWLEDGE);
        return move(i, user, IncidentStatus.ACKNOWLEDGED, user.getCode() + " acknowledged the incident");
    }

    public Incident start(Long id, User user) {
        Incident i = require(id);
        policy.require(user, i, Action.START);
        String text = i.getStatus() == IncidentStatus.AWAITING_INFO ? "Investigation resumed" : "Investigation started";
        return move(i, user, IncidentStatus.IN_PROGRESS, text);
    }

    public Incident requestInfo(Long id, User user, String question) {
        Incident i = require(id);
        policy.require(user, i, Action.REQUEST_INFO);
        requireText(question, "Write the question for the reporter");
        i.addNote(user, WorkNote.Type.INFO_REQUEST, question, false, time.now());
        IncidentStatus from = i.getStatus();
        i.transitionTo(IncidentStatus.AWAITING_INFO, time.now());
        events.publish(IncidentEventType.INFO_REQUESTED, i, user, from, i.getStatus(), question);
        return i;
    }

    public Incident provideInfo(Long id, User user, String answer) {
        Incident i = require(id);
        policy.require(user, i, Action.PROVIDE_INFO);
        requireText(answer, "Write your reply");
        i.addNote(user, WorkNote.Type.INFO_RESPONSE, answer, false, time.now());
        IncidentStatus from = i.getStatus();
        i.transitionTo(IncidentStatus.IN_PROGRESS, time.now());
        events.publish(IncidentEventType.INFO_PROVIDED, i, user, from, i.getStatus(), answer);
        return i;
    }

    public Incident addNote(Long id, User user, String text, boolean internal) {
        Incident i = require(id);
        policy.require(user, i, Action.ADD_NOTE);
        requireText(text, "The note is empty");
        boolean staff = user.isStaff();
        WorkNote.Type type = staff ? WorkNote.Type.INVESTIGATION : WorkNote.Type.COMMENT;
        i.addNote(user, type, text, staff && internal, time.now());
        events.publish(IncidentEventType.NOTE_ADDED, i, user,
                (staff && internal ? "Internal note: " : "Note: ") + abbreviate(text));
        return i;
    }

    public Incident resolve(Long id, User user, String summary, String rootCause, String code) {
        Incident i = require(id);
        policy.require(user, i, Action.RESOLVE);
        Resolution resolution;
        try {
            resolution = new Resolution(summary, rootCause, code, user.getFullName());
        } catch (IllegalArgumentException ex) {
            throw new InvalidInputException(ex.getMessage());
        }
        IncidentStatus from = i.getStatus();
        i.resolve(resolution, time.now());
        events.publish(IncidentEventType.RESOLVED, i, user, from, i.getStatus(),
                "Resolved: " + abbreviate(summary) + (rootCause == null || rootCause.isBlank() ? "" : " Root cause: " + rootCause));
        return i;
    }

    // ---- reporter workflow ----------------------------------------------------

    public Incident verify(Long id, User user, int rating, String comment) {
        Incident i = require(id);
        policy.require(user, i, Action.VERIFY);
        Feedback feedback;
        try {
            feedback = new Feedback(rating, comment);
        } catch (IllegalArgumentException ex) {
            throw new InvalidInputException(ex.getMessage());
        }
        IncidentStatus from = i.getStatus();
        i.verify(feedback, time.now());
        events.publish(IncidentEventType.FEEDBACK_GIVEN, i, user, from, i.getStatus(),
                "Fix confirmed, rated " + rating + "/5" + (comment == null || comment.isBlank() ? "" : ": " + comment));
        return i;
    }

    public Incident reopen(Long id, User user, String reason) {
        Incident i = require(id);
        policy.require(user, i, Action.REOPEN);
        requireText(reason, "Tell the technician what is still wrong");
        i.addNote(user, WorkNote.Type.COMMENT, "Reopened: " + reason, false, time.now());
        IncidentStatus from = i.getStatus();
        i.transitionTo(IncidentStatus.REOPENED, time.now());
        events.publish(IncidentEventType.REOPENED, i, user, from, i.getStatus(), "Reopened: " + reason);
        if (i.getAssignee() != null && i.getAssignee().isAvailable()) {
            IncidentStatus before = i.getStatus();
            i.assignTo(i.getAssignee(), "REOPEN", time.now());
            events.publish(IncidentEventType.ASSIGNED, i, null, before, i.getStatus(),
                    "Returned to " + i.getAssignee().getFullName() + " after reopening");
        } else {
            autoAssign(i);
        }
        return i;
    }

    public Incident close(Long id, User user) {
        Incident i = require(id);
        policy.require(user, i, Action.CLOSE);
        IncidentStatus from = i.getStatus();
        i.transitionTo(IncidentStatus.CLOSED, time.now());
        events.publish(IncidentEventType.CLOSED, i, user, from, i.getStatus(), "Incident closed by " + user.getCode());
        return i;
    }

    public Incident cancel(Long id, User user, String reason) {
        Incident i = require(id);
        policy.require(user, i, Action.CANCEL);
        IncidentStatus from = i.getStatus();
        i.transitionTo(IncidentStatus.CANCELLED, time.now());
        events.publish(IncidentEventType.STATUS_CHANGED, i, user, from, i.getStatus(),
                "Cancelled" + (reason == null || reason.isBlank() ? "" : ": " + reason));
        return i;
    }

    /**
     * "Me too": another person confirms they have the same problem. More affected people
     * can raise the priority automatically (the SU Connect up-vote idea, applied to IT).
     */
    public Incident meToo(Long id, User user) {
        Incident i = require(id);
        if (!i.addSupporter(user, time.now())) {
            return i;
        }
        events.publish(IncidentEventType.ME_TOO, i, user, user.getCode() + " has the same problem ("
                + (i.getSupporterCount() + 1) + " people have reported it, " + i.getAffectedUsers() + " affected)");
        var before = i.getPriority();
        var a = priority.assess(new PriorityStrategy.Input(i.getImpact(), i.getUrgency(), i.getService(),
                i.getAffectedUsers()));
        if (before != null && a.priority().ordinal() < before.ordinal()) {
            prioritise(i, null, "Raised automatically after " + i.getSupporterCount() + " \"me too\" reports");
        }
        return i;
    }

    // ---- supervisor actions ---------------------------------------------------

    public Incident assign(Long id, User user, Long technicianId, boolean override) {
        Incident i = require(id);
        policy.require(user, i, Action.ASSIGN);
        Technician t = users.requireTechnician(technicianId);
        assignment.checkManual(t, override);
        IncidentStatus from = i.getStatus();
        i.assignTo(t, "MANUAL", time.now());
        events.publish(IncidentEventType.ASSIGNED, i, user, from, i.getStatus(),
                "Assigned to " + t.getCode() + " " + t.getFullName() + " by " + user.getCode()
                        + (override ? " (workload limit overridden)" : ""));
        return i;
    }

    public Incident take(Long id, User user) {
        Incident i = require(id);
        policy.require(user, i, Action.TAKE);
        Technician t = users.requireTechnician(user.getId());
        assignment.checkManual(t, false);
        IncidentStatus from = i.getStatus();
        i.assignTo(t, "SELF", time.now());
        events.publish(IncidentEventType.ASSIGNED, i, user, from, i.getStatus(), t.getFullName() + " took the incident");
        return i;
    }

    public Incident changePriority(Long id, User user, Level impact, Level urgency, int affectedUsers, String reason) {
        Incident i = require(id);
        policy.require(user, i, Action.CHANGE_PRIORITY);
        requireText(reason, "Give a reason for changing the priority");
        i.reassess(impact, urgency, affectedUsers);
        prioritise(i, user, "Priority reassessed by " + user.getCode() + " (" + reason + ")");
        return i;
    }

    /** Resolves every open incident linked to a problem with one fix, walking each through its lifecycle. */
    public int resolveLinked(ProblemRecord problem, String summary, User actor) {
        int count = 0;
        for (Incident i : incidents.findByProblemIdOrderByCreatedAtAsc(problem.getId())) {
            // unassigned or reopened incidents are left for a person to handle
            if (!i.isOpen() || i.getAssignee() == null) {
                continue;
            }
            Instant now = time.now();
            if (i.getStatus() == IncidentStatus.ASSIGNED) {
                move(i, actor, IncidentStatus.ACKNOWLEDGED, "Acknowledged as part of " + problem.getNumber());
            }
            if (i.getStatus() == IncidentStatus.ACKNOWLEDGED) {
                move(i, actor, IncidentStatus.IN_PROGRESS, "Worked under " + problem.getNumber());
            }
            if (i.getStatus() == IncidentStatus.IN_PROGRESS || i.getStatus() == IncidentStatus.AWAITING_INFO) {
                IncidentStatus from = i.getStatus();
                i.resolve(new Resolution(summary, problem.getTitle(), "PROBLEM_FIX", actor.getFullName()), now);
                events.publish(IncidentEventType.RESOLVED, i, actor, from, i.getStatus(),
                        "Resolved by fixing " + problem.getNumber() + ": " + abbreviate(summary));
                count++;
            }
        }
        return count;
    }

    // ---- queries --------------------------------------------------------------------

    @Transactional(readOnly = true)
    public Incident get(Long id, User user) {
        Incident i = require(id);
        policy.requireView(user, i);
        return i;
    }

    @Transactional(readOnly = true)
    public Incident getByNumber(String number) {
        return incidents.findByNumberIgnoreCase(number)
                .orElseThrow(() -> new ResourceNotFoundException("Incident", number));
    }

    @Transactional(readOnly = true)
    public List<Incident> reportedBy(User user) {
        return incidents.findByReporterIdOrderByCreatedAtDesc(user.getId());
    }

    /** Incidents the user reported plus those they said "me too" to, newest first. */
    @Transactional(readOnly = true)
    public List<Incident> involving(User user) {
        List<Incident> all = new java.util.ArrayList<>(reportedBy(user));
        all.addAll(incidents.findSupportedBy(user.getId()));
        all.sort(java.util.Comparator.comparing(Incident::getCreatedAt).reversed());
        return all;
    }

    @Transactional(readOnly = true)
    public List<Incident> assignedTo(User user) {
        return incidents.findByAssigneeIdAndStatusInOrderByCreatedAtDesc(user.getId(),
                java.util.EnumSet.of(IncidentStatus.ASSIGNED, IncidentStatus.ACKNOWLEDGED, IncidentStatus.IN_PROGRESS,
                        IncidentStatus.AWAITING_INFO, IncidentStatus.RESOLVED, IncidentStatus.REOPENED));
    }

    @Transactional(readOnly = true)
    public Page<Incident> search(IncidentSearch search, Pageable pageable, User user) {
        IncidentSearch scoped = user.isStaff() ? search : search.forReporter(user.getId());
        return incidents.findAll(IncidentSpecifications.matching(scoped), pageable);
    }

    @Transactional(readOnly = true)
    public List<Recommendation> recommend(Long id) {
        return assignment.recommend(require(id));
    }

    public Incident require(Long id) {
        return incidents.findById(id).orElseThrow(() -> new ResourceNotFoundException("Incident", id));
    }

    private Incident move(Incident i, User user, IncidentStatus target, String description) {
        IncidentStatus from = i.getStatus();
        i.transitionTo(target, time.now());
        events.publish(IncidentEventType.STATUS_CHANGED, i, user, from, target, description);
        return i;
    }

    private static void requireText(String text, String message) {
        if (text == null || text.isBlank()) {
            throw new InvalidInputException(message);
        }
        if (text.length() > 4000) {
            throw new InvalidInputException("Keep it under 4000 characters");
        }
    }

    private static String abbreviate(String s) {
        return s.length() <= 160 ? s : s.substring(0, 157) + "...";
    }
}
