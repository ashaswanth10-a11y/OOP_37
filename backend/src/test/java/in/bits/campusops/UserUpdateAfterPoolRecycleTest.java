package in.bits.campusops;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.zaxxer.hikari.HikariDataSource;
import javax.sql.DataSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.annotation.DirtiesContext;

/**
 * The connection pool retires connections after 30 minutes. With H2, a check constraint created on
 * a connection that was later closed could no longer be evaluated, so every update to a user row failed.
 */
@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class UserUpdateAfterPoolRecycleTest extends ApiSupport {

    @Autowired
    DataSource dataSource;

    @Test
    @DisplayName("TC42 Users can still be updated after the pool replaces the connection that created the schema")
    void savePreferencesAfterPoolRecycle() throws Exception {
        String aarav = bearer("aarav");
        // closes every idle connection, including the one Hibernate used to create the tables
        ((HikariDataSource) dataSource).getHikariPoolMXBean().softEvictConnections();

        mvc.perform(put("/api/users/me/preferences").header("Authorization", aarav)
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("{\"emailNotifications\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.emailNotifications").value(false));
    }
}
