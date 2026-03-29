/**
 * Simple Lamport logical clock.
 * - onSend(): call before sending a message; increments local time and returns it.
 * - onReceive(remoteTs): call when receiving a message with timestamp remoteTs;
 *   updates local time = max(local, remoteTs) + 1, returns new local time.
 * - now(): current logical time (read-only).
 *
 * Thread-safe: synchronized methods.
 */
public class LamportClock {

    private long time = 0L;

    /** Called before sending a message; increments local time and returns it. */
    public synchronized long onSend() {
        time = time + 1;
        return time;
    }

    /** Called when receiving a message with remote timestamp. */
    public synchronized long onReceive(long remoteTs) {
        time = Math.max(time, remoteTs) + 1;
        return time;
    }

    /** Returns the current local Lamport time without modification. */
    public synchronized long now() {
        return time;
    }
}
