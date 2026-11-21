package in.bits.campusops;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;

@SpringBootTest
@AutoConfigureMockMvc
class IncidentPersistenceIntegrationTest extends ApiSupport {
    @Test
    @DisplayName("TC43 An employee can retrieve a reported incident after signing in again")
    void reportSurvivesNewLogin() throws Exception {
        String body = report("Printer jammed in library", "The library printer keeps jamming",
                serviceId("PRINT"), "LOW", "LOW", 5, true);
        var created = mvc.perform(post("/api/incidents").header("Authorization", bearer("aarav"))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andReturn();
        var summary = json(created).get("summary");
        mvc.perform(get("/api/incidents/" + summary.get("id").asLong()).header("Authorization", bearer("aarav")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary.number").value(summary.get("number").asString()));
    }
}
