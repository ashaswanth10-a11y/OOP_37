package in.bits.campusops;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import in.bits.campusops.catalog.CatalogRepositories.SlaPolicyRepository;
import in.bits.campusops.catalog.CatalogService;
import in.bits.campusops.catalog.Priority;
import in.bits.campusops.common.Exceptions.MissingSlaConfigurationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;

/** Moves the clock forward, so it gets its own application context. */
@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class SlaEscalationIntegrationTest extends ApiSupport {

    @Autowired
    CatalogService catalog;

    @Autowired
    SlaPolicyRepository policies;

    @Test
    @DisplayName("TC35 An unacknowledged P1 breaches its SLA and is escalated up the chain")
    void breachAndEscalate() throws Exception {
        var created = mvc.perform(post("/api/incidents").header("Authorization", bearer("sneha"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(report("Fee payment portal down for everyone", "Nobody can pay the semester fee, the portal shows error 500",
                                serviceId("FEE-PORTAL"), "CRITICAL", "CRITICAL", 900, true)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary.priority").value("P1"))
                .andReturn();
        long id = json(created).at("/summary/id").asLong();

        mvc.perform(post("/api/admin/demo/time-warp").param("minutes", "11").header("Authorization", bearer("kiran")))
                .andExpect(status().isOk());

        JsonNode d = json(mvc.perform(get("/api/incidents/" + id).header("Authorization", bearer("kiran"))).andReturn());
        assertThat(d.at("/sla/responseBreached").asBoolean()).isTrue();
        assertThat(d.at("/summary/escalationLevel").asInt()).isGreaterThanOrEqualTo(1);
        assertThat(d.at("/escalatedTo/name").asString()).isNotBlank();
        boolean escalated = false;
        for (JsonNode t : d.get("timeline")) {
            escalated |= t.get("action").asString().equals("ESCALATED");
        }
        assertThat(escalated).isTrue();

        JsonNode violations = json(mvc.perform(get("/api/admin/sla-violations").header("Authorization", bearer("kiran")))
                .andReturn());
        assertThat(violations.size()).isGreaterThan(0);
    }

    @Autowired
    in.bits.campusops.sla.SlaMonitor monitor;

    @Test
    @DisplayName("TC41 The scheduled SLA sweep saves its flags, so a breach is alerted and recorded only once")
    void sweepIsIdempotent() {
        monitor.scheduled();
        var first = monitor.sweep();
        var second = monitor.sweep();
        assertThat(second.warnings()).isZero();
        assertThat(second.breaches()).isZero();
        assertThat(second.escalations()).isZero();
        assertThat(first.checked()).isEqualTo(second.checked());
    }

    @Test
    @DisplayName("TC36 Manual assignment beyond a technician's limit is refused unless overridden")
    void workloadExceeded() throws Exception {
        String lead = bearer("vikram");
        long techId = 0;
        for (JsonNode t : json(mvc.perform(get("/api/users/technicians").header("Authorization", lead)).andReturn())) {
            if (t.get("code").asString().startsWith("T") && t.get("fullName").asString().startsWith("Suresh")) {
                techId = t.get("id").asLong();
            }
        }
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/users/" + techId + "/technician")
                        .header("Authorization", bearer("kiran")).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"team\":\"Desktop Support\",\"skills\":[\"HARDWARE\"],\"maxConcurrent\":1,\"experienceYears\":8,\"available\":true}"))
                .andExpect(status().isOk());
        long first = createErpIncident("Grades not visible in ERP for my section");
        long second = createErpIncident("Course registration page missing electives");
        String body = "{\"technicianId\":" + techId + ",\"override\":false}";
        mvc.perform(post("/api/incidents/" + first + "/assign").header("Authorization", lead)
                .contentType(MediaType.APPLICATION_JSON).content(body));
        mvc.perform(post("/api/incidents/" + second + "/assign").header("Authorization", lead)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("WORKLOAD_EXCEEDED"));
        mvc.perform(post("/api/incidents/" + second + "/assign").header("Authorization", lead)
                        .contentType(MediaType.APPLICATION_JSON).content(body.replace("false", "true")))
                .andExpect(status().isOk());
    }

    @Test
    @Transactional
    @DisplayName("TC37 A missing SLA policy is reported as MISSING_SLA_CONFIGURATION")
    void missingSla() {
        var print = catalog.allServices().stream().filter(s -> s.getCode().equals("PRINT")).findFirst().orElseThrow();
        policies.findByServiceIsNullAndPriority(Priority.P4).ifPresent(policies::delete);
        policies.flush();
        assertThatThrownBy(() -> catalog.policyFor(print, Priority.P4))
                .isInstanceOf(MissingSlaConfigurationException.class);
    }

    private long createErpIncident(String title) throws Exception {
        var r = mvc.perform(post("/api/incidents").header("Authorization", bearer("lakshmi"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(report(title, title + " since this morning for all students", serviceId("ERP"),
                                "MEDIUM", "MEDIUM", 30, true)))
                .andExpect(status().isOk()).andReturn();
        return json(r).at("/summary/id").asLong();
    }
}
