import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;

public class CalculatorServer {
    public static void main(String[] args) {
        try {
            // 1. Create a remote object instance
            Calculator calculator = new CalculatorImplementation();

            // 2. Create the RMI Registry
            LocateRegistry.createRegistry(1099);

            // 3. Binding objects to the RMI Registry
            Registry registry = LocateRegistry.getRegistry();
            registry.rebind("CalculatorService", calculator);

            System.out.println("CalculatorServer is ready.");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
