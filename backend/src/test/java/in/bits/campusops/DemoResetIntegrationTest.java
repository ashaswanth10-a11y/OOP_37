package in.bits.campusops;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import in.bits.campusops.incident.IncidentRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;

/** Wipes and reseeds the database, so it gets its own application context. */
@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class DemoResetIntegrationTest extends ApiSupport {

    @Autowired
    IncidentRepository incidents;

    @Test
    @DisplayName("TC43 Demo reset restores the seeded campus: same counts, numbering, clock and logins")
    void resetRestoresSeed() throws Exception {
        long seeded = incidents.count();
        String aarav = bearer("aarav");
        String kiran = bearer("kiran");

        int problems = json(mvc.perform(get("/api/problems").header("Authorization", kiran)).andReturn()).size();
        String before = createIncident(aarav);
        mvc.perform(post("/api/admin/demo/scenario/core-switch-b").header("Authorization", kiran))
                .andExpect(status().isOk());
        mvc.perform(post("/api/admin/demo/time-warp").param("minutes", "30").header("Authorization", kiran))
                .andExpect(status().isOk());
        assertThat(incidents.count()).isGreaterThan(seeded);

        mvc.perform(post("/api/admin/demo/reset").header("Authorization", aarav)).andExpect(status().isForbidden());
        mvc.perform(post("/api/admin/demo/reset").header("Authorization", kiran)).andExpect(status().isOk());

        assertThat(incidents.count()).isEqualTo(seeded);
        mvc.perform(get("/api/public/time")).andExpect(jsonPath("$.offsetMinutes").value(0));
        mvc.perform(get("/api/problems").header("Authorization", kiran)).andExpect(jsonPath("$.length()").value(problems));
        // tokens issued before the reset still point at the same people, and numbering starts over
        assertThat(createIncident(aarav)).isEqualTo(before);
    }

    private String createIncident(String token) throws Exception {
        var r = mvc.perform(post("/api/incidents").header("Authorization", token).contentType(MediaType.APPLICATION_JSON)
                        .content(report("Printer jammed in the library", "The library printer on the ground floor keeps jamming",
                                serviceId("PRINT"), "LOW", "LOW", 5, true)))
                .andExpect(status().isOk()).andReturn();
        return json(r).at("/summary/number").asString();
    }
}
