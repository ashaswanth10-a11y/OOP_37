package in.bits.campusops.intelligence;

import in.bits.campusops.catalog.IncidentCategory;
import in.bits.campusops.catalog.ItService;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * Offline classifier: counts keyword hits per category (after the same normalisation the
 * duplicate detector uses) and gives the affected service's usual category a head start.
 */
@Component
public class KeywordCategoryClassifier implements CategoryClassifier {

    private static final double SERVICE_PRIOR = 1.5;

    @Override
    public String name() {
        return "KEYWORD";
    }

    @Override
    public Optional<Suggestion> classify(String text, ItService service, List<IncidentCategory> categories) {
        Set<String> terms = new HashSet<>(TextAnalyzer.terms(text));
        Map<IncidentCategory, Double> scores = new LinkedHashMap<>();
        Map<IncidentCategory, List<String>> hits = new LinkedHashMap<>();
        for (IncidentCategory c : categories) {
            double score = 0;
            List<String> matched = new ArrayList<>();
            for (String keyword : c.keywordList()) {
                for (String k : TextAnalyzer.terms(keyword)) {
                    if (terms.contains(k)) {
                        score += 1;
                        matched.add(keyword);
                        break;
                    }
                }
            }
            if (service != null && service.getDefaultCategory() != null
                    && service.getDefaultCategory().getId().equals(c.getId())) {
                score += SERVICE_PRIOR;
            }
            scores.put(c, score);
            hits.put(c, matched);
        }
        double total = scores.values().stream().mapToDouble(Double::doubleValue).sum();
        return scores.entrySet().stream()
                .filter(e -> e.getValue() > 0)
                .max(Map.Entry.comparingByValue())
                .map(e -> {
                    double confidence = Math.round(e.getValue() / total * 100) / 100.0;
                    List<String> words = hits.get(e.getKey());
                    String reason = words.isEmpty()
                            ? "Usual category for " + service.getName()
                            : "Matched: " + String.join(", ", words.stream().distinct().limit(5).toList());
                    return new Suggestion(e.getKey(), confidence, name(), reason);
                });
    }
}
