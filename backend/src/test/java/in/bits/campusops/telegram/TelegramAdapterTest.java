package in.bits.campusops.telegram;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.json.JsonMapper;

class TelegramAdapterTest {

    @Test
    void pollsPrivateMessagesAndAdvancesPastUnsupportedUpdates() throws Exception {
        HttpClient http = mock(HttpClient.class);
        HttpResponse<?> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(200);
        doReturn("""
                {"ok":true,"result":[
                  {"update_id":10,"message":{"chat":{"id":42,"type":"private"},"text":"/services","from":{"first_name":"Aarav"}}},
                  {"update_id":11,"message":{"chat":{"id":-55,"type":"group"},"text":"/start 123456"}},
                  {"update_id":12,"message":{"chat":{"id":42,"type":"private"},"photo":[]}}
                ]}
                """).when(response).body();
        doReturn(response).when(http).send(any(HttpRequest.class), any());

        var adapter = new TelegramAdapter("123:test-token", JsonMapper.builder().build(), http);
        assertThat(adapter.getUpdates(10)).containsExactly(
                new TelegramAdapter.Update(10, "42", "/services", "Aarav"),
                new TelegramAdapter.Update(11, null, null, null),
                new TelegramAdapter.Update(12, null, null, null));

        var request = ArgumentCaptor.forClass(HttpRequest.class);
        verify(http).send(request.capture(), any());
        assertThat(request.getValue().uri().getPath()).isEqualTo("/bot123:test-token/getUpdates");
        assertThat(request.getValue().uri().getQuery()).contains("offset=10", "allowed_updates=[\"message\"]");
    }

    @Test
    void postsNotificationsToTelegram() {
        HttpClient http = mock(HttpClient.class);
        HttpResponse<?> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(200);
        doReturn(CompletableFuture.completedFuture(response)).when(http).sendAsync(any(HttpRequest.class), any());

        var adapter = new TelegramAdapter("123:test-token", JsonMapper.builder().build(), http);
        adapter.sendMessage("42", "Your incident was resolved.");

        var request = ArgumentCaptor.forClass(HttpRequest.class);
        verify(http).sendAsync(request.capture(), any());
        assertThat(request.getValue().method()).isEqualTo("POST");
        assertThat(request.getValue().uri().getPath()).isEqualTo("/bot123:test-token/sendMessage");
        assertThat(request.getValue().bodyPublisher()).isPresent();

        adapter.sendMessage("-55", "Your incident was resolved.");
        verifyNoMoreInteractions(http);
    }
}
