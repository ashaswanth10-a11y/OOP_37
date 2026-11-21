package in.bits.campusops;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
@AutoConfigureMockMvc
class IncidentPersistenceIntegrationTest extends ApiSupport {
    @Autowired
    private JdbcTemplate jdbc;

    @Test
    @DisplayName("TC43 A student can retrieve a reported incident after signing in again")
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

    @Test
    @DisplayName("An incident saved with a retired source can still be opened")
    void readsHistoricalReportingSource() throws Exception {
        String aarav = bearer("aarav");
        String body = report("Library printer stopped mid-job", "The printer stopped while printing notes",
                serviceId("PRINT"), "LOW", "LOW", 1, true);
        var created = mvc.perform(post("/api/incidents").header("Authorization", aarav)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andReturn();
        long id = json(created).at("/summary/id").asLong();
        jdbc.update("update incident set channel = ? where id = ?", "TELEGRAM", id);
        mvc.perform(get("/api/incidents/" + id).header("Authorization", aarav))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary.channel").value("LEGACY"));
    }
}
