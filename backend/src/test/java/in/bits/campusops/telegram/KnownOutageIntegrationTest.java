package in.bits.campusops.telegram;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import in.bits.campusops.ApiSupport;
import in.bits.campusops.incident.IncidentService;
import in.bits.campusops.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;

/** Fires the Core Switch B outage, so it gets its own application context. */
@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class KnownOutageIntegrationTest extends ApiSupport {

    private static final String CHAT = "chat-diya";

    @Autowired
    TelegramBot bot;

    @Autowired
    UserRepository users;

    @Autowired
    IncidentService incidents;

    @Autowired
    PlatformTransactionManager txManager;

    @BeforeEach
    void outage() throws Exception {
        if (json(mvc.perform(get("/api/public/status")).andReturn()).get("problems").isEmpty()) {
            mvc.perform(post("/api/admin/demo/scenario/core-switch-b").header("Authorization", bearer("kiran")))
                    .andExpect(status().isOk());
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

    @Test
    @DisplayName("TC45The Telegram bot points a new report on an affected service to /metoo, and /metoo adds the person")
    void botSuggestsMeToo() {
        TransactionTemplate tx = new TransactionTemplate(txManager);
        tx.executeWithoutResult(t -> users.findByEmailIgnoreCase("diya" + DOMAIN).orElseThrow().linkTelegram(CHAT));

        String reply = tx.execute(t -> bot.handle(CHAT, "/report WIFI-GAUTAM no internet in my room"));
        assertThat(reply).contains("Known outage").contains("/metoo INC-");
        String number = reply.replaceAll("(?s).*/metoo (INC-\\d+).*", "$1");

        String joined = tx.execute(t -> bot.handle(CHAT, "/metoo " + number));
        assertThat(joined).contains("Added you to " + number);
        int supporters = tx.execute(t -> incidents.getByNumber(number).getSupporterCount());
        assertThat(supporters).isGreaterThanOrEqualTo(1);

        String anyway = tx.execute(t -> bot.handle(CHAT, "/report WIFI-GAUTAM anyway my laptop wifi card is broken"));
        assertThat(anyway).startsWith("Logged INC-");
    }
}
