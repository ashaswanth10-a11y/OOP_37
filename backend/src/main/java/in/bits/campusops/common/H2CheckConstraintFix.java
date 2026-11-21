package in.bits.campusops.common;

import java.sql.Connection;
import java.util.List;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Works around an H2 bug. Hibernate adds {@code CHECK (col IN (...))} constraints (for example on the
 * user type discriminator). H2 keeps a reference to the connection that created such a constraint, so
 * once the pool closes that connection (after 30 minutes by default) every insert or update on the
 * table fails with "Check constraint invalid". A restart does not help when the schema is created fresh,
 * which is every boot on a free host. The application already controls these values, so on H2 we drop
 * the IN constraints. PostgreSQL is not affected and keeps them.
 */
@Component
class H2CheckConstraintFix {

    private static final Logger log = LoggerFactory.getLogger(H2CheckConstraintFix.class);

    private final DataSource dataSource;

    H2CheckConstraintFix(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @EventListener(ApplicationReadyEvent.class)
    void dropInConstraints() throws Exception {
        try (Connection c = dataSource.getConnection()) {
            if (!"H2".equals(c.getMetaData().getDatabaseProductName())) {
                return;
            }
        }
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        List<String[]> found = jdbc.query("""
                select tc.table_name, tc.constraint_name
                from information_schema.table_constraints tc
                join information_schema.check_constraints cc
                  on cc.constraint_schema = tc.constraint_schema and cc.constraint_name = tc.constraint_name
                where tc.constraint_schema = 'PUBLIC' and tc.constraint_type = 'CHECK'
                  and cc.check_clause like '% IN(%'
                """, (rs, i) -> new String[] {rs.getString(1), rs.getString(2)});
        for (String[] t : found) {
            jdbc.execute("alter table \"" + t[0] + "\" drop constraint \"" + t[1] + "\"");
        }
        if (!found.isEmpty()) {
            log.info("H2: dropped {} IN check constraint(s) to avoid the closed-connection bug", found.size());
        }
    }
}
