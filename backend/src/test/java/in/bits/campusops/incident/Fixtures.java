package in.bits.campusops.incident;

import in.bits.campusops.catalog.IncidentCategory;
import in.bits.campusops.catalog.ItService;
import in.bits.campusops.catalog.Level;
import in.bits.campusops.catalog.Priority;
import in.bits.campusops.catalog.ServiceType;
import in.bits.campusops.catalog.SlaPolicy;
import in.bits.campusops.user.Student;
import in.bits.campusops.user.Technician;
import java.lang.reflect.Field;
import java.time.Instant;
import java.util.List;

/** Test helpers that build domain objects without a database. */
public final class Fixtures {

    public static final Instant T0 = Instant.parse("2026-11-20T04:30:00Z");

    private Fixtures() {
    }

    public static <T> T withId(T entity, long id) {
        Class<?> c = entity.getClass();
        while (c != null) {
            try {
                Field f = c.getDeclaredField("id");
                f.setAccessible(true);
                f.set(entity, id);
                return entity;
            } catch (NoSuchFieldException e) {
                c = c.getSuperclass();
            } catch (IllegalAccessException e) {
                throw new IllegalStateException(e);
            }
        }
        throw new IllegalStateException("No id field on " + entity.getClass());
    }

    public static IncidentCategory category(String code) {
        return withId(new IncidentCategory(code, code, null, "wifi,network", "#000"), code.hashCode() & 0xffff);
    }

    public static ItService service(String code, int criticality) {
        return withId(new ItService(code, code + " service", null, ServiceType.APPLICATION, criticality, "Campus", 100,
                null, 0, 0), code.hashCode() & 0xffff);
    }

    public static Student student(long id) {
        return withId(new Student("E" + id, "Student " + id, "e" + id + "@campus.test", "hash", T0, "CS", "Hostel"), id);
    }

    public static Technician technician(long id, List<String> skills, int max, int years) {
        return withId(new Technician("T" + id, "Tech " + id, "t" + id + "@campus.test", "hash", T0, "Ops", skills, max,
                years), id);
    }

    public static Incident incident(ItService service, Level impact, Level urgency, int users) {
        return withId(new Incident(1001, student(1), "Wi-Fi not working", "No internet in the hostel since morning",
                service, null, null, impact, urgency, users, Incident.Channel.WEB, T0), 1001);
    }

    /** An incident that has been classified and prioritised P2 with a 15 min / 120 min SLA. */
    public static Incident classifiedIncident() {
        Incident i = incident(service("WIFI", 4), Level.HIGH, Level.HIGH, 50);
        i.classify(category("NETWORK"), "TEST", 1.0, T0);
        i.applyPriority(Priority.P2, 40, "test", new SlaPolicy(null, Priority.P2, 15, 120), T0);
        return i;
    }
}
