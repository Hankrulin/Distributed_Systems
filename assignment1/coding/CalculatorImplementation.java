import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;

/**
 * Server-side implementation for the Calculator RMI service.
 * 
 * Design notes:
 * - Uses Deque<Integer> as the stack (ArrayDeque for performance).
 * - All public methods are synchronized to make the shared stack thread-safe.
 * - pushOperation pops ALL current values, computes one result (min/max/lcm/gcd), then pushes it back.
 * - delayPop sleeps OUTSIDE the lock, then pops INSIDE the lock to avoid blocking other calls while waiting.
 */
public class CalculatorImplementation extends UnicastRemoteObject implements Calculator {

    // Shared stack across ALL clients (as per base requirement).
    private final Deque<Integer> stack = new ArrayDeque<>();

    public CalculatorImplementation() throws RemoteException {
        super(); // export this object for RMI
    }

    // ------------------------
    // Remote methods (API)
    // ------------------------

    @Override
    public synchronized void pushValue(int val) throws RemoteException {
        stack.push(val);
    }

    @Override
    public void pushOperation(String operator) throws RemoteException {
        // Validate operator early (no lock held yet)
        final String op = (operator == null) ? "" : operator.trim().toLowerCase();
        if (!(op.equals("min") || op.equals("max") || op.equals("gcd") || op.equals("lcm"))) {
            throw new RemoteException("Invalid operator: " + operator + " (allowed: min|max|gcd|lcm)");
        }

        // We want the entire "pop all, compute, push result" to be atomic.
        // So we synchronize for the critical section.
        final List<Integer> values = new ArrayList<>();
        int result;

        synchronized (this) {
            if (stack.isEmpty()) {
                throw new RemoteException("Stack is empty; cannot apply operation: " + op);
            }

            // Pop ALL current values
            while (!stack.isEmpty()) {
                values.add(stack.pop());
            }
        }

        // Compute outside the synchronized block (no need to hold the lock while computing).
        // Note: 'values' currently holds numbers in LIFO order due to pops;
        // for min/max/gcd/lcm the order doesn't matter.
        switch (op) {
            case "min":
                result = Collections.min(values);
                break;
            case "max":
                result = Collections.max(values);
                break;
            case "gcd":
                result = gcdAll(values);
                break;
            case "lcm":
                result = lcmAll(values);
                break;
            default:
                // This should never happen because we validated the operator above.
                throw new RemoteException("Unknown operator: " + op);
        }

        // Push result back atomically
        synchronized (this) {
            stack.push(result);
        }
    }

    @Override
    public synchronized int pop() throws RemoteException {
        if (stack.isEmpty()) {
            throw new RemoteException("Cannot pop: stack is empty.");
        }
        return stack.pop();
    }

    @Override
    public synchronized boolean isEmpty() throws RemoteException {
        return stack.isEmpty();
    }

    @Override
    public int delayPop(int millis) throws RemoteException {
        // Be lenient: treat negative as 0
        if (millis < 0) millis = 0;

        try {
            // Sleep OUTSIDE the lock so we don't block other calls while waiting.
            Thread.sleep(millis);
        } catch (InterruptedException ie) {
            // Restore interrupt status and report
            Thread.currentThread().interrupt();
            throw new RemoteException("delayPop interrupted while waiting.", ie);
        }

        // After waking up, actually pop under synchronization
        synchronized (this) {
            if (stack.isEmpty()) {
                // Race condition case: someone else popped the last value while we were sleeping
                throw new RemoteException("delayPop: stack became empty after waiting.");
            }
            return stack.pop();
        }
    }

    // create the Helper fuctions

    /** Compute gcd for two integers (handles negatives). */
    private static int gcd(int a, int b) {
        a = Math.abs(a);
        b = Math.abs(b);
        // Standard Euclidean algorithm
        while (b != 0) {
            int t = a % b;
            a = b;
            b = t;
        }
        return a; // gcd >= 0
    }

    /** Compute lcm for two integers (handles zeros & negatives). */
    private static int lcm(int a, int b) {
        a = Math.abs(a);
        b = Math.abs(b);
        if (a == 0 || b == 0) return 0;     // define lcm(0, x) = 0
        return (a / gcd(a, b)) * b;         // avoid overflow: divide before multiply
    }

    /** gcd for a list of integers; assumes values is non-empty. */
    private static int gcdAll(List<Integer> values) {
        int g = values.get(0);
        for (int i = 1; i < values.size(); i++) {
            g = gcd(g, values.get(i));
            if (g == 1) break; // early exit, cannot get smaller than 1
        }
        return g;
    }

    /** lcm for a list of integers; assumes values is non-empty. */
    private static int lcmAll(List<Integer> values) {
        int current = values.get(0);
        for (int i = 1; i < values.size(); i++) {
            current = lcm(current, values.get(i));
            if (current == 0) break; // early exit
        }
        return current;
    }
}