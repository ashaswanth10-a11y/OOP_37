package in.bits.campusops;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;

/** End-to-end workflows through the REST API with the seeded campus. */
@SpringBootTest
@AutoConfigureMockMvc
class IncidentWorkflowIntegrationTest extends ApiSupport {

    @Test
    @DisplayName("TC23 Workflow 1+2: report -> auto classify/prioritise/assign -> investigate -> resolve -> verify -> close")
    void fullLifecycle() throws Exception {
        String student = bearer("rohan");
        long lab = serviceId("LAB-NET");
        MvcResult created = mvc.perform(post("/api/incidents").header("Authorization", student)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(report("No network on F-Block lab PCs", "All PCs in F107 show no network connection and the switch light is off",
                                lab, "HIGH", "HIGH", 40, true)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary.status").value("ASSIGNED"))
                .andExpect(jsonPath("$.summary.category.code").value("NETWORK"))
                .andReturn();
        JsonNode detail = json(created);
        long id = detail.at("/summary/id").asLong();
        assertThat(detail.at("/summary/priority").asString()).isIn("P1", "P2");
        assertThat(detail.at("/sla/resolutionDue").isNull()).isFalse();
        String techHandle = detail.at("/summary/assignee/code").asString();
        assertThat(techHandle).isNotBlank();

        String tech = bearer(handleForAssignee(detail.at("/summary/assignee/name").asString()));
        mvc.perform(post("/api/incidents/" + id + "/acknowledge").header("Authorization", tech))
                .andExpect(status().isOk()).andExpect(jsonPath("$.summary.status").value("ACKNOWLEDGED"));
        mvc.perform(post("/api/incidents/" + id + "/start").header("Authorization", tech))
                .andExpect(status().isOk()).andExpect(jsonPath("$.summary.status").value("IN_PROGRESS"));
        mvc.perform(post("/api/incidents/" + id + "/resolve").header("Authorization", tech)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"summary\":\"Replaced the uplink cable\",\"rootCause\":\"Faulty cable\",\"code\":\"FIXED\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.summary.status").value("RESOLVED"));
        mvc.perform(post("/api/incidents/" + id + "/verify").header("Authorization", student)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"rating\":5,\"comment\":\"works\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.summary.status").value("VERIFIED"));
        MvcResult closed = mvc.perform(post("/api/incidents/" + id + "/close").header("Authorization", tech))
                .andExpect(status().isOk()).andExpect(jsonPath("$.summary.status").value("CLOSED")).andReturn();

        List<String> actions = new ArrayList<>();
        json(closed).get("timeline").forEach(t -> actions.add(t.get("action").asString()));
        assertThat(actions).contains("CREATED", "CLASSIFIED", "PRIORITY_CALCULATED", "ASSIGNED", "RESOLVED",
                "FEEDBACK_GIVEN", "CLOSED");
    }

