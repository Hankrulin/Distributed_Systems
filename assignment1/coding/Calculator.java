import java.rmi.Remote;
import java.rmi.RemoteException;

/**
 * Remote interface for the RMI calculator stack.
 *
 * Contract (high level):
 * - The server maintains a stack of integers.
 * - pushValue pushes one integer on top.
 * - pushOperation pops ALL current values, applies the operator, and pushes ONE result back.
 * - pop removes and returns the top value.
 * - isEmpty returns whether the stack is empty.
 * - delayPop waits for the given milliseconds, then performs pop and returns the value.
 *
 * Error model:
 * - All remote methods may throw RemoteException due to network/RMI issues.
 * - For logically invalid operations (e.g., pop on empty), the server implementation may also
 *   throw RemoteException with an explanatory message (even though the spec allows assuming
 *   “sensible operations”). Clients should use isEmpty() to avoid invalid calls.
 */

public interface Calculator extends Remote {
    void pushValue(int val) throws RemoteException; //Pushes an integer value onto the top of the stack.
    // Pop all values currently in the stack, applies the operation, and push the result back.
    void pushOperation(String operator) throws RemoteException;
    int pop() throws RemoteException; // Pop the top value from the stack and returns it.
    boolean isEmpty() throws RemoteException;  // Return true if stack is empty.
    int delayPop(int millis) throws RemoteException; //Waits millis milliseconds before performing pop().
}

