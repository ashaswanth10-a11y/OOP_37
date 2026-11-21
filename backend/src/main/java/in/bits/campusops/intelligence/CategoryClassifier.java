package in.bits.campusops.intelligence;

import in.bits.campusops.catalog.IncidentCategory;
import in.bits.campusops.catalog.ItService;
import java.util.List;
import java.util.Optional;

/** Suggests a category from what the reporter wrote. Several implementations, one contract. */
public interface CategoryClassifier {

    record Suggestion(IncidentCategory category, double confidence, String method, String reason) {
    }

    String name();

    Optional<Suggestion> classify(String text, ItService service, List<IncidentCategory> categories);
}
