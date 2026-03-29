import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public class ContentServer {
    public static void main(String[] args) throws Exception {
        String url = null;
        String file = null;
        for (int i = 0; i < args.length; i++) {
            if ("-url".equals(args[i]) && i + 1 < args.length) url = args[++i];
            else if ("-f".equals(args[i]) && i + 1 < args.length) file = args[++i];
        }
        if (url == null || file == null) {
            System.err.println("Usage: java ContentServer -url host:port -f input.json");
            System.exit(2);
        }

        // 讀入檔案（你也可以先用 key:value 檔，轉成 JSON 後再送）
        String json = Files.readString(Path.of(file), StandardCharsets.UTF_8).trim();
        if (json.isBlank()) {
            System.err.println("Input file is empty.");
            System.exit(3);
        }

        LamportClock lc = new LamportClock();
        long ts = lc.onSend();
        HttpUtil.Response resp = HttpUtil.httpPut(url, "/weather.json", json, ts);

        String tsHdr = resp.headers().getOrDefault("X-Lamport-TS", "0");
        try { lc.onReceive(Long.parseLong(tsHdr)); } catch (NumberFormatException ignore) {}

        System.out.println("PUT status: " + resp.status() + " " + resp.reason());
        System.out.println("X-Lamport-TS (resp): " + tsHdr);
    }
}
