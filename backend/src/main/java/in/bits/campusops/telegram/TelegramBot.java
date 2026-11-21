package in.bits.campusops.telegram;

import in.bits.campusops.catalog.CatalogRepositories.ServiceRepository;
import in.bits.campusops.catalog.ItService;
import in.bits.campusops.common.CampusOpsException;
import in.bits.campusops.incident.Incident;
import in.bits.campusops.incident.IncidentService;
import in.bits.campusops.problem.OutageMapService;
import in.bits.campusops.user.User;
import in.bits.campusops.user.UserRepository;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Chat-based reporting over Telegram. Commands:
 * /start CODE (link account), /services, /report SERVICE-CODE description, /metoo INC-1001,
 * /status INC-1001, /mine. Known outages are shown before a report is filed, to stop duplicates.
 */
@Component
public class TelegramBot {

    private static final Logger log = LoggerFactory.getLogger(TelegramBot.class);

    private final TelegramAdapter telegram;
    private final UserRepository users;
    private final ServiceRepository services;
    private final IncidentService incidents;
    private final OutageMapService outages;
    private final TransactionTemplate tx;
    private long offset = 0;

    public TelegramBot(TelegramAdapter telegram, UserRepository users, ServiceRepository services,
                       IncidentService incidents, OutageMapService outages, PlatformTransactionManager txManager) {
        this.telegram = telegram;
        this.users = users;
        this.services = services;
        this.incidents = incidents;
        this.outages = outages;
        this.tx = new TransactionTemplate(txManager);
    }

    @Scheduled(fixedDelay = 3000, initialDelay = 5000)
    public void poll() {
        if (!telegram.isEnabled()) {
            return;
        }
        for (TelegramAdapter.Update u : telegram.getUpdates(offset)) {
            offset = Math.max(offset, u.updateId() + 1);
            if (u.chatId() == null || u.text() == null) {
                continue;
            }
            try {
                String reply = tx.execute(status -> handle(u.chatId(), u.text().trim()));
                telegram.sendMessage(u.chatId(), reply);
            } catch (CampusOpsException ex) {
                telegram.sendMessage(u.chatId(), "⚠️ " + ex.getMessage());
            } catch (RuntimeException ex) {
                log.warn("Telegram command failed", ex);
                telegram.sendMessage(u.chatId(), "Something went wrong. Please try again or use the website.");
            }
        }
    }

    String handle(String chatId, String text) {
        String[] parts = text.split("\\s+", 3);
        String command = parts[0].toLowerCase().replaceAll("@.*", "");
        Optional<User> linked = users.findByTelegramChatId(chatId);
        switch (command) {
            case "/start":
                if (parts.length > 1) {
                    return link(chatId, parts[1]);
                }
                return linked.map(user -> "Hi " + user.getFullName() + "! " + help())
                        .orElse("Welcome to CampusOps. Open your profile on the website, tap 'Link Telegram' and send "
                                + "/start followed by the 6-digit code.");
            case "/services":
                return "Service codes:\n" + services.findAllByOrderByName().stream()
                        .map(s -> s.getCode() + " - " + s.getName()).collect(Collectors.joining("\n"));
            case "/report": {
                User user = linked.orElse(null);
                if (user == null) {
                    return "Link your account first: /start CODE";
                }
                if (parts.length < 3) {
                    return knownOutagesText() + "Usage: /report SERVICE-CODE what is wrong\n"
                            + "Example: /report WIFI-GAUTAM no internet on 2nd floor";
                }
                ItService service = services.findByCode(parts[1].toUpperCase()).orElse(null);
                if (service == null) {
                    return "Unknown service " + parts[1] + ". Send /services for the list.";
                }
                String description = parts[2];
                if (description.toLowerCase().startsWith("anyway ")) {
                    description = description.substring(7).trim();
                } else {
                    var known = knownOutageFor(service);
                    if (known.isPresent() && known.get().followIncidentNumber() != null) {
                        var p = known.get();
                        return "⚠️ Known outage: " + p.title() + " (" + p.number() + ", " + p.incidents()
                                + " reports). Technicians are already on it.\n"
                                + "Send /metoo " + p.followIncidentNumber() + " to be added and get updates.\n"
                                + "If yours is a different problem: /report " + service.getCode() + " anyway "
                                + description;
                    }
                }
                String title = description.length() > 60 ? description.substring(0, 57) + "..." : description;
                Incident i = incidents.report(new IncidentService.ReportCommand(title, description, service.getId(),
                        null, null, null, null, 1, null, Incident.Channel.TELEGRAM, true), user);
                return "Logged " + i.getNumber() + " (" + i.getPriority() + ", " + i.getCategory().getName() + ")"
                        + (i.getAssignee() == null ? ". Waiting for a technician." : ". Assigned to " + i.getAssignee().getFullName() + ".");
            }
            case "/metoo": {
                User user = linked.orElse(null);
                if (user == null) {
                    return "Link your account first: /start CODE";
                }
                if (parts.length < 2) {
                    return "Usage: /metoo INC-1001";
                }
                Incident i = incidents.meToo(incidents.getByNumber(parts[1]).getId(), user);
                return "Added you to " + i.getNumber() + " (" + i.getSupporterCount() + " people have said me too). "
                        + "You will get an update here when it is fixed.";
            }
            case "/status": {
                if (parts.length < 2) {
                    return "Usage: /status INC-1001";
                }
                User user = linked.orElse(null);
                if (user == null) {
                    return "Link your account first: /start CODE";
                }
                Incident i = incidents.getByNumber(parts[1]);
                if (!user.isStaff() && !i.isReportedBy(user)) {
                    return "You can only check incidents you reported.";
                }
                return i.getNumber() + ": " + i.getTitle() + "\nStatus: " + i.getStatus() + "\nPriority: "
                        + i.getPriority() + "\nTechnician: "
                        + (i.getAssignee() == null ? "not assigned yet" : i.getAssignee().getFullName());
            }
            case "/mine": {
                User user = linked.orElse(null);
                if (user == null) {
                    return "Link your account first: /start CODE";
                }
                List<Incident> mine = user.isStaff() ? incidents.assignedTo(user) : incidents.reportedBy(user);
                if (mine.isEmpty()) {
                    return "Nothing open. 🎉";
                }
                return mine.stream().limit(10).map(i -> i.getNumber() + " " + i.getStatus() + " - " + i.getTitle())
                        .collect(Collectors.joining("\n"));
            }
            default:
                return help();
        }
    }

    private String link(String chatId, String code) {
        return users.findByTelegramLinkCode(code.trim())
                .map(user -> {
                    user.linkTelegram(chatId);
                    return "Linked to " + user.getFullName() + " (" + user.getCode() + "). You will get CampusOps "
                            + "updates here. " + help();
                })
                .orElse("That code is not valid. Generate a new one on your profile page.");
    }

    private Optional<OutageMapService.ProblemSummary> knownOutageFor(ItService service) {
        return outages.build().problems().stream()
                .filter(p -> p.affectedServiceIds().contains(service.getId()))
                .findFirst();
    }

    private String knownOutagesText() {
        var open = outages.build().problems();
        if (open.isEmpty()) {
            return "";
        }
        return "Known outages right now:\n" + open.stream()
                .map(p -> "• " + p.title() + " (" + p.incidents() + " reports)"
                        + (p.followIncidentNumber() == null ? "" : ": /metoo " + p.followIncidentNumber()))
                .collect(Collectors.joining("\n")) + "\n\n";
    }

    private static String help() {
        return "Commands:\n/report SERVICE-CODE what is wrong\n/metoo INC-1001\n/status INC-1001\n/mine\n/services";
    }
}
