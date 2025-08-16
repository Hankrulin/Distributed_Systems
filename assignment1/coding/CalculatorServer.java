import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;

public class CalculatorServer {
    public static void main(String[] args) {
        try {
            // 1. 建立遠端物件實例
            Calculator calculator = new CalculatorImplementation();

            // 2. 建立 RMI Registry（如果沒有啟動 rmiregistry）
            LocateRegistry.createRegistry(1099);

            // 3. 把物件綁定到 RMI Registry
            Registry registry = LocateRegistry.getRegistry();
            registry.rebind("CalculatorService", calculator);

            System.out.println("CalculatorServer is ready.");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
