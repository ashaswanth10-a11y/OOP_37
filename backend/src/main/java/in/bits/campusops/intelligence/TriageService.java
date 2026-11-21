package in.bits.campusops.intelligence;

import in.bits.campusops.ai.GeminiCategoryClassifier;
import in.bits.campusops.catalog.CatalogService;
import in.bits.campusops.catalog.IncidentCategory;
import in.bits.campusops.catalog.ItService;
import in.bits.campusops.settings.SettingsService;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;

/** Classifies submitted reports with Gemini when enabled, with local keywords as a fallback. */
@Service
public class TriageService {

    private final GeminiCategoryClassifier gemini;
    private final KeywordCategoryClassifier keywords;
    private final CatalogService catalog;
    private final SettingsService settings;

    public TriageService(GeminiCategoryClassifier gemini,
                         KeywordCategoryClassifier keywords, CatalogService catalog, SettingsService settings) {
        this.gemini = gemini;
        this.keywords = keywords;
        this.catalog = catalog;
        this.settings = settings;
    }

    public Optional<CategoryClassifier.Suggestion> suggest(String text, ItService service) {
        List<IncidentCategory> categories = catalog.activeCategories();
        if (aiAvailable()) {
            Optional<CategoryClassifier.Suggestion> suggestion = gemini.classify(text, service, categories);
            if (suggestion.isPresent()) {
                return suggestion;
            }
        }
        return keywords.classify(text, service, categories);
    }

    /** Quick suggestion for the report form while the user is typing; never calls an external API. */
    public Optional<CategoryClassifier.Suggestion> preview(String text, ItService service) {
        return keywords.classify(text, service, catalog.activeCategories());
    }

    public boolean aiAvailable() {
        return gemini.isEnabled() && settings.get().isAiTriage();
    }
}
