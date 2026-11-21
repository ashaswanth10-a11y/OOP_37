package in.bits.campusops.incident;

import in.bits.campusops.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import java.time.Instant;

/** A note in the incident's conversation: investigation notes, questions and answers. */
@Entity
public class WorkNote {

    public enum Type { INVESTIGATION, INFO_REQUEST, INFO_RESPONSE, COMMENT, SYSTEM }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Incident incident;

    @ManyToOne(fetch = FetchType.EAGER)
    private User author;

    @Enumerated(EnumType.STRING)
    private Type type;

    @Column(length = 4000, nullable = false)
    private String text;

    /** Internal notes are visible to staff only. */
    private boolean internal;

    private Instant createdAt;

    protected WorkNote() {
    }

    WorkNote(Incident incident, User author, Type type, String text, boolean internal, Instant createdAt) {
        this.incident = incident;
        this.author = author;
        this.type = type;
        this.text = text;
        this.internal = internal;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public User getAuthor() {
        return author;
    }

    public Type getType() {
        return type;
    }

    public String getText() {
        return text;
    }

    public boolean isInternal() {
        return internal;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
