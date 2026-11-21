package in.bits.campusops.catalog;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import java.util.Arrays;
import java.util.List;

/**
 * Categories are data, not an enum, so an admin can add "Printing" or "AV Equipment"
 * at runtime without touching code. Keywords drive automatic categorisation.
 */
@Entity
public class IncidentCategory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 32)
    private String code;

    @Column(nullable = false)
    private String name;

    private String description;

    /** Comma-separated words that suggest this category, e.g. "wifi,router,lan,ping". */
    @Column(length = 1000)
    private String keywords;

    private String color;
    private boolean active = true;

    protected IncidentCategory() {
    }

    public IncidentCategory(String code, String name, String description, String keywords, String color) {
        this.code = code.toUpperCase();
        this.name = name;
        this.description = description;
        this.keywords = keywords;
        this.color = color;
    }

    public List<String> keywordList() {
        if (keywords == null || keywords.isBlank()) {
            return List.of();
        }
        return Arrays.stream(keywords.split(",")).map(String::trim).filter(s -> !s.isEmpty())
                .map(String::toLowerCase).toList();
    }

    public void update(String name, String description, String keywords, String color, boolean active) {
        this.name = name;
        this.description = description;
        this.keywords = keywords;
        this.color = color;
        this.active = active;
    }

    public Long getId() {
        return id;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public String getKeywords() {
        return keywords;
    }

    public String getColor() {
        return color;
    }

    public boolean isActive() {
        return active;
    }
}
