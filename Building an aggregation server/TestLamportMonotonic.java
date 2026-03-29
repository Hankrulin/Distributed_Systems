import java.net.URI;
import java.net.http.*;
import java.time.Duration;

public class TestLamportMonotonic {
    static String BASE = System.getProperty("base", "http://localhost:4567");

    public static void main(String[] args) throws Exception {
        HttpClient c = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();

        long last = 0;
        for (int i = 0; i < 5; i++) {
            long ts = 100 + i;
            HttpRequest req = HttpRequest.newBuilder(URI.create(BASE + "/weather.json"))
                    .header("X-Lamport-TS", String.valueOf(ts))
                    .GET().build();
            HttpResponse<String> res = c.send(req, HttpResponse.BodyHandlers.ofString());
            long respTs = Long.parseLong(res.headers().firstValue("X-Lamport-TS").orElse("0"));
            System.out.println("Req ts=" + ts + " -> Resp X-Lamport-TS=" + respTs);
            if (respTs <= last) fail("Lamport should be monotonic increasing", res);
            if (respTs <= ts) fail("Resp timestamp should be > request's Lamport", res);
            last = respTs;
        }
        System.out.println("PASS: Lamport timestamps are monotonic and respect happens-before.");
    }

    static void fail(String msg, HttpResponse<String> res) {
        System.out.println("FAIL: " + msg + " (resp status " + res.statusCode() + ")");
        System.exit(1);
    }
}