    @Test
    @DisplayName("TC24 Reporting the same problem twice is caught; confirming a different problem is allowed")
    void duplicateOwnReport() throws Exception {
        String student = bearer("ananya");
        long vpn = serviceId("VPN");
        String body = report("VPN keeps disconnecting at night", "Campus VPN disconnects every five minutes from the hostel",
                vpn, "MEDIUM", "MEDIUM", 1, false);
        mvc.perform(post("/api/incidents").header("Authorization", student).contentType(MediaType.APPLICATION_JSON)
                .content(body)).andExpect(status().isOk());
        mvc.perform(post("/api/incidents").header("Authorization", student).contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_INCIDENT"));
        mvc.perform(post("/api/incidents").header("Authorization", student).contentType(MediaType.APPLICATION_JSON)
                .content(body.replace("\"confirmedNew\":false", "\"confirmedNew\":true"))).andExpect(status().isOk());
    }

    @Test
    @DisplayName("TC25 Workflow 3: Wi-Fi reports from several hostels are correlated to Core Switch B")
    void rootCauseCorrelation() throws Exception {
        String admin = bearer("kiran");
        reportNetworkOutages("WIFI-GAUTAM", "WIFI-KRISHNA", "WIFI-VYAS", "WIFI-MEERA");
        MvcResult problems = mvc.perform(get("/api/problems").header("Authorization", admin))
                .andExpect(status().isOk()).andReturn();
        JsonNode coreB = null;
        for (JsonNode p : json(problems)) {
            if (p.get("rootService").asString().startsWith("Core Switch B")) {
                coreB = p;
            }
        }
        assertThat(coreB).as("a problem rooted at Core Switch B").isNotNull();
        assertThat(coreB.get("incidents").size()).isGreaterThanOrEqualTo(3);

        MvcResult map = mvc.perform(get("/api/outage-map").header("Authorization", admin)).andReturn();
        boolean rootFlagged = false;
        for (JsonNode n : json(map).get("nodes")) {
            if (n.get("code").asString().equals("CORE-B")) {
                rootFlagged = n.get("rootCause").asBoolean() && n.get("health").asString().equals("OUTAGE");
            }
        }
        assertThat(rootFlagged).isTrue();
    }

    @Test
    @DisplayName("TC26 Search filters by status and priority")
    void searchFilters() throws Exception {
        MvcResult r = mvc.perform(get("/api/incidents").param("status", "CLOSED").param("priority", "P1", "P2")
                .header("Authorization", bearer("kiran"))).andExpect(status().isOk()).andReturn();
        JsonNode items = json(r).get("items");
        assertThat(items.size()).isGreaterThan(0);
        items.forEach(i -> {
            assertThat(i.get("status").asString()).isEqualTo("CLOSED");
            assertThat(i.get("priority").asString()).isIn("P1", "P2");
        });
    }

    @Test
    @DisplayName("TC27 Unknown service is a 404 with code SERVICE_NOT_FOUND")
    void unknownService() throws Exception {
        mvc.perform(post("/api/incidents").header("Authorization", bearer("aarav")).contentType(MediaType.APPLICATION_JSON)
                        .content(report("Something is broken here", "Something is broken in a service that does not exist", 99999,
                                "LOW", "LOW", 1, true)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SERVICE_NOT_FOUND"));
    }

    @Test
    @DisplayName("TC28 Invalid input is rejected with field errors")
    void invalidInput() throws Exception {
        mvc.perform(post("/api/incidents").header("Authorization", bearer("aarav")).contentType(MediaType.APPLICATION_JSON)
                        .content(report("Hi", "short", serviceId("ERP"), "LOW", "LOW", 0, true)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"))
                .andExpect(jsonPath("$.fieldErrors.title").exists())
                .andExpect(jsonPath("$.fieldErrors.affectedUsers").exists());
    }

    @Test
    @DisplayName("TC38 'Me too' counts affected people once per person; the reporter cannot me-too their own incident")
    void meToo() throws Exception {
        MvcResult created = mvc.perform(post("/api/incidents").header("Authorization", bearer("diya"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(report("Printer in library prints blank pages", "The library printer feeds paper but every page comes out blank",
                                serviceId("PRINT"), "LOW", "LOW", 1, true)))
                .andExpect(status().isOk()).andReturn();
        long id = json(created).at("/summary/id").asLong();
        mvc.perform(post("/api/incidents/" + id + "/me-too").header("Authorization", bearer("kabir")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.supporters").value(1))
                .andExpect(jsonPath("$.affectedUsers").value(2));
        mvc.perform(post("/api/incidents/" + id + "/me-too").header("Authorization", bearer("kabir")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.supporters").value(1));
        mvc.perform(post("/api/incidents/" + id + "/me-too").header("Authorization", bearer("diya")))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("INVALID_STATE_TRANSITION"));
    }

    @Test
    @DisplayName("TC39 Fest-load scenario: academic-block network reports point at Core Switch A")
    void festLoad() throws Exception {
        String admin = bearer("kiran");
        reportNetworkOutages("LAN-ACAD", "WIFI-LIB", "LAB-NET");
        boolean found = false;
        for (JsonNode p : json(mvc.perform(get("/api/problems").header("Authorization", admin)).andReturn())) {
            found |= p.get("rootService").asString().startsWith("Core Switch A");
        }
        assertThat(found).isTrue();
    }

    private static String handleForAssignee(String fullName) {
        return fullName.split(" ")[0].toLowerCase();
    }
}
