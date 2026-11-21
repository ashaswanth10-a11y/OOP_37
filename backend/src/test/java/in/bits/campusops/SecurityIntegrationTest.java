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
import tools.jackson.databind.JsonNode;

/** Authentication, role-based authorization and ownership rules. */
@SpringBootTest
@AutoConfigureMockMvc
class SecurityIntegrationTest extends ApiSupport {

    @Test
    @DisplayName("TC29 Wrong password returns 401 INVALID_LOGIN")
    void invalidLogin() throws Exception {
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"aarav" + DOMAIN + "\",\"password\":\"wrong-password\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_LOGIN"));
    }

    @Test
    @DisplayName("TC30 API calls without a token are rejected; the public status page is open")
    void unauthenticated() throws Exception {
        mvc.perform(get("/api/incidents/mine")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/public/status")).andExpect(status().isOk())
                .andExpect(jsonPath("$.overall").exists());
    }

    @Test
    @DisplayName("TC31 A student cannot open admin settings or the dashboard")
    void roleBasedAuthorization() throws Exception {
        String student = bearer("aarav");
        mvc.perform(get("/api/admin/settings").header("Authorization", student)).andExpect(status().isForbidden());
        mvc.perform(get("/api/reports/dashboard").header("Authorization", student)).andExpect(status().isForbidden());
        mvc.perform(get("/api/reports/dashboard").header("Authorization", bearer("ravi"))).andExpect(status().isOk());
    }

    @Test
    @DisplayName("TC32 A student cannot view another person's incident or resolve their own")
    void ownership() throws Exception {
        String diya = bearer("diya");
        var created = mvc.perform(post("/api/incidents").header("Authorization", diya).contentType(MediaType.APPLICATION_JSON)
                        .content(report("Projector flickering in A102", "The projector screen keeps flickering during lectures",
                                serviceId("CLASS-AV"), "LOW", "MEDIUM", 60, true)))
                .andExpect(status().isOk()).andReturn();
        JsonNode d = json(created);
        long id = d.at("/summary/id").asLong();
        mvc.perform(get("/api/incidents/" + id).header("Authorization", bearer("kabir")))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/incidents/" + id + "/resolve").header("Authorization", diya)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"summary\":\"I fixed it myself\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED_OPERATION"));
    }

    @Test
    @DisplayName("TC33 Closing before the fix is verified is an invalid state transition (409)")
    void closeTooEarly() throws Exception {
        var created = mvc.perform(post("/api/incidents").header("Authorization", bearer("sneha"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(report("Shared drive quota full", "The accounts shared drive says the quota is full",
                                serviceId("DC-CLUSTER"), "MEDIUM", "MEDIUM", 8, true)))
                .andExpect(status().isOk()).andReturn();
        long id = json(created).at("/summary/id").asLong();
        mvc.perform(post("/api/incidents/" + id + "/close").header("Authorization", bearer("kiran")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_STATE_TRANSITION"));
    }

    @Test
    @DisplayName("TC34 Self-registration requires a campus email")
    void campusEmailOnly() throws Exception {
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullName\":\"Outsider\",\"email\":\"someone@gmail.com\",\"password\":\"longenough1\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("campus email")));
    }

    @Test
    @DisplayName("Self-registration creates a student account")
    void registersStudent() throws Exception {
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fullName":"New Student","email":"new-student%s","password":"longenough1"}
                                """.formatted(DOMAIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.role").value("STUDENT"))
                .andExpect(jsonPath("$.user.roles[0]").value("STUDENT"));
    }

    @Test
    @DisplayName("Faculty and campus employees have separate roles and access only their own tickets")
    void facultyAndEmployeeAreReporters() throws Exception {
        for (String[] account : new String[][] {{"lakshmi", "FACULTY"}, {"sneha", "EMPLOYEE"}}) {
            String token = bearer(account[0]);
            mvc.perform(get("/api/auth/me").header("Authorization", token))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.role").value(account[1]));
            mvc.perform(get("/api/incidents/mine").header("Authorization", token)).andExpect(status().isOk());
            mvc.perform(get("/api/admin/settings").header("Authorization", token)).andExpect(status().isForbidden());
            mvc.perform(get("/api/incidents/assigned").header("Authorization", token)).andExpect(status().isForbidden());
        }
    }
}
