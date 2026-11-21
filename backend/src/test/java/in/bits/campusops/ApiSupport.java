package in.bits.campusops;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** Shared helpers for the HTTP-level integration tests. */
public abstract class ApiSupport {

    public static final String PASSWORD = "Campus@123";
    public static final String DOMAIN = "@hyderabad.bits-pilani.ac.in";

    @Autowired
    protected MockMvc mvc;

    @Autowired
    protected ObjectMapper mapper;

    protected String token(String handle) throws Exception {
        MvcResult r = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + handle + DOMAIN + "\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isOk()).andReturn();
        return json(r).get("token").asString();
    }

    protected String bearer(String handle) throws Exception {
        return "Bearer " + token(handle);
    }

    protected JsonNode json(MvcResult r) throws Exception {
        return mapper.readTree(r.getResponse().getContentAsString());
    }

    protected long serviceId(String code) throws Exception {
        MvcResult r = mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/services")
                .header("Authorization", bearer("kiran"))).andReturn();
        for (JsonNode s : json(r)) {
            if (s.get("code").asString().equals(code)) {
                return s.get("id").asLong();
            }
        }
        throw new IllegalStateException("No service " + code);
    }

    protected void reportNetworkOutages(String... serviceCodes) throws Exception {
        String[] reporters = {"aarav", "diya", "kabir", "ananya"};
        for (int i = 0; i < serviceCodes.length; i++) {
            String service = serviceCodes[i];
            String body = report("Network unavailable at " + service, "Students cannot connect to the network at " + service,
                    serviceId(service), "HIGH", "HIGH", 120, true);
            mvc.perform(post("/api/incidents").header("Authorization", bearer(reporters[i]))
                            .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isOk());
        }
    }

    protected static String report(String title, String description, long serviceId, String impact, String urgency,
                                   int users, boolean confirmedNew) {
        return """
                {"title":"%s","description":"%s","serviceId":%d,"impact":"%s","urgency":"%s","affectedUsers":%d,
                 "confirmedNew":%s}""".formatted(title, description, serviceId, impact, urgency, users, confirmedNew);
    }
}
