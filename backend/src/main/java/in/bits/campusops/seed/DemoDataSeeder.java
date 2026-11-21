package in.bits.campusops.seed;

import in.bits.campusops.catalog.Asset;
import in.bits.campusops.catalog.CatalogRepositories.AssetRepository;
import in.bits.campusops.catalog.CatalogRepositories.CategoryRepository;
import in.bits.campusops.catalog.CatalogRepositories.ServiceRepository;
import in.bits.campusops.catalog.CatalogRepositories.SlaPolicyRepository;
import in.bits.campusops.catalog.IncidentCategory;
import in.bits.campusops.catalog.ItService;
import in.bits.campusops.catalog.Level;
import in.bits.campusops.catalog.Priority;
import in.bits.campusops.catalog.ServiceType;
import in.bits.campusops.catalog.SlaPolicy;
import in.bits.campusops.common.TimeProvider;
import in.bits.campusops.incident.Incident;
import in.bits.campusops.incident.IncidentService;
import in.bits.campusops.incident.IncidentStatus;
import in.bits.campusops.settings.SettingsService;
import in.bits.campusops.user.Role;
import in.bits.campusops.user.Technician;
import in.bits.campusops.user.User;
import in.bits.campusops.user.UserFactory;
import in.bits.campusops.user.UserRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Seeds a realistic BITS Hyderabad campus on first start: categories, a service dependency
 * graph, QR assets, SLA policies, users for every role and two weeks of incident history
 * (driven through the real IncidentService, so the audit trail and analytics are genuine).
 */
