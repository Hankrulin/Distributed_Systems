import java.rmi.RemoteException; // Added import for RemoteException
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;

public class MultiClientTest {
    static class ClientTask implements Runnable {
        private final String clientId;
        private final int[] valuesToPush;
        private final String operation;
        private final int delayMillis;

        public ClientTask(String clientId, int[] valuesToPush, String operation, int delayMillis) {
            this.clientId = clientId;
            this.valuesToPush = valuesToPush;
            this.operation = operation;
            this.delayMillis = delayMillis;
        }

        @Override
        public void run() {
            try {
                Registry registry = LocateRegistry.getRegistry("localhost", 1099);
                Calculator calc = (Calculator) registry.lookup("CalculatorService");

                // Push values
                for (int val : valuesToPush) {
                    System.out.println(clientId + ": Pushing " + val);
                    calc.pushValue(val);
                }

                // Apply operation
                System.out.println(clientId + ": Applying operation " + operation);
                calc.pushOperation(operation);

                // Check if stack is empty
                System.out.println(clientId + ": isEmpty? " + calc.isEmpty());

                // Pop top value
                try {
                    int top = calc.pop();
                    System.out.println(clientId + ": pop() = " + top);
                } catch (RemoteException e) {
                    System.err.println(clientId + ": Pop failed: " + e.getMessage());
                }

                // Push more values and perform delayPop
                calc.pushValue(5);
                calc.pushValue(7);
                System.out.println(clientId + ": Calling delayPop(" + delayMillis + ") ...");
                int delayed = calc.delayPop(delayMillis);
                System.out.println(clientId + ": delayPop(" + delayMillis + ") = " + delayed);

                // Final empty check
                System.out.println(clientId + ": isEmpty (end)? " + calc.isEmpty());

            } catch (Exception e) {
                System.err.println(clientId + ": Error: " + e.getMessage());
                e.printStackTrace();
            }
        }
    }

    public static void main(String[] args) {
        // Define client tasks with different behaviors
        ClientTask[] tasks = {
            new ClientTask("Client-1", new int[]{10, 15, 25}, "max", 500),
            new ClientTask("Client-2", new int[]{3, 6, 9}, "gcd", 1000),
            new ClientTask("Client-3", new int[]{4, 8, 12}, "lcm", 1500),
            new ClientTask("Client-4", new int[]{7, 14, 21}, "min", 2000)
        };

        // Start threads for each client
        Thread[] threads = new Thread[tasks.length];
        for (int i = 0; i < tasks.length; i++) {
            threads[i] = new Thread(tasks[i]);
            threads[i].start();
        }

        // Wait for all threads to complete
        for (Thread thread : threads) {
            try {
                thread.join();
            } catch (InterruptedException e) {
                System.err.println("Main thread interrupted: " + e.getMessage());
            }
        }

        System.out.println("All clients completed.");
    }
}