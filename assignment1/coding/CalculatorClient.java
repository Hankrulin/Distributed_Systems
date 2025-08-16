import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;

public class CalculatorClient {
    public static void main(String[] args) {
        try {
            // Link to the RMI Registry (If the server and client in the same computer, use the localhost; port:1099 )
            Registry registry = LocateRegistry.getRegistry("localhost", 1099);

            // 2. Get the remote object's "proxy" (stub) by registering the name
            // This name should be the same as the name you rebind in CalculatorServer.java (e.g. "CalculatorService").
            Calculator calc = (Calculator) registry.lookup("CalculatorService");

            // 3. The five remote methods for the operations
            // 3.1 pushValue：push the value into stack
            calc.pushValue(8);
            calc.pushValue(12);
            calc.pushValue(20);

            // 3.2 pushOperation：Pop up all current values and calculate, then push the results back
            calc.pushOperation("min"); // gcd(8,12,20) = 4
            
            // 3.3 isEmpty：Check if the stack is empty
            System.out.println("isEmpty? " + calc.isEmpty()); // Expected false

            // 3.4 pop：Take the top value
            int top = calc.pop(); // 預期 4
            System.out.println("pop() = " + top);

            // 3.5 delayPop：Wait and then pop (fill in a few numbers first)
            calc.pushValue(5);
            calc.pushValue(7);
            System.out.println("Calling delayPop(1000) ... (wait ~1s)");
            int delayed = calc.delayPop(1000); // wait for 1 sec and pop -> Expected 7
            System.out.println("delayPop(1000) = " + delayed);

            // Finally, check if there is anything left on the stack (there may be 5 at this point)
            System.out.println("isEmpty (end)? " + calc.isEmpty());

        } catch (Exception e) {
            // Any remote errors will be reported here.
            System.err.println("Client error: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
