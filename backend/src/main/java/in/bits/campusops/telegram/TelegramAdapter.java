package in.bits.campusops.telegram;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Adapter pattern: hides the Telegram Bot HTTP API behind two plain methods.
 * With no token configured it reports itself disabled and the channel is skipped.
 */
@Component
public class TelegramAdapter {

    private static final Logger log = LoggerFactory.getLogger(TelegramAdapter.class);

    public record Update(long updateId, String chatId, String text, String fromName) {
    }

    private final String token;
    private final ObjectMapper mapper;
    private final HttpClient http;

    @Autowired
    public TelegramAdapter(@Value("${campusops.telegram.bot-token:}") String token, ObjectMapper mapper) {
        this(token, mapper, HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build());
    }

    TelegramAdapter(String token, ObjectMapper mapper, HttpClient http) {
        this.token = token == null ? "" : token.trim();
        this.mapper = mapper;
        this.http = http;
    }

    public boolean isEnabled() {
        return !token.isEmpty();
    }

    /** Fire-and-forget so a slow Telegram API never holds up a web request. */
    public void sendMessage(String chatId, String text) {
        if (!isEnabled() || chatId == null || chatId.startsWith("-")) {
            return;
        }
        String form = "chat_id=" + enc(chatId) + "&text=" + enc(text);
        HttpRequest req = HttpRequest.newBuilder(URI.create(base() + "/sendMessage"))
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(form))
                .build();
        http.sendAsync(req, HttpResponse.BodyHandlers.ofString())
                .whenComplete((res, err) -> {
                    if (err != null) {
                        log.warn("Telegram send failed ({})", err.getClass().getSimpleName());
                    } else if (res.statusCode() != 200) {
                        log.warn("Telegram send returned HTTP {}", res.statusCode());
                    }
                });
    }

    /** Short poll for new messages after {@code offset}. Returns empty on any failure. */
    public List<Update> getUpdates(long offset) {
        List<Update> updates = new ArrayList<>();
        if (!isEnabled()) {
            return updates;
        }
        try {
            HttpRequest req = HttpRequest.newBuilder(URI.create(base() + "/getUpdates?timeout=0&offset=" + offset
                            + "&allowed_updates=" + enc("[\"message\"]")))
                    .timeout(Duration.ofSeconds(10)).GET().build();
            HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() != 200) {
                log.warn("Telegram getUpdates returned {}", res.statusCode());
                return updates;
            }
            JsonNode root = mapper.readTree(res.body());
            for (JsonNode u : root.path("result")) {
                JsonNode msg = u.path("message");
                // A group chat must never be linked to a CampusOps user or receive ticket updates.
                if (!"private".equals(msg.path("chat").path("type").asString("")) || !msg.has("text")) {
                    updates.add(new Update(u.path("update_id").asLong(), null, null, null));
                    continue;
                }
                updates.add(new Update(u.path("update_id").asLong(),
                        msg.path("chat").path("id").asString(),
                        msg.path("text").asString(),
                        msg.path("from").path("first_name").asString("")));
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        } catch (Exception ex) {
            log.debug("Telegram poll failed ({})", ex.getClass().getSimpleName());
        }
        return updates;
    }

    private String base() {
        return "https://api.telegram.org/bot" + token;
    }

    private static String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }
}
