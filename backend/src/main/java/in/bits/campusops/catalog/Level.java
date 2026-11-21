package in.bits.campusops.catalog;

/** Four-step scale used for both impact and urgency. */
public enum Level {
    LOW(1), MEDIUM(2), HIGH(3), CRITICAL(4);

    private final int weight;

    Level(int weight) {
        this.weight = weight;
    }

    public int weight() {
        return weight;
    }
}
