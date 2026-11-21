package in.bits.campusops.intelligence;

import in.bits.campusops.ai.ClaudeCategoryClassifier;
import in.bits.campusops.ai.GeminiCategoryClassifier;
import in.bits.campusops.catalog.CatalogService;
import in.bits.campusops.catalog.IncidentCategory;
import in.bits.campusops.catalog.ItService;
import in.bits.campusops.settings.SettingsService;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;

/**
 * Chain of classifiers: the AI providers that are configured (Claude, then Gemini) when AI
 * triage is switched on, and the offline keyword classifier last, which always answers.
 */
@Service
public class TriageService {

    private final ClaudeCategoryClassifier claude;
    private final GeminiCategoryClassifier gemini;
    private final KeywordCategoryClassifier keywords;
    private final CatalogService catalog;
    private final SettingsService settings;

    public TriageService(ClaudeCategoryClassifier claude, GeminiCategoryClassifier gemini,
                         KeywordCategoryClassifier keywords, CatalogService catalog, SettingsService settings) {
        this.claude = claude;
        this.gemini = gemini;
        this.keywords = keywords;
        this.catalog = catalog;
        this.settings = settings;
    }

    public Optional<CategoryClassifier.Suggestion> suggest(String text, ItService service) {
        List<IncidentCategory> categories = catalog.activeCategories();
        for (CategoryClassifier classifier : chain()) {
            Optional<CategoryClassifier.Suggestion> s = classifier.classify(text, service, categories);
            if (s.isPresent()) {
                return s;
            }
        }
        return Optional.empty();
    }

    /** Quick suggestion for the report form while the user is typing; never calls an external API. */
    public Optional<CategoryClassifier.Suggestion> preview(String text, ItService service) {
        return keywords.classify(text, service, catalog.activeCategories());
    }

    public boolean aiAvailable() {
        return claude.isEnabled() || gemini.isEnabled();
    }

    private List<CategoryClassifier> chain() {
        List<CategoryClassifier> chain = new ArrayList<>();
        if (settings.get().isAiTriage()) {
            if (claude.isEnabled()) {
                chain.add(claude);
            }
            if (gemini.isEnabled()) {
                chain.add(gemini);
            }
        }
        chain.add(keywords);
        return chain;
    }
}
