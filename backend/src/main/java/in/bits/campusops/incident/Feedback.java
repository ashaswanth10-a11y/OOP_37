package in.bits.campusops.incident;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

/** The reporter's rating after the fix. */
@Embeddable
public class Feedback {

    private Integer feedbackRating;

    @Column(length = 1000)
    private String feedbackComment;

    protected Feedback() {
    }

    public Feedback(int rating, String comment) {
        if (rating < 1 || rating > 5) {
            throw new IllegalArgumentException("Rating must be between 1 and 5");
        }
        this.feedbackRating = rating;
        this.feedbackComment = comment;
    }

    public Integer getFeedbackRating() {
        return feedbackRating;
    }

    public String getFeedbackComment() {
        return feedbackComment;
    }
}