@Component
@Order(1)
public class DemoDataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoDataSeeder.class);
    public static final String DEMO_PASSWORD = "Campus@123";
    private static final String DOMAIN = "@hyderabad.bits-pilani.ac.in";

    private final UserRepository users;
    private final UserFactory userFactory;
    private final CategoryRepository categories;
    private final ServiceRepository services;
    private final AssetRepository assets;
    private final SlaPolicyRepository slaPolicies;
    private final IncidentService incidents;
    private final SettingsService settings;
    private final TimeProvider time;
    private final boolean enabled;
    private final boolean history;

    private final Map<String, IncidentCategory> cat = new HashMap<>();
    private final Map<String, ItService> svc = new HashMap<>();
    private final Map<String, User> people = new HashMap<>();

    public DemoDataSeeder(UserRepository users, UserFactory userFactory, CategoryRepository categories,
                          ServiceRepository services, AssetRepository assets, SlaPolicyRepository slaPolicies,
                          IncidentService incidents, SettingsService settings, TimeProvider time,
                          @Value("${campusops.seed.enabled:true}") boolean enabled,
                          @Value("${campusops.seed.history:true}") boolean history) {
        this.users = users;
        this.userFactory = userFactory;
        this.categories = categories;
        this.services = services;
        this.assets = assets;
        this.slaPolicies = slaPolicies;
        this.incidents = incidents;
        this.settings = settings;
        this.time = time;
        this.enabled = enabled;
        this.history = history;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!enabled || users.count() > 0) {
            return;
        }
        seed();
    }

    /** Builds the whole demo campus into an empty database. */
    private void seed() {
        log.info("Seeding the CampusOps demo campus...");
        settings.get();
        seedCategories();
        seedServices();
        seedAssets();
        seedSla();
        seedUsers();
        if (history) {
            seedHistory();
            seedOpenIncidents();
        }
        log.info("Seed complete. Log in with any demo account, password {}", DEMO_PASSWORD);
    }

    // ---- catalogue ------------------------------------------------------------

    private void seedCategories() {
        category("NETWORK", "Network & Wi-Fi", "Wi-Fi, LAN, switches, internet access",
                "wifi,network,internet,router,switch,lan,ssid,access point,signal,ping,dns,dhcp,bandwidth,slow", "#2563eb");
        category("HARDWARE", "Hardware", "PCs, monitors, projectors, peripherals",
                "pc,monitor,keyboard,mouse,laptop,projector,power,ups,screen,boot,hdmi,cable,battery", "#64748b");
        category("SOFTWARE", "Software", "Installed software and licences",
                "install,licence,license,software,update,matlab,ansys,windows,office,antivirus", "#7c3aed");
        category("DATABASE", "Database", "Database servers and data",
                "database,db,query,replication,replica,records,data,sql,backup", "#0d9488");
        category("CLOUD", "Cloud & Servers", "Virtual machines and hosting",
                "cloud,vm,server,hosting,cluster,container,kubernetes,instance", "#0891b2");
        category("SECURITY", "Security", "Phishing, malware, suspicious activity",
                "phishing,virus,malware,hacked,suspicious,spam,breach,ransomware,scam,fraud", "#dc2626");
        category("AUTH", "Authentication", "Login, passwords, OTP, account access",
                "login,password,otp,sso,account,locked,credentials,access,reset,certificate", "#d97706");
        category("APPLICATION", "Campus Applications", "ERP, CMS, portals",
                "erp,cms,portal,moodle,timetable,registration,grades,payment,fee,quiz,upload,course", "#16a34a");
        category("STORAGE", "Storage", "Drives, quotas, file shares",
                "storage,drive,quota,disk,files,share,space", "#9333ea");
        category("PRINTING", "Printing", "Printers and scanners",
                "printer,print,toner,paper,jam,scan,scanner,cartridge", "#ea580c");
    }

    private void category(String code, String name, String description, String keywords, String color) {
        cat.put(code, categories.save(new IncidentCategory(code, name, description, keywords, color)));
    }

    private void seedServices() {
        // column x = layer (0 core -> 3 user facing), y = row; the outage map scales these
        // layer 0: what everything rests on
        service("ISP-UPLINK", "Internet Uplink (NKN + ISP)", ServiceType.NETWORK, 5, "CCIT Data Centre", 6000, "NETWORK", 0, 2);
        service("DC-CLUSTER", "Data Centre VM Cluster", ServiceType.INFRASTRUCTURE, 5, "CCIT Data Centre", 6000, "CLOUD", 0, 8);
        // layer 1: security edge and shared platforms
        service("FIREWALL", "Sophos Firewall & Web Filter", ServiceType.INFRASTRUCTURE, 5, "CCIT Data Centre", 6000, "SECURITY", 1, 2, "ISP-UPLINK");
        service("DNS-DHCP", "Campus DNS / DHCP", ServiceType.INFRASTRUCTURE, 5, "CCIT Data Centre", 6000, "NETWORK", 1, 5, "DC-CLUSTER");
        service("SSO", "Campus SSO / Identity", ServiceType.PLATFORM, 5, "CCIT Data Centre", 6000, "AUTH", 1, 8, "DC-CLUSTER");
        service("DB-CLUSTER", "Campus Database Cluster", ServiceType.INFRASTRUCTURE, 5, "CCIT Data Centre", 6000, "DATABASE", 1, 10, "DC-CLUSTER");
        // layer 2: distribution
        service("CORE-A", "Core Switch A (Academic)", ServiceType.NETWORK, 5, "B+G Block Server Room", 3000, "NETWORK", 2, 0, "FIREWALL");
        service("CORE-B", "Core Switch B (Hostels)", ServiceType.NETWORK, 5, "Hostel NOC", 3500, "NETWORK", 2, 3, "FIREWALL");
        service("CAPTIVE-PORTAL", "Internet Login (Sophos Captive Portal)", ServiceType.PLATFORM, 5, "CCIT Data Centre", 6000, "AUTH", 2, 6, "FIREWALL", "DNS-DHCP");
        service("ERP", "ERP Portal", ServiceType.APPLICATION, 5, "CCIT Data Centre", 6000, "APPLICATION", 2, 8, "DB-CLUSTER", "DC-CLUSTER", "SSO");
        service("CMS", "CMS (Moodle LMS)", ServiceType.APPLICATION, 5, "CCIT Data Centre", 6000, "APPLICATION", 2, 9, "DB-CLUSTER", "DC-CLUSTER", "SSO");
        service("SWD-PORTAL", "SWD Portal", ServiceType.APPLICATION, 3, "CCIT Data Centre", 5000, "APPLICATION", 2, 10, "DB-CLUSTER", "SSO");
        service("MAIL", "BITS Mail (Google Workspace)", ServiceType.APPLICATION, 4, "Cloud", 6000, "APPLICATION", 2, 11, "FIREWALL", "SSO");
        service("VPN", "Campus VPN", ServiceType.PLATFORM, 3, "CCIT Data Centre", 900, "AUTH", 2, 12, "FIREWALL", "SSO");
        // layer 3: access networks people actually connect to
        service("LAN-ACAD", "Academic Blocks LAN (A, B+G, C, D+F, LTC)", ServiceType.NETWORK, 4, "Academic blocks", 1500, "NETWORK", 3, 0, "CORE-A", "DNS-DHCP");
        service("WIFI-LIB", "Library Wi-Fi", ServiceType.NETWORK, 3, "Library", 800, "NETWORK", 3, 1, "CORE-A", "CAPTIVE-PORTAL");
        service("LAB-NET", "Lab Network (D+F Block)", ServiceType.NETWORK, 4, "D+F Block", 400, "NETWORK", 3, 2, "CORE-A");
        service("WIFI-GAUTAM", "Hostel Wi-Fi: Gautam Bhavan", ServiceType.NETWORK, 4, "Gautam Bhavan", 450, "NETWORK", 3, 3, "CORE-B", "CAPTIVE-PORTAL");
        service("WIFI-KRISHNA", "Hostel Wi-Fi: Krishna Bhavan", ServiceType.NETWORK, 4, "Krishna Bhavan", 450, "NETWORK", 3, 4, "CORE-B", "CAPTIVE-PORTAL");
        service("WIFI-RAM", "Hostel Wi-Fi: Ram Bhavan", ServiceType.NETWORK, 4, "Ram Bhavan", 450, "NETWORK", 3, 5, "CORE-B", "CAPTIVE-PORTAL");
        service("WIFI-VYAS", "Hostel Wi-Fi: Vyas Bhavan", ServiceType.NETWORK, 4, "Vyas Bhavan", 450, "NETWORK", 3, 6, "CORE-B", "CAPTIVE-PORTAL");
        service("WIFI-GANDHI", "Hostel Wi-Fi: Gandhi Bhavan", ServiceType.NETWORK, 4, "Gandhi Bhavan", 450, "NETWORK", 3, 7, "CORE-B", "CAPTIVE-PORTAL");
        service("WIFI-MEERA", "Hostel Wi-Fi: Meera Bhavan", ServiceType.NETWORK, 4, "Meera Bhavan", 500, "NETWORK", 3, 8, "CORE-B", "CAPTIVE-PORTAL");
        // layer 4: user-facing endpoints
        service("CLASS-AV", "Classroom AV (LTC Projectors)", ServiceType.END_USER, 3, "Lecture Theatre Complex", 1200, "HARDWARE", 4, 0, "LAN-ACAD");
        service("PRINT", "Print Station (Library)", ServiceType.END_USER, 2, "Library, Ground Floor", 600, "PRINTING", 4, 1, "LAN-ACAD");
        service("LAB-PCS", "Lab PCs (D+F Block)", ServiceType.END_USER, 3, "D+F Block Labs", 300, "HARDWARE", 4, 2, "LAB-NET");
        service("FEE-PORTAL", "Fee Payment Portal", ServiceType.APPLICATION, 5, "CCIT Data Centre", 5000, "APPLICATION", 4, 8, "ERP", "FIREWALL");
    }

    private void service(String code, String name, ServiceType type, int criticality, String location, int userBase,
                         String category, int x, int y, String... dependsOn) {
        ItService s = services.save(new ItService(code, name, null, type, criticality, location, userBase, cat.get(category),
                x, y));
        for (String up : dependsOn) {
            s.addDependency(svc.get(up));
        }
        svc.put(code, services.save(s));
    }

    private void seedAssets() {
        asset("AP-GAUTAM-2F", "Access point, 2nd floor corridor", "Gautam Bhavan, 2F", "WIFI-GAUTAM", "NETWORK");
        asset("AP-KRISHNA-3F", "Access point, 3rd floor", "Krishna Bhavan, 3F", "WIFI-KRISHNA", "NETWORK");
        asset("AP-MEERA-B1", "Access point, wing B", "Meera Bhavan, Wing B", "WIFI-MEERA", "NETWORK");
        asset("LAB-F104-PC07", "Lab PC 07", "D+F Block, Lab F104", "LAB-PCS", "HARDWARE");
        asset("LAB-F104-PC12", "Lab PC 12", "D+F Block, Lab F104", "LAB-PCS", "HARDWARE");
        asset("PRN-LIB-01", "Library printer 1 (B/W)", "Library, Ground Floor", "PRINT", "PRINTING");
        asset("PROJ-A102", "Projector, room A102", "LTC, Room 102", "CLASS-AV", "HARDWARE");
    }

    private void asset(String tag, String name, String location, String service, String category) {
        assets.save(new Asset(tag, name, location, svc.get(service), cat.get(category)));
    }

    private void seedSla() {
        slaPolicies.save(new SlaPolicy(null, Priority.P1, 5, 30));
        slaPolicies.save(new SlaPolicy(null, Priority.P2, 15, 120));
        slaPolicies.save(new SlaPolicy(null, Priority.P3, 60, 480));
        slaPolicies.save(new SlaPolicy(null, Priority.P4, 240, 1440));
        slaPolicies.save(new SlaPolicy(svc.get("FEE-PORTAL"), Priority.P1, 5, 20));
        slaPolicies.save(new SlaPolicy(svc.get("ERP"), Priority.P2, 10, 90));
    }

    // ---- people ------------------------------------------------------------------

    private void seedUsers() {
        student("aarav", "Aarav Mehta", "Computer Science", "Gautam Bhavan 214");
        student("diya", "Diya Reddy", "Electrical & Electronics", "Meera Bhavan 118");
        student("kabir", "Kabir Singh", "Mechanical", "Krishna Bhavan 305");
        student("ananya", "Ananya Iyer", "Chemical", "Meera Bhavan 220");
        student("rohan", "Rohan Varma", "Physics", "Vyas Bhavan 112");
        student("lakshmi", "Lakshmi Narayan", "Computer Science", "Meera Bhavan 216");
        student("sneha", "Sneha Kulkarni", "Economics", "Meera Bhavan 208");

        staff("ravi", Role.TECHNICIAN, "Ravi Kumar", "Network Ops", List.of("NETWORK"), 5, 6);
        staff("imran", Role.TECHNICIAN, "Imran Shaikh", "Network Ops", List.of("NETWORK", "SECURITY"), 4, 3);
        staff("priya", Role.TEAM_LEAD, "Priya Sharma", "Network Ops", List.of("NETWORK", "SECURITY", "CLOUD"), 3, 10);
        staff("arjun", Role.TECHNICIAN, "Arjun Rao", "Apps & Data", List.of("APPLICATION", "DATABASE", "AUTH"), 5, 5);
        staff("meghana", Role.TECHNICIAN, "Meghana Joshi", "Apps & Data", List.of("APPLICATION", "SOFTWARE", "CLOUD"), 4, 2);
        staff("vikram", Role.TEAM_LEAD, "Vikram Nair", "Apps & Data", List.of("APPLICATION", "DATABASE", "AUTH", "CLOUD"), 3, 12);
        staff("suresh", Role.TECHNICIAN, "Suresh Babu", "Desktop Support", List.of("HARDWARE", "PRINTING", "SOFTWARE"), 6, 8);
        staff("farah", Role.TECHNICIAN, "Farah Khan", "Desktop Support", List.of("HARDWARE", "SOFTWARE", "STORAGE"), 5, 4);
        staff("kiran", Role.SYSTEM_ADMIN, "Kiran Desai", null, null, null, null);
        staff("anil", Role.IT_MANAGER, "Dr. Anil Kapoor", null, null, null, null);
    }

    private void student(String handle, String name, String department, String location) {
        people.put(handle, users.save(userFactory.create(new UserFactory.Spec(Role.STUDENT, name, handle + DOMAIN,
                DEMO_PASSWORD, department, location, null, null, null, null))));
    }

    private void staff(String handle, Role role, String name, String team, List<String> skills, Integer max,
                       Integer years) {
        people.put(handle, users.save(userFactory.create(new UserFactory.Spec(role, name, handle + DOMAIN,
                DEMO_PASSWORD, null, null, team, skills, max, years))));
    }

    // ---- two weeks of history -------------------------------------------------------

    private record Template(String service, String category, String title, String description, Level impact,
                            Level urgency, int users, String fix, String rootCause) {
    }

    private static final List<Template> HISTORY = List.of(
            new Template("WIFI-GAUTAM", "NETWORK", "Wi-Fi slow in Gautam Bhavan", "Internet is very slow in the evenings and videos keep buffering on the hostel Wi-Fi", Level.MEDIUM, Level.MEDIUM, 40,
                    "Rebalanced channels on the 2F access points and raised the bandwidth cap for the hostel VLAN", "Access point channel congestion"),
            new Template("WIFI-KRISHNA", "NETWORK", "Access point dead on Krishna 3rd floor", "No Wi-Fi signal near rooms 301 to 320, the access point light is off", Level.MEDIUM, Level.HIGH, 25,
                    "Replaced the failed PoE injector for AP-KRISHNA-3F", "PoE injector failure"),
            new Template("LAB-PCS", "HARDWARE", "Lab PC not booting", "PC 12 in F104 shows a black screen on startup and the fans spin loudly", Level.LOW, Level.MEDIUM, 1,
                    "Reseated the RAM and replaced the CMOS battery; the PC boots normally", "Loose RAM module"),
            new Template("PRINT", "PRINTING", "Printer paper jam in library", "Library printer shows paper jam and cannot print assignments before the deadline", Level.LOW, Level.HIGH, 15,
                    "Cleared jammed sheets from tray 2 and replaced the worn pickup roller", "Worn pickup roller"),
            new Template("ERP", "APPLICATION", "ERP login failing with invalid session", "Getting an invalid session error right after login on ERP, cannot see registration", Level.HIGH, Level.HIGH, 200,
                    "Cleared the stale session cache on the ERP app servers and restarted the session service", "Session cache full"),
            new Template("CMS", "APPLICATION", "CMS course page not loading", "Moodle course page for CS F213 gives a 504 timeout during the quiz", Level.MEDIUM, Level.HIGH, 120,
                    "Increased the PHP worker pool and cleared the Moodle cache", "Worker pool exhausted during quiz"),
            new Template("MAIL", "APPLICATION", "Not receiving emails", "BITS mail is not receiving any emails since the morning", Level.MEDIUM, Level.MEDIUM, 1,
                    "Removed a mail filter that was archiving all incoming mail", "User mail filter"),
            new Template("SSO", "AUTH", "Password reset OTP not arriving", "The OTP for password reset never arrives on my phone", Level.MEDIUM, Level.HIGH, 10,
                    "SMS gateway credits were exhausted; recharged them and resent the OTPs", "SMS gateway credits exhausted"),
            new Template("VPN", "AUTH", "VPN not connecting from home", "Campus VPN client fails with an authentication error from home", Level.LOW, Level.MEDIUM, 5,
                    "Re-issued the user's VPN certificate", "Expired VPN certificate"),
            new Template("FEE-PORTAL", "APPLICATION", "Fee payment page error", "Payment page shows error 500 while paying the hostel fee", Level.HIGH, Level.CRITICAL, 300,
                    "Renewed the expired payment gateway API key and replayed the failed transactions", "Expired payment gateway key"),
            new Template("LAB-NET", "NETWORK", "No network in F-Block lab", "All PCs in F105 show no network connection", Level.HIGH, Level.HIGH, 60,
                    "Replaced a faulty uplink cable on the F-Block distribution switch", "Faulty uplink cable"),
            new Template("WIFI-LIB", "NETWORK", "Library Wi-Fi keeps disconnecting", "My laptop disconnects from the library Wi-Fi every few minutes", Level.MEDIUM, Level.MEDIUM, 30,
                    "Updated the access point firmware to fix the roaming bug", "Access point firmware bug"),
            new Template("WIFI-MEERA", "NETWORK", "Meera Bhavan Wi-Fi down in wing B", "No internet in Meera wing B for the whole corridor", Level.HIGH, Level.HIGH, 80,
                    "Restarted the wing B access switch that had hung", "Access switch hung"),
            new Template("LAB-PCS", "SOFTWARE", "MATLAB licence error in lab", "MATLAB says no licence is available on the lab PCs", Level.MEDIUM, Level.HIGH, 40,
                    "Restarted the licence server and raised the concurrent seat count", "Licence server stuck"),
            new Template("DB-CLUSTER", "DATABASE", "ERP reports showing old data", "Reports in ERP show yesterday's data; looks like database replication lag", Level.MEDIUM, Level.MEDIUM, 50,
                    "Rebuilt the read replica and tuned replication settings", "Replica lag"),
            new Template("PRINT", "PRINTING", "Printer not printing colour", "Library colour printer prints everything without cyan", Level.LOW, Level.LOW, 5,
                    "Replaced the cyan toner cartridge", "Empty toner"),
            new Template("CLASS-AV", "HARDWARE", "Projector not detecting laptop", "The projector in A102 says no signal when I connect my laptop over HDMI", Level.MEDIUM, Level.HIGH, 60,
                    "Replaced the damaged HDMI cable in A102", "Damaged HDMI cable"),
            new Template("SSO", "AUTH", "Account locked after password change", "My campus account keeps getting locked after I changed the password", Level.LOW, Level.MEDIUM, 1,
                    "Unlocked the account and removed the old saved password from the phone mail app", "Old password retried by phone"),
            new Template("WIFI-VYAS", "NETWORK", "Wi-Fi asks for login repeatedly", "The captive portal keeps asking me to sign in again every hour", Level.LOW, Level.MEDIUM, 20,
                    "Extended the captive portal session timeout from 1 hour to 12 hours", "Short portal session timeout"),
            new Template("CMS", "APPLICATION", "Assignment upload failing", "File upload to CMS fails for files over 20 MB", Level.MEDIUM, Level.HIGH, 60,
                    "Raised the upload size limit to 100 MB", "Upload size limit"),
            new Template("ERP", "APPLICATION", "Timetable not visible in ERP", "The registration page shows a blank timetable", Level.MEDIUM, Level.MEDIUM, 100,
                    "Re-published the semester timetable", "Timetable not published"),
            // the following come from real complaint patterns on the BPHC Shoutbox
            new Template("FIREWALL", "NETWORK", "YouTube lectures blocked on campus network", "NPTEL and YouTube course videos do not load on the campus LAN; the page says the site is blocked", Level.MEDIUM, Level.HIGH, 300,
                    "Whitelisted educational video domains in the Sophos web filter; the SNI filtering rule was too broad", "Over-broad web filter rule"),
            new Template("CAPTIVE-PORTAL", "AUTH", "Locked out after changing internet password", "Changed my internet password with a PDF guide from a group chat and now the captive portal rejects my login", Level.LOW, Level.HIGH, 1,
                    "Reset the captive portal account at the CCIT office (B Block) and explained the official password change process", "Password changed through an unofficial method"),
            new Template("CAPTIVE-PORTAL", "NETWORK", "Internet stops every evening", "Internet stops working around 7 pm every day, the portal says the daily data quota is exhausted", Level.LOW, Level.MEDIUM, 1,
                    "Explained the 10 GB per day policy (resets at midnight) and found a cloud backup app on the laptop using most of the quota", "Daily quota used by background sync"),
            new Template("LAN-ACAD", "NETWORK", "Internet unusable in academic block during fest", "During the fest the internet in the academic blocks is extremely slow and every page times out", Level.HIGH, Level.HIGH, 400,
                    "Added a temporary second uplink for the academic blocks and rate-limited the fest streaming VLAN", "Uplink saturated during fest"),
            new Template("CMS", "APPLICATION", "CMS app push notifications not arriving", "Not getting CMS app notifications for new course announcements for two days", Level.MEDIUM, Level.MEDIUM, 300,
                    "Fixed the push notification configuration together with the Timetable Division", "Push service misconfiguration"),
            new Template("ERP", "APPLICATION", "ERP timing out during add/swap", "ERP keeps timing out while swapping a course in the add/swap window, seats are filling up", Level.HIGH, Level.CRITICAL, 800,
                    "Scaled out the ERP application servers for the registration window and enabled a request queue", "Registration-window load spike"),
            new Template("MAIL", "SECURITY", "Suspicious email asking for password", "Got an email claiming to be from IT asking me to verify my password on a strange link", Level.HIGH, Level.HIGH, 500,
                    "Blocked the sender domain, purged the message from all inboxes and sent an awareness notice", "Phishing campaign"));

    private static final String[] REPORTERS = {"aarav", "diya", "kabir", "ananya", "rohan", "lakshmi", "sneha"};

    private void seedHistory() {
        Random rnd = new Random(42);
        Instant now = time.now();
        List<Template> sequence = new ArrayList<>(HISTORY);
        for (int k = 0; k < HISTORY.size(); k += 2) {
            sequence.add(HISTORY.get(k)); // recurring problems show up in the analytics
        }
        int n = sequence.size();
        for (int k = 0; k < n; k++) {
            Template t = sequence.get(k);
            double daysAgo = 14.0 - (13.5 * k / n) - rnd.nextDouble() * 0.4;
            Instant reportedAt = now.minus(Duration.ofMinutes((long) (daysAgo * 24 * 60)));
            User reporter = people.get(REPORTERS[rnd.nextInt(REPORTERS.length)]);
            try {
                playLifecycle(t, reporter, reportedAt, rnd);
            } catch (RuntimeException ex) {
                log.warn("Skipped a history incident ({}): {}", t.title(), ex.getMessage());
            }
        }
    }

    private void playLifecycle(Template t, User reporter, Instant at, Random rnd) {
        Incident.Channel[] channels = {Incident.Channel.WEB, Incident.Channel.WEB, Incident.Channel.VOICE};
        Incident.Channel channel = channels[rnd.nextInt(channels.length)];
        Incident i = time.at(at, () -> incidents.report(command(t, channel), reporter));
        Long id = i.getId();
        User lead = people.get("vikram");
        if (i.getAssignee() == null) {
            i = time.at(at.plusSeconds(120), () -> incidents.assign(id, lead, pickTech(t.category()).getId(), true));
        }
        User tech = i.getAssignee();
        Duration response = Duration.between(i.getSla().getSlaStart(), i.getSla().getResponseDue());
        Duration resolution = Duration.between(i.getSla().getSlaStart(), i.getSla().getResolutionDue());
        // most incidents meet their SLA; roughly one in seven is late, so analytics have something to show
        double ackFactor = rnd.nextDouble() < 0.12 ? 1.1 + rnd.nextDouble() * 0.6 : 0.15 + rnd.nextDouble() * 0.7;
        double fixFactor = rnd.nextDouble() < 0.15 ? 1.05 + rnd.nextDouble() * 0.5 : 0.2 + rnd.nextDouble() * 0.7;
        Instant ack = at.plus(scale(response, ackFactor));
        Instant fixed = at.plus(scale(resolution, fixFactor));
        if (!fixed.isAfter(ack)) {
            fixed = ack.plusSeconds(300);
        }
        Instant started = ack.plusSeconds(60);
        Instant fixedAt = fixed;
        time.at(ack, () -> incidents.acknowledge(id, tech));
        time.at(started, () -> incidents.start(id, tech));
        time.at(started.plusSeconds(120), () -> incidents.addNote(id, tech, "Checked logs and reproduced the issue.", true));
        time.at(fixedAt, () -> incidents.resolve(id, tech, t.fix(), t.rootCause(), "FIXED"));
        Instant verified = fixedAt.plus(Duration.ofMinutes(30 + rnd.nextInt(300)));
        int rating = 3 + rnd.nextInt(3);
        time.at(verified, () -> incidents.verify(id, reporter, rating, rating == 5 ? "Quick fix, thanks!" : null));
        time.at(verified.plusSeconds(600), () -> incidents.close(id, tech));
    }

    private void seedOpenIncidents() {
        Instant now = time.now();
        // queued and assigned, waiting for acknowledgement (urgent: will warn, breach and escalate during the demo)
        time.at(now.minus(Duration.ofMinutes(11)), () -> incidents.report(new IncidentService.ReportCommand(
                "Quiz submission error on CMS", "Students in CS F213 cannot submit the online quiz, the submit button spins forever",
                svc.get("CMS").getId(), null, null, Level.HIGH, Level.HIGH, 150, "Online", Incident.Channel.WEB, true),
                people.get("lakshmi")));

        // in progress with an investigation note
        Incident erp = time.at(now.minus(Duration.ofMinutes(95)), () -> incidents.report(new IncidentService.ReportCommand(
                "ERP grades page very slow", "The grades page on ERP takes more than a minute to load for everyone",
                svc.get("ERP").getId(), null, null, Level.MEDIUM, Level.MEDIUM, 80, null, Incident.Channel.WEB, true),
                people.get("rohan")));
        progress(erp, now.minus(Duration.ofMinutes(80)), "Slow query on the grades view; checking indexes with the DB team.");

        // awaiting more information from the reporter
        Incident mail = time.at(now.minus(Duration.ofMinutes(140)), () -> incidents.report(new IncidentService.ReportCommand(
                "Email attachments not downloading", "PDF attachments in BITS mail fail to download on the hostel network",
                svc.get("MAIL").getId(), null, null, Level.LOW, Level.MEDIUM, 3, null, Incident.Channel.WEB, true),
                people.get("ananya")));
        Incident mailStarted = progress(mail, now.minus(Duration.ofMinutes(120)), null);
        if (mailStarted != null && mailStarted.getStatus() == IncidentStatus.IN_PROGRESS) {
            time.at(now.minus(Duration.ofMinutes(110)), () -> incidents.requestInfo(mail.getId(), mailStarted.getAssignee(),
                    "Does this happen on mobile data too, or only on hostel Wi-Fi? Which browser are you using?"));
        }

        // reported via QR sticker, assigned
        time.at(now.minus(Duration.ofMinutes(35)), () -> incidents.report(new IncidentService.ReportCommand(
                "Keyboard not working on lab PC", "Several keys on the keyboard do not respond, cannot type the lab exam code",
                null, "LAB-F104-PC07", null, Level.LOW, Level.HIGH, 1, null, Incident.Channel.QR, true), people.get("kabir")));

        // low priority printer issue
        time.at(now.minus(Duration.ofMinutes(50)), () -> incidents.report(new IncidentService.ReportCommand(
                "Library printer offline", "The B/W printer in the library shows offline on the print queue",
                null, "PRN-LIB-01", null, Level.LOW, Level.LOW, 10, null, Incident.Channel.QR, true), people.get("diya")));

        // resolved, waiting for the reporter to confirm and rate
        Incident wifi = time.at(now.minus(Duration.ofMinutes(200)), () -> incidents.report(new IncidentService.ReportCommand(
                "Wi-Fi weak in room 305", "Wi-Fi signal is very weak inside room 305, works only near the door",
                svc.get("WIFI-KRISHNA").getId(), null, null, Level.LOW, Level.MEDIUM, 2, "Krishna Bhavan 305",
                Incident.Channel.WEB, true), people.get("kabir")));
        Incident wifiStarted = progress(wifi, now.minus(Duration.ofMinutes(180)), "Signal survey shows -78 dBm inside the room.");
        if (wifiStarted != null && wifiStarted.getStatus() == IncidentStatus.IN_PROGRESS) {
            time.at(now.minus(Duration.ofMinutes(60)), () -> incidents.resolve(wifi.getId(), wifiStarted.getAssignee(),
                    "Moved the corridor access point 3 m closer to room 305 and raised its transmit power",
                    "Access point placement", "CONFIG_CHANGE"));
        }
    }

    /** Acknowledge and start an incident at {@code at}; returns the incident or null if nobody was assigned. */
    private Incident progress(Incident i, Instant at, String note) {
        if (i.getAssignee() == null) {
            return null;
        }
        User tech = i.getAssignee();
        time.at(at, () -> incidents.acknowledge(i.getId(), tech));
        Incident started = time.at(at.plusSeconds(60), () -> incidents.start(i.getId(), tech));
        if (note != null) {
            started = time.at(at.plusSeconds(180), () -> incidents.addNote(i.getId(), tech, note, true));
        }
        return started;
    }

    private IncidentService.ReportCommand command(Template t, Incident.Channel channel) {
        return new IncidentService.ReportCommand(t.title(), t.description(), svc.get(t.service()).getId(), null,
                cat.get(t.category()).getId(), t.impact(), t.urgency(), t.users(), null, channel, true);
    }

    private Technician pickTech(String category) {
        return users.findActiveTechnicians().stream().filter(t -> t.hasSkill(category)).findFirst()
                .orElse(users.findActiveTechnicians().get(0));
    }

    private static Duration scale(Duration d, double factor) {
        return Duration.ofMillis((long) (d.toMillis() * factor));
    }
}
