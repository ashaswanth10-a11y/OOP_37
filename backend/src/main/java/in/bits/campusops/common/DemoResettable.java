package in.bits.campusops.common;

/**
 * A component that keeps state in memory (a counter, a cache, a clock offset) and must
 * forget it when the demo data is wiped and seeded again.
 */
public interface DemoResettable {

    void resetForDemo();
}
