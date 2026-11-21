package in.bits.campusops.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import in.bits.campusops.incident.Fixtures;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.json.JsonMapper;

class GeminiCategoryClassifierTest {

    @Test
    void authenticatesAndReadsTheFinalAnswerAfterAThinkingPart() throws Exception {
        HttpClient http = mock(HttpClient.class);
        HttpResponse<?> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(200);
        doReturn("""
                {"candidates":[{"content":{"parts":[
                  {"text":"Internal analysis","thought":true},
                  {"text":"{\\"code\\":\\"network\\",\\"confidence\\":0.91,\\"reason\\":\\"Wi-Fi failure\\"}"}
                ]}}]}
                """).when(response).body();
        doReturn(response).when(http).send(any(HttpRequest.class), any());

        var classifier = new GeminiCategoryClassifier("test-key", "gemini-3.5-flash-lite",
                JsonMapper.builder().build(), http);
        var category = Fixtures.category("NETWORK");
        var result = classifier.classify("Wi-Fi unavailable", null, List.of(category)).orElseThrow();

        assertThat(result.category()).isEqualTo(category);
        assertThat(result.confidence()).isEqualTo(0.91);
        assertThat(result.reason()).isEqualTo("Wi-Fi failure");

        var request = ArgumentCaptor.forClass(HttpRequest.class);
        verify(http).send(request.capture(), any());
        assertThat(request.getValue().method()).isEqualTo("POST");
        assertThat(request.getValue().uri().getPath()).endsWith("/gemini-3.5-flash-lite:generateContent");
        assertThat(request.getValue().headers().firstValue("x-goog-api-key")).contains("test-key");
    }

    @Test
    void providerRateLimitLeavesClassificationToTheFallback() throws Exception {
        HttpClient http = mock(HttpClient.class);
        HttpResponse<?> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(429);
        doReturn(response).when(http).send(any(HttpRequest.class), any());

        var classifier = new GeminiCategoryClassifier("test-key", "gemini-3.5-flash-lite",
                JsonMapper.builder().build(), http);
        assertThat(classifier.classify("Wi-Fi unavailable", null, List.of(Fixtures.category("NETWORK"))))
                .isEmpty();
    }
}
