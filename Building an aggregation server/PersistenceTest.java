import java.nio.charset.StandardCharsets;
import java.nio.file.*;

public class PersistenceTest {
    public static void main(String[] args) throws Exception {
        Path tmpDir = Files.createTempDirectory("persist-test");
        Path data = tmpDir.resolve("aggregate.json");
        Path wal  = tmpDir.resolve("aggregate.wal");

        Persistence p = new Persistence(data.toString(), wal.toString());

        // === Test walAppend + commit ===
        p.walAppend("{\"id\":\"X\",\"v\":1}");
        p.walAppend("{\"id\":\"Y\",\"v\":2}");
        p.commit(() -> "{\"X\":{\"id\":\"X\",\"v\":1},\"Y\":{\"id\":\"Y\",\"v\":2}}");

        String agg = Files.readString(data, StandardCharsets.UTF_8);
        check(agg.contains("\"X\"") && agg.contains("\"Y\""), "Commit should write both X and Y");
        check(Files.size(wal) == 0, "WAL should be truncated after commit");

        // === Test recover ===
        // 模擬舊 data + WAL 有新 entry
        Files.writeString(data, "{\"A\":{\"id\":\"A\",\"v\":1}}", StandardCharsets.UTF_8);
        Files.writeString(wal,
                "{\"id\":\"B\",\"v\":2}\n{\"id\":\"A\",\"v\":3}\n",
                StandardCharsets.UTF_8);

        p.recover();
        String recovered = Files.readString(data, StandardCharsets.UTF_8);
        check(recovered.contains("\"A\""), "Aggregate should contain A after recovery");
        check(recovered.contains("\"B\""), "Aggregate should contain B after recovery");

        System.out.println("PersistenceTest passed ✅");
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError("Test failed: " + message);
        }
    }
}
