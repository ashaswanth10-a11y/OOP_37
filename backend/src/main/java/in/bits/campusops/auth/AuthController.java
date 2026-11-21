package in.bits.campusops.auth;

import in.bits.campusops.common.Exceptions.InvalidLoginException;
import in.bits.campusops.user.CurrentUser;
import in.bits.campusops.user.Role;
import in.bits.campusops.user.User;
import in.bits.campusops.user.UserFactory;
import in.bits.campusops.user.UserRepository;
import in.bits.campusops.user.UserService;
import in.bits.campusops.user.UserView;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final UserRepository users;
    private final UserService userService;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwt;
    private final CurrentUser currentUser;

    public AuthController(UserRepository users, UserService userService, PasswordEncoder passwordEncoder,
                          JwtService jwt, CurrentUser currentUser) {
        this.users = users;
        this.userService = userService;
        this.passwordEncoder = passwordEncoder;
        this.jwt = jwt;
        this.currentUser = currentUser;
    }

    public record LoginRequest(@Email @NotBlank String email, @NotBlank String password) {
    }

    public record RegisterRequest(@NotBlank @Size(max = 80) String fullName, @Email @NotBlank String email,
                                  @NotBlank @Size(min = 8, max = 72) String password,
                                  String department, String location) {
    }

    public record AuthResponse(String token, UserView user) {
    }

    @PostMapping("/login")
    @Transactional(readOnly = true)
    public AuthResponse login(@Valid @RequestBody LoginRequest req) {
        User user = users.findByEmailIgnoreCase(req.email().trim())
                .filter(User::isActive)
                .filter(u -> passwordEncoder.matches(req.password(), u.getPasswordHash()))
                .orElseThrow(InvalidLoginException::new);
        return new AuthResponse(jwt.issue(user), UserView.of(user));
    }

    @PostMapping("/register")
    public AuthResponse register(@Valid @RequestBody RegisterRequest req) {
        User user = userService.register(new UserFactory.Spec(Role.EMPLOYEE, req.fullName().trim(), req.email(),
                req.password(), req.department(), req.location(), null, null, null, null));
        return new AuthResponse(jwt.issue(user), UserView.of(user));
    }

    @GetMapping("/me")
    @Transactional(readOnly = true)
    public UserView me() {
        return UserView.of(currentUser.get());
    }
}
