package in.bits.campusops.demo;

import in.bits.campusops.catalog.CatalogRepositories.ServiceRepository;
import in.bits.campusops.catalog.ItService;
import in.bits.campusops.catalog.Level;
import in.bits.campusops.common.Exceptions.InvalidInputException;
import in.bits.campusops.common.Exceptions.UnauthorizedOperationException;
import in.bits.campusops.common.TimeProvider;
import in.bits.campusops.incident.Incident;
import in.bits.campusops.incident.IncidentService;
import in.bits.campusops.sla.SlaMonitor;
import in.bits.campusops.user.Employee;
import in.bits.campusops.user.User;
import in.bits.campusops.user.UserRepository;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Demo controls for the recorded presentation: fast-forward time, fire scripted scenarios, reset. */
@RestController
@RequestMapping("/api/admin/demo")
@PreAuthorize("hasRole('SYSTEM_ADMIN')")
public class DemoController {

    private final TimeProvider time;
    private final SlaMonitor monitor;
    private final IncidentService incidents;
    private final UserRepository users;
    private final ServiceRepository services;
    private final DemoResetService resetService;
    private final boolean enabled;

    public DemoController(TimeProvider time, SlaMonitor monitor, IncidentService incidents, UserRepository users,
                          ServiceRepository services, DemoResetService resetService,
                          @Value("${campusops.demo.enabled:true}") boolean enabled) {
        this.time = time;
        this.monitor = monitor;
        this.incidents = incidents;
        this.users = users;
        this.services = services;
        this.resetService = resetService;
        this.enabled = enabled;
    }

    @PostMapping("/time-warp")
    public Map<String, Object> warp(@RequestParam int minutes) {
        requireEnabled();
        if (minutes < 1 || minutes > 24 * 60) {
            throw new InvalidInputException("Warp between 1 minute and 24 hours");
        }
        time.advance(Duration.ofMinutes(minutes));
        SlaMonitor.SweepResult r = monitor.sweep();
        return Map.of("offsetMinutes", time.offset().toMinutes(), "sweep", r);
    }

    /** Wipes everything and seeds the demo campus again, so each recording starts from the same state. */
    @PostMapping("/reset")
    public Map<String, Object> reset() throws Exception {
        requireEnabled();
        return resetService.reset();
    }

    @PostMapping("/sweep")
    public SlaMonitor.SweepResult sweep() {
        return monitor.sweep();
    }

    @PostMapping("/scenario/{name}")
    @Transactional
    public Map<String, Object> scenario(@PathVariable String name) {
        requireEnabled();
        List<Employee> reporters = users.findAll().stream().filter(u -> u instanceof Employee).map(u -> (Employee) u)
                .toList();
        if (reporters.size() < 4) {
            throw new InvalidInputException("Need at least 4 employee accounts for scenarios");
        }
        List<String> created = new ArrayList<>();
        switch (name) {
            case "core-switch-b" -> {
                String[][] reports = {
                        {"WIFI-GAUTAM", "Wi-Fi not working in Gautam Bhavan", "No internet on the 2nd floor of Gautam since 10 minutes, the SSID shows but no connection"},
                        {"WIFI-KRISHNA", "Krishna hostel internet down", "Whole wing has no network, cannot connect to wifi at all"},
                        {"WIFI-VYAS", "Vyas Bhavan wifi disconnected", "Wifi keeps disconnecting and now nothing loads, ping fails"},
                        {"WIFI-MEERA", "Meera Bhavan no internet", "Internet is down in Meera, many girls in the corridor have the same problem"},
                };
                for (int k = 0; k < reports.length; k++) {
                    created.add(report(reporters.get(k % reporters.size()), reports[k][0], reports[k][1], reports[k][2],
                            Level.HIGH, Level.HIGH, 120));
                }
            }
            case "payment-down" -> {
                String[][] reports = {
                        {"Payment application unavailable", "The fee payment portal shows an error page and nobody can pay the semester fee"},
                        {"Payment transaction failing", "My UPI payment for mess fee failed twice on the portal, money not debited"},
                        {"Unable to make payment", "Unable to make payment for hostel fee, the page times out after OTP"},
                };
                for (int k = 0; k < reports.length; k++) {
                    created.add(report(reporters.get(k % reporters.size()), "FEE-PORTAL", reports[k][0], reports[k][1],
                            Level.HIGH, Level.CRITICAL, 300));
                }
            }
            case "fest-load" -> {
                // the real Atmos complaint: academic-block internet collapses during the fest
                String[][] reports = {
                        {"LAN-ACAD", "Acad block internet dead during fest", "No internet anywhere in C Block, pages time out, we cannot run the fest registration desk"},
                        {"WIFI-LIB", "Library Wi-Fi not loading anything", "Library wifi connects but nothing loads since the fest started, ping fails"},
                        {"LAN-ACAD", "LTC network down for the event stream", "The lecture theatre network is down and the event live stream keeps failing, no internet"},
                        {"LAB-NET", "D+F Block lab has no internet", "All lab PCs in D+F Block lost internet connection during the fest evening"},
                };
                for (int k = 0; k < reports.length; k++) {
                    created.add(report(reporters.get(k % reporters.size()), reports[k][0], reports[k][1], reports[k][2],
                            Level.HIGH, Level.HIGH, 250));
                }
            }
            default -> throw new InvalidInputException("Unknown scenario " + name
                    + " (core-switch-b, payment-down, fest-load)");
        }
        return Map.of("scenario", name, "created", created);
    }

    private String report(User reporter, String serviceCode, String title, String description, Level impact,
                          Level urgency, int users) {
        ItService service = services.findByCode(serviceCode)
                .orElseThrow(() -> new InvalidInputException("Service " + serviceCode + " is not in the catalogue"));
        Incident i = incidents.report(new IncidentService.ReportCommand(title, description, service.getId(), null, null,
                impact, urgency, users, null, Incident.Channel.SIMULATION, true), reporter);
        return i.getNumber();
    }

    private void requireEnabled() {
        if (!enabled) {
            throw new UnauthorizedOperationException("Demo controls are switched off on this server");
        }
    }
}
