package in.bits.campusops.intelligence;

import static org.assertj.core.api.Assertions.assertThat;

import in.bits.campusops.catalog.IncidentCategory;
import in.bits.campusops.incident.Fixtures;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TextIntelligenceTest {

    @Test
    @DisplayName("TC20 The brief's payment reports are recognised as similar")
    void paymentReportsAreSimilar() {
        List<String> reports = List.of("Payment application unavailable", "Payment transaction failing",
                "Unable to make payment", "Projector in A102 has no signal");
        TextAnalyzer.Corpus corpus = new TextAnalyzer.Corpus(reports);
        double ab = corpus.cosine(reports.get(0), reports.get(1));
        double ac = corpus.cosine(reports.get(0), reports.get(2));
        double ad = corpus.cosine(reports.get(0), reports.get(3));
        assertThat(ab).isGreaterThan(0.5);
        assertThat(ac).isGreaterThan(0.5);
        assertThat(ad).isEqualTo(0.0);
    }

    @Test
    @DisplayName("TC21 Synonyms fold together: 'not working', 'down' and 'unavailable' mean the same")
    void synonyms() {
        assertThat(TextAnalyzer.terms("Wi-Fi not working")).containsExactly("wifi", "fail");
        assertThat(TextAnalyzer.terms("wifi is down")).containsExactly("wifi", "fail");
    }

    @Test
    @DisplayName("TC22 Keyword classifier suggests the right category with a reason")
    void classifier() {
        IncidentCategory network = Fixtures.withId(new IncidentCategory("NETWORK", "Network", null,
                "wifi,internet,router", "#00f"), 1);
        IncidentCategory printing = Fixtures.withId(new IncidentCategory("PRINTING", "Printing", null,
                "printer,toner,jam", "#f80"), 2);
        var s = new KeywordCategoryClassifier().classify("The hostel wifi router has no internet", null,
                List.of(network, printing)).orElseThrow();
        assertThat(s.category().getCode()).isEqualTo("NETWORK");
        assertThat(s.reason()).contains("wifi");
        assertThat(s.confidence()).isEqualTo(1.0);
    }
}
