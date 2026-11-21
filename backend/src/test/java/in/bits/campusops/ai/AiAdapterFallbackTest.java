package in.bits.campusops.ai;

import static org.assertj.core.api.Assertions.assertThat;

import in.bits.campusops.catalog.IncidentCategory;
import in.bits.campusops.incident.Fixtures;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class AiAdapterFallbackTest {

    private final List<IncidentCategory> categories = List.of(Fixtures.category("NETWORK"));

    @Test
    @DisplayName("TC40 Gemini without an API key stays disabled and steps aside for the keyword classifier")
    void disabledWithoutKey() {
        JsonMapper mapper = JsonMapper.builder().build();
        GeminiCategoryClassifier gemini = new GeminiCategoryClassifier("", "gemini-3.5-flash-lite", mapper);
        assertThat(gemini.isEnabled()).isFalse();
        assertThat(gemini.classify("wifi down", null, categories)).isEmpty();
    }
}
