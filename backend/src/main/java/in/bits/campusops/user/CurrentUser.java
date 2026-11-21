package in.bits.campusops.user;

import in.bits.campusops.common.Exceptions.UnauthorizedOperationException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

/** Resolves the logged-in user from the JWT on the current request. */
@Component
public class CurrentUser {

    private final UserRepository users;

    public CurrentUser(UserRepository users) {
        this.users = users;
    }

    public Long id() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof Jwt jwt)) {
            throw new UnauthorizedOperationException("You must be logged in");
        }
        return Long.valueOf(jwt.getSubject());
    }

    public User get() {
        return users.findById(id())
                .filter(User::isActive)
                .orElseThrow(() -> new UnauthorizedOperationException("Your account is not active"));
    }
}
