package in.bits.campusops.intelligence;

import in.bits.campusops.catalog.ItService;
import in.bits.campusops.common.TimeProvider;
import in.bits.campusops.incident.Incident;
import in.bits.campusops.incident.IncidentRepository;
import in.bits.campusops.incident.IncidentStatus;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Duplicate detection and suggested resolutions, both built on TF-IDF similarity.
 * Duplicate score = 0.6 x text similarity + 0.25 if same service + 0.15 x closeness in time.
 */
@Service
@Transactional(readOnly = true)
public class SimilarityService {

    public record Match(Incident incident, double score, String reason) {
    }

    public record SuggestedFix(Incident source, double similarity, String resolution, String rootCause) {
    }

    private static final Duration DUPLICATE_LOOKBACK = Duration.ofHours(24);
    private static final Duration KNOWLEDGE_LOOKBACK = Duration.ofDays(365);

    private final IncidentRepository incidents;
    private final TimeProvider time;

    public SimilarityService(IncidentRepository incidents, TimeProvider time) {
        this.incidents = incidents;
        this.time = time;
    }

    /** Open incidents from the last 24 hours that look like the same problem. */
    public List<Match> findDuplicates(String text, ItService service, Long excludeId, double threshold) {
        Instant now = time.now();
        List<Incident> pool = incidents.findByStatusInAndCreatedAtAfter(IncidentStatus.OPEN, now.minus(DUPLICATE_LOOKBACK))
                .stream().filter(i -> !Objects.equals(i.getId(), excludeId)).toList();
        if (pool.isEmpty()) {
            return List.of();
        }
        List<String> corpusTexts = new ArrayList<>(pool.stream().map(Incident::text).toList());
        corpusTexts.add(text);
        TextAnalyzer.Corpus corpus = new TextAnalyzer.Corpus(corpusTexts);
        var query = corpus.vector(text);
        List<Match> matches = new ArrayList<>();
        for (Incident candidate : pool) {
            double textScore = TextAnalyzer.Corpus.cosine(query, corpus.vector(candidate.text()));
            boolean sameService = service != null && candidate.getService().getId().equals(service.getId());
            double hours = Duration.between(candidate.getCreatedAt(), now).toMinutes() / 60.0;
            double recency = hours <= 2 ? 1 : Math.max(0, 1 - (hours - 2) / 22);
            double score = 0.6 * textScore + (sameService ? 0.25 : 0) + 0.15 * recency;
            if (textScore < 0.15) {
                continue; // same service alone is not a duplicate
            }
            if (score >= threshold) {
                String reason = "%d%% text match%s, reported %s ago".formatted(Math.round(textScore * 100),
                        sameService ? ", same service" : "", humanize(Duration.between(candidate.getCreatedAt(), now)));
                matches.add(new Match(candidate, Math.round(score * 100) / 100.0, reason));
            }
        }
        matches.sort(Comparator.comparingDouble(Match::score).reversed());
        return matches.stream().limit(5).toList();
    }

    /** Resolutions from past fixed incidents that look similar: the knowledge base. */
    public List<SuggestedFix> suggestFixes(String text, ItService service, Long excludeId) {
        Instant since = time.now().minus(KNOWLEDGE_LOOKBACK);
        List<Incident> fixed = incidents.findFixedWithResolution(IncidentStatus.FIXED).stream()
                .filter(i -> !Objects.equals(i.getId(), excludeId))
                .filter(i -> i.getCreatedAt().isAfter(since))
                .toList();
        if (fixed.isEmpty()) {
            return List.of();
        }
        List<String> texts = new ArrayList<>(fixed.stream().map(Incident::text).toList());
        texts.add(text);
        TextAnalyzer.Corpus corpus = new TextAnalyzer.Corpus(texts);
        var query = corpus.vector(text);
        return fixed.stream()
                .map(i -> {
                    double sim = TextAnalyzer.Corpus.cosine(query, corpus.vector(i.text()));
                    if (service != null && i.getService().getId().equals(service.getId())) {
                        sim = Math.min(1, sim + 0.1);
                    }
                    return new SuggestedFix(i, Math.round(sim * 100) / 100.0,
                            i.getResolution().getResolutionSummary(), i.getResolution().getRootCause());
                })
                .filter(f -> f.similarity() >= 0.25)
                .sorted(Comparator.comparingDouble(SuggestedFix::similarity).reversed())
                .limit(3)
                .toList();
    }

    static String humanize(Duration d) {
        long minutes = Math.max(0, d.toMinutes());
        if (minutes < 60) {
            return minutes + " min";
        }
        if (minutes < 48 * 60) {
            return (minutes / 60) + " h";
        }
        return (minutes / 1440) + " days";
    }
}
