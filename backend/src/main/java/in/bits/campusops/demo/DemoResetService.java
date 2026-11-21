package in.bits.campusops.demo;

import in.bits.campusops.common.DemoResettable;
import in.bits.campusops.realtime.SseHub;
import in.bits.campusops.seed.DemoDataSeeder;
import in.bits.campusops.sla.SlaMonitor;
import java.sql.Connection;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * Puts the system back to the freshly seeded campus so every demo run starts the same:
 * empties every table (ids restart, so the demo logins keep working), makes every
 * {@link DemoResettable} forget its in-memory state, then runs the seeder again.
 */
@Service
public class DemoResetService {

    private static final Logger log = LoggerFactory.getLogger(DemoResetService.class);

    private final DataSource dataSource;
    private final List<DemoResettable> resettables;
    private final DemoDataSeeder seeder;
    private final SlaMonitor monitor;
    private final SseHub hub;

    public DemoResetService(DataSource dataSource, List<DemoResettable> resettables, DemoDataSeeder seeder,
                            SlaMonitor monitor, SseHub hub) {
        this.dataSource = dataSource;
        this.resettables = resettables;
        this.seeder = seeder;
        this.monitor = monitor;
        this.hub = hub;
    }

    public synchronized Map<String, Object> reset() throws Exception {
        long started = System.currentTimeMillis();
        monitor.setPaused(true);
        try {
            int tables = emptyAllTables();
            resettables.forEach(DemoResettable::resetForDemo);
            seeder.seed();
            log.info("Demo reset: emptied {} tables and seeded again", tables);
            hub.broadcast("reset", Map.of());
            return Map.of("tables", tables, "millis", System.currentTimeMillis() - started);
        } finally {
            monitor.setPaused(false);
        }
    }

    private int emptyAllTables() throws Exception {
        List<String> tables = new ArrayList<>();
        boolean h2;
        try (Connection c = dataSource.getConnection()) {
            h2 = "H2".equals(c.getMetaData().getDatabaseProductName());
            try (ResultSet rs = c.getMetaData().getTables(c.getCatalog(), c.getSchema(), "%", new String[] {"TABLE"})) {
                while (rs.next()) {
                    tables.add(rs.getString("TABLE_NAME"));
                }
            }
        }
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        if (h2) {
            // H2 has no CASCADE for truncate, so foreign keys are switched off for the moment
            jdbc.execute("SET REFERENTIAL_INTEGRITY FALSE");
            try {
                for (String t : tables) {
                    jdbc.execute("TRUNCATE TABLE \"" + t + "\" RESTART IDENTITY");
                }
            } finally {
                jdbc.execute("SET REFERENTIAL_INTEGRITY TRUE");
            }
        } else if (!tables.isEmpty()) {
            jdbc.execute("TRUNCATE TABLE " + String.join(", ", tables.stream().map(t -> "\"" + t + "\"").toList())
                    + " RESTART IDENTITY CASCADE");
        }
        return tables.size();
    }
}
