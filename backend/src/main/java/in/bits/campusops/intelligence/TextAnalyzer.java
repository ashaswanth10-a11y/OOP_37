package in.bits.campusops.intelligence;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Turns free text into comparable terms: lowercase, split, drop stop words, fold
 * synonyms ("unavailable", "down", "not working" -> "fail") and strip simple suffixes.
 * Also computes TF-IDF cosine similarity between texts.
 */
public final class TextAnalyzer {

    private static final Set<String> STOP = Set.of(
            "a", "an", "the", "is", "are", "was", "were", "be", "been", "am", "i", "we", "my", "our", "me", "it",
            "its", "to", "of", "in", "on", "at", "for", "from", "and", "or", "but", "with", "this", "that", "there",
            "please", "help", "hi", "hello", "sir", "maam", "since", "today", "now", "also", "very", "so", "can",
            "cannot", "can't", "cant", "could", "any", "some", "all", "has", "have", "had", "do", "does", "did",
            "get", "getting", "got", "still", "again", "just", "when", "while", "after", "before", "us", "you",
            "your", "they", "them", "he", "she", "his", "her", "as", "by", "if", "then", "than", "too", "about",
            "into", "out", "up", "what", "which", "who", "how", "being", "will", "would", "should", "make", "making");

    private static final Map<String, String> SYNONYMS = new HashMap<>();

    static {
        for (String w : List.of("unavailable", "down", "outage", "fail", "failing", "failed", "failure", "unable",
                "error", "errors", "broken", "dead", "crash", "crashed", "crashing", "notworking", "stopped",
                "unreachable", "timeout", "timing")) {
            SYNONYMS.put(w, "fail");
        }
        for (String w : List.of("slow", "lag", "lagging", "laggy", "sluggish", "latency", "buffering")) {
            SYNONYMS.put(w, "slow");
        }
        for (String w : List.of("wifi", "wi-fi", "wlan", "wireless", "hotspot", "ssid")) {
            SYNONYMS.put(w, "wifi");
        }
        for (String w : List.of("internet", "network", "lan", "ethernet", "connectivity", "connection", "connect",
                "connected", "ping")) {
            SYNONYMS.put(w, "network");
        }
        for (String w : List.of("login", "log-in", "signin", "sign-in", "password", "otp", "sso", "authenticate",
                "authentication", "credentials")) {
            SYNONYMS.put(w, "login");
        }
        for (String w : List.of("pay", "payment", "payments", "transaction", "transactions", "fee", "fees",
                "upi", "checkout")) {
            SYNONYMS.put(w, "payment");
        }
        for (String w : List.of("print", "printer", "printing", "printout", "xerox")) {
            SYNONYMS.put(w, "printer");
        }
        for (String w : List.of("pc", "computer", "desktop", "system", "machine", "workstation")) {
            SYNONYMS.put(w, "pc");
        }
        for (String w : List.of("mail", "email", "gmail", "inbox", "e-mail")) {
            SYNONYMS.put(w, "email");
        }
        for (String w : List.of("moodle", "lms", "cms")) {
            SYNONYMS.put(w, "cms");
        }
    }

    private TextAnalyzer() {
    }

    public static List<String> terms(String text) {
        List<String> out = new ArrayList<>();
        if (text == null) {
            return out;
        }
        String normalised = text.toLowerCase()
                .replace("not working", " notworking ")
                .replace("wi-fi", " wifi ")
                .replace("log in", " login ")
                .replace("sign in", " signin ");
        for (String raw : normalised.split("[^a-z0-9-]+")) {
            if (raw.length() < 2 || STOP.contains(raw)) {
                continue;
            }
            String t = SYNONYMS.getOrDefault(raw, null);
            if (t == null) {
                t = SYNONYMS.getOrDefault(stem(raw), stem(raw));
            }
            if (!STOP.contains(t)) {
                out.add(t);
            }
        }
        return out;
    }

    static String stem(String w) {
        if (w.length() > 5 && w.endsWith("ing")) {
            return w.substring(0, w.length() - 3);
        }
        if (w.length() > 4 && w.endsWith("ed")) {
            return w.substring(0, w.length() - 2);
        }
        if (w.length() > 4 && w.endsWith("es")) {
            return w.substring(0, w.length() - 2);
        }
        if (w.length() > 3 && w.endsWith("s") && !w.endsWith("ss")) {
            return w.substring(0, w.length() - 1);
        }
        return w;
    }

    /** A small TF-IDF model built over a corpus of documents, used for cosine similarity. */
    public static final class Corpus {
        private final Map<String, Integer> documentFrequency = new HashMap<>();
        private int documents;

        public Corpus(Collection<String> texts) {
            for (String text : texts) {
                add(text);
            }
        }

        public void add(String text) {
            documents++;
            for (String term : new HashSet<>(terms(text))) {
                documentFrequency.merge(term, 1, Integer::sum);
            }
        }

        private double idf(String term) {
            int df = documentFrequency.getOrDefault(term, 0);
            return Math.log((1.0 + documents) / (1.0 + df)) + 1.0;
        }

        public Map<String, Double> vector(String text) {
            Map<String, Double> tf = new HashMap<>();
            for (String term : terms(text)) {
                tf.merge(term, 1.0, Double::sum);
            }
            tf.replaceAll((term, count) -> count * idf(term));
            return tf;
        }

        public double cosine(String a, String b) {
            return cosine(vector(a), vector(b));
        }

        public static double cosine(Map<String, Double> a, Map<String, Double> b) {
            if (a.isEmpty() || b.isEmpty()) {
                return 0;
            }
            double dot = 0;
            for (Map.Entry<String, Double> e : a.entrySet()) {
                Double other = b.get(e.getKey());
                if (other != null) {
                    dot += e.getValue() * other;
                }
            }
            double na = Math.sqrt(a.values().stream().mapToDouble(v -> v * v).sum());
            double nb = Math.sqrt(b.values().stream().mapToDouble(v -> v * v).sum());
            return dot / (na * nb);
        }
    }
}
