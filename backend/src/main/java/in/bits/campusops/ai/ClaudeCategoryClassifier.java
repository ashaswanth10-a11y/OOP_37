package in.bits.campusops.ai;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.OutputConfig;
import in.bits.campusops.catalog.IncidentCategory;
import in.bits.campusops.catalog.ItService;
import in.bits.campusops.intelligence.CategoryClassifier;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Adapter over the Claude API (official Anthropic Java SDK). Reads the report and picks
 * one of the configured categories. Returns empty when no API key is set, the call fails
 * or the model declines, so the keyword classifier takes over and the workflow never stops.
 */
@Component
public class ClaudeCategoryClassifier implements CategoryClassifier {

    private static final Logger log = LoggerFactory.getLogger(ClaudeCategoryClassifier.class);

    private final AnthropicClient client;
    private final String model;
    private final ObjectMapper mapper;

    public ClaudeCategoryClassifier(@Value("${campusops.ai.api-key:}") String apiKey,
                                    @Value("${campusops.ai.model:claude-opus-5-5}") String model,
                                    ObjectMapper mapper) {
        this.model = model;
        this.mapper = mapper;
        this.client = apiKey == null || apiKey.isBlank() ? null
                : AnthropicOkHttpClient.builder().apiKey(apiKey.trim()).timeout(Duration.ofSeconds(20)).maxRetries(1)
                        .build();
    }

    public boolean isEnabled() {
        return client != null;
    }

    @Override
    public String name() {
        return "AI";
    }

    @Override
    public Optional<Suggestion> classify(String text, ItService service, List<IncidentCategory> categories) {
        if (client == null || categories.isEmpty()) {
            return Optional.empty();
        }
        String options = categories.stream()
                .map(c -> "- " + c.getCode() + ": " + c.getName()
                        + (c.getDescription() == null ? "" : " (" + c.getDescription() + ")"))
                .collect(Collectors.joining("\n"));
        String prompt = """
                You triage IT incidents for a university campus IT helpdesk.
                Pick the single best category for the incident below.

                Categories:
                %s

                Affected service: %s

                Incident report:
                <report>
                %s
                </report>

                Reply with only a JSON object on one line, no other text:
                {"code": "<one category code from the list>", "confidence": <0.0 to 1.0>, "reason": "<one short sentence>"}
                """.formatted(options, service == null ? "unknown" : service.getName(), text);
        try {
            MessageCreateParams params = MessageCreateParams.builder()
                    .model(model)
                    .maxTokens(1024L)
                    .outputConfig(OutputConfig.builder().effort(OutputConfig.Effort.LOW).build())
                    .addUserMessage(prompt)
                    .build();
            Message response = client.messages().create(params);
            if (response.stopReason().map(r -> r.toString().equalsIgnoreCase("refusal")).orElse(false)) {
                log.info("Claude declined to classify; falling back to keywords");
                return Optional.empty();
            }
            String reply = response.content().stream()
                    .flatMap(block -> block.text().stream())
                    .map(t -> t.text())
                    .collect(Collectors.joining())
                    .trim();
            int start = reply.indexOf('{');
            int end = reply.lastIndexOf('}');
            if (start < 0 || end <= start) {
                return Optional.empty();
            }
            JsonNode json = mapper.readTree(reply.substring(start, end + 1));
            String code = json.path("code").asString("").trim().toUpperCase();
            double confidence = Math.max(0, Math.min(1, json.path("confidence").asDouble(0.7)));
            String reason = json.path("reason").asString("");
            return categories.stream()
                    .filter(c -> c.getCode().equals(code))
                    .findFirst()
                    .map(c -> new Suggestion(c, Math.round(confidence * 100) / 100.0, name(), reason));
        } catch (Exception ex) {
            log.warn("Claude classification failed, using keyword classifier: {}", ex.getMessage());
            return Optional.empty();
        }
    }
}
