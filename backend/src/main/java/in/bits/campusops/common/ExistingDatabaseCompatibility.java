package in.bits.campusops.common;

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

    ExistingDatabaseCompatibility(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
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
    }
}
