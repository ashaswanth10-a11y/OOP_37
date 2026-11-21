package in.bits.campusops.user;

import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorColumn;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Inheritance;
import jakarta.persistence.InheritanceType;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.EnumSet;
import java.util.Set;

/**
 * Every person who uses CampusOps. Concrete kinds (employee, technician, admin) are
 * real "is-a" specialisations that add their own data and behaviour.
 */
@Entity
@Table(name = "users")
@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
@DiscriminatorColumn(name = "user_type")
public abstract class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 12)
    private String code;

    @Column(nullable = false)
    private String fullName;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false)
    private String passwordHash;

    private String phone;
    private String telegramChatId;
    private String telegramLinkCode;
    private boolean emailNotifications = true;
    private boolean telegramNotifications = true;
    private boolean active = true;

    @Column(nullable = false)
    private Instant createdAt;

    protected User() {
    }

    protected User(String code, String fullName, String email, String passwordHash, Instant createdAt) {
        this.code = code;
        this.fullName = fullName;
        this.email = email.toLowerCase();
        this.passwordHash = passwordHash;
        this.createdAt = createdAt;
    }

    /** The primary role of this kind of user. */
    public abstract Role getRole();

    /** All roles this user acts with. Subclasses widen this (a team lead is also a technician). */
    public Set<Role> grantedRoles() {
        return EnumSet.of(getRole());
    }

    public boolean hasRole(Role role) {
        return grantedRoles().contains(role);
    }

    public boolean isStaff() {
        return getRole().tier() >= Role.TECHNICIAN.tier();
    }

    public void rename(String fullName) {
        this.fullName = fullName;
    }

    public void changePassword(String newHash) {
        this.passwordHash = newHash;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public void updateNotificationPreferences(boolean email, boolean telegram) {
        this.emailNotifications = email;
        this.telegramNotifications = telegram;
    }

    public void issueTelegramLinkCode(String linkCode) {
        this.telegramLinkCode = linkCode;
    }

    public void linkTelegram(String chatId) {
        this.telegramChatId = chatId;
        this.telegramLinkCode = null;
    }

    public void unlinkTelegram() {
        this.telegramChatId = null;
    }

    public void deactivate() {
        this.active = false;
    }

    public void activate() {
        this.active = true;
    }

    public Long getId() {
        return id;
    }

    public String getCode() {
        return code;
    }

    public String getFullName() {
        return fullName;
    }

    public String getEmail() {
        return email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public String getPhone() {
        return phone;
    }

    public String getTelegramChatId() {
        return telegramChatId;
    }

    public String getTelegramLinkCode() {
        return telegramLinkCode;
    }

    public boolean isTelegramLinked() {
        return telegramChatId != null;
    }

    public boolean isEmailNotifications() {
        return emailNotifications;
    }

    public boolean isTelegramNotifications() {
        return telegramNotifications;
    }

    public boolean isActive() {
        return active;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    @Override
    public String toString() {
        return getRole().label() + " " + code + " (" + fullName + ")";
    }
}
