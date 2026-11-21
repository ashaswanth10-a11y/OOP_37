package in.bits.campusops;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.annotation.DirtiesContext;
import tools.jackson.databind.JsonNode;

/** Fires the Core Switch B outage, so it gets its own application context. */
@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class KnownOutageIntegrationTest extends ApiSupport {

    @BeforeEach
    void outage() throws Exception {
        if (json(mvc.perform(get("/api/public/status")).andReturn()).get("problems").isEmpty()) {
            reportNetworkOutages("WIFI-GAUTAM", "WIFI-KRISHNA", "WIFI-VYAS", "WIFI-MEERA");
        }
    }

    @Test
    @DisplayName("TC44 The public status page lists a known outage with every downstream service it affects")
    void statusListsKnownOutage() throws Exception {
        JsonNode s = json(mvc.perform(get("/api/public/status")).andReturn());
        JsonNode p = s.get("problems").get(0);
        assertThat(p.get("rootService").asString()).contains("Core Switch B");
        assertThat(p.get("followIncidentNumber").asString()).startsWith("INC-");
        long gautam = serviceId("WIFI-GAUTAM");
        boolean covered = false;
        for (JsonNode id : p.get("affectedServiceIds")) {
            covered |= id.asLong() == gautam;
        }
        assertThat(covered).as("Gautam Bhavan Wi-Fi is downstream of Core Switch B").isTrue();
    }

    @Test
    @DisplayName("TC46 Everyone who said \"Me too\" is notified when the outage is fixed")
    void supportersHearAboutTheFix() throws Exception {
        JsonNode known = json(mvc.perform(get("/api/public/status")).andReturn()).get("problems").get(0);
        long follow = known.get("followIncidentId").asLong();
        String lakshmi = bearer("lakshmi");
        mvc.perform(post("/api/incidents/" + follow + "/me-too").header("Authorization", lakshmi))
                .andExpect(status().isOk());

        long problemId = 0;
        for (JsonNode p : json(mvc.perform(get("/api/problems").header("Authorization", bearer("kiran"))).andReturn())) {
            if (p.get("number").asString().equals(known.get("number").asString())) {
                problemId = p.get("id").asLong();
            }
        }
        mvc.perform(post("/api/problems/" + problemId + "/resolve").header("Authorization", bearer("priya"))
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("{\"summary\":\"Replaced the failed line card in Core Switch B\"}"))
                .andExpect(status().isOk());

        boolean told = false;
        for (JsonNode n : json(mvc.perform(get("/api/notifications").header("Authorization", lakshmi)).andReturn())
                .get("items")) {
            told |= n.get("title").asString().endsWith(" fixed")
                    && n.get("body").asString().contains("line card");
        }
        assertThat(told).as("the supporter got a 'fixed' notification").isTrue();
    }
}
