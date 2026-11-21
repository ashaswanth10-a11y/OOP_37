package in.bits.campusops.user;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Map;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService service;
    private final CurrentUser currentUser;

    public UserController(UserService service, CurrentUser currentUser) {
        this.service = service;
        this.currentUser = currentUser;
    }

    public record CreateUserRequest(@NotNull Role role, @NotBlank String fullName, @Email @NotBlank String email,
                                    @Size(min = 8) String password, String department, String location,
                                    String team, List<String> skills, Integer maxConcurrent,
                                    Integer experienceYears) {
    }

    public record TechnicianUpdate(String team, @NotNull List<String> skills, @Min(1) int maxConcurrent,
                                   @Min(0) int experienceYears, boolean available) {
    }

    public record Preferences(boolean emailNotifications, boolean telegramNotifications) {
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('SYSTEM_ADMIN','TEAM_LEAD')")
    public List<UserView> all() {
        return service.listAll();
    }

    @GetMapping("/technicians")
    @PreAuthorize("hasAnyRole('TECHNICIAN','SYSTEM_ADMIN')")
    public List<UserView> technicians() {
        return service.listTechnicians();
    }

    @PostMapping
    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    public UserView create(@Valid @RequestBody CreateUserRequest req) {
        User u = service.register(new UserFactory.Spec(req.role(), req.fullName(), req.email(), req.password(),
                req.department(), req.location(), req.team(), req.skills(), req.maxConcurrent(),
                req.experienceYears()));
        return UserView.of(u, 0);
    }

    @PutMapping("/{id}/technician")
    @PreAuthorize("hasAnyRole('SYSTEM_ADMIN','TEAM_LEAD')")
    public UserView updateTechnician(@PathVariable Long id, @Valid @RequestBody TechnicianUpdate req) {
        return UserView.of(service.updateTechnician(id, req.team(), req.skills(), req.maxConcurrent(),
                req.experienceYears(), req.available()));
    }

    @PatchMapping("/{id}/active")
    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    public UserView setActive(@PathVariable Long id, @RequestParam boolean active) {
        return UserView.of(service.setActive(id, active));
    }

    @PatchMapping("/me/availability")
    @PreAuthorize("hasRole('TECHNICIAN')")
    public UserView myAvailability(@RequestParam boolean available) {
        Technician t = service.requireTechnician(currentUser.id());
        return UserView.of(service.setAvailability(t, available));
    }

    @PutMapping("/me/preferences")
    public UserView preferences(@RequestBody Preferences req) {
        return UserView.of(service.updatePreferences(currentUser.get(), req.emailNotifications(),
                req.telegramNotifications()));
    }

    @PostMapping("/me/telegram-code")
    public Map<String, String> telegramCode() {
        return Map.of("code", service.issueTelegramLinkCode(currentUser.get()));
    }
}
