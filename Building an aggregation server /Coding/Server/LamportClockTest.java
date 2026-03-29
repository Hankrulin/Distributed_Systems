public class LamportClockTest {
    public static void main(String[] args) {
        LamportClock lc = new LamportClock();

        check(lc.onSend() == 1, "onSend from 0 should be 1");
        check(lc.onSend() == 2, "onSend from 1 should be 2");
        check(lc.onReceive(4) == 5, "onReceive(4) after 2 should be 5");
        check(lc.onSend() == 6, "onSend after 5 should be 6");
        check(lc.onReceive(9) == 10, "onReceive(9) after 6 should be 10");

        System.out.println("LamportClockTest passed ✅");
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError("Test failed: " + message);
        }
    }
}
