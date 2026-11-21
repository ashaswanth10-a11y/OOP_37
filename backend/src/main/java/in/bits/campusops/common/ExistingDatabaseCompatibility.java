package in.bits.campusops.common;

import in.bits.campusops.user.Role;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Keeps new user inserts working on databases created before the notification settings changed. */
@Component
@Order(0)
class ExistingDatabaseCompatibility implements ApplicationRunner {

    private final JdbcTemplate jdbc;
    private final boolean seedEnabled;

    ExistingDatabaseCompatibility(JdbcTemplate jdbc, @Value("${campusops.seed.enabled:true}") boolean seedEnabled) {
        this.jdbc = jdbc;
        this.seedEnabled = seedEnabled;
    }

    @Override
    public void run(ApplicationArguments args) {
        Integer requiredLegacyColumn = jdbc.queryForObject("""
                select count(*)
                from information_schema.columns
                where table_schema = current_schema
                  and lower(table_name) = 'users'
                  and lower(column_name) = 'telegram_notifications'
                  and is_nullable = 'NO'
                  and column_default is null
                """, Integer.class);
        if (requiredLegacyColumn != null && requiredLegacyColumn > 0) {
            jdbc.execute("alter table users alter column telegram_notifications set default false");
        }
        if (seedEnabled) {
            updateDemoReporter("lakshmi", Role.FACULTY, "Computer Science", "C Block 210", "Lakshmi Narayan");
            updateDemoReporter("sneha", Role.EMPLOYEE, "Accounts Office", "C Block", "Sneha Kulkarni");
        }
    }

    private void updateDemoReporter(String handle, Role role, String department, String location, String fullName) {
        jdbc.update("""
                update users set campus_role = ?, department = ?, location = ?
                where email = ? and full_name in (?, ?) and user_type = 'EMPLOYEE' and campus_role is null
                """, role.name(), department, location, handle + "@hyderabad.bits-pilani.ac.in",
                fullName, "Prof. " + fullName);
    }
}
