import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.UUID;

public class TestTTLExpiration {
    static String BASE = System.getProperty("base", "http://localhost:4567");

    public static void main(String[] args) throws Exception {
        HttpClient c = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
        String sid = "ttl-" + UUID.randomUUID().toString().substring(0,6);
        String sender = "cs-" + UUID.randomUUID().toString().substring(0,6);

        // PUT once
        String body = "{\"id\":\"" + sid + "\",\"v\":1}";
        HttpRequest put = HttpRequest.newBuilder(URI.create(BASE + "/weather.json"))
                .header("Content-Type", "application/json")
                .header("X-Lamport-TS", "200")
                .header("X-Sender-ID", sender)
                .PUT(HttpRequest.BodyPublishers.ofString(body))
                .build();
        HttpResponse<String> pr = c.send(put, HttpResponse.BodyHandlers.ofString());
        System.out.println("PUT status: " + pr.statusCode());

        // Immediately GET: should contain id
        HttpRequest get1 = HttpRequest.newBuilder(URI.create(BASE + "/weather.json"))
                .header("X-Lamport-TS", "201").GET().build();
        HttpResponse<String> g1 = c.send(get1, HttpResponse.BodyHandlers.ofString());
        if (g1.statusCode() != 200 || !g1.body().contains("\"" + sid + "\"")) {
            fail("Fresh GET should contain " + sid, g1);
        }
        System.out.println("Waiting ~35s for TTL sweeper...");
        Thread.sleep(35_000);

        // GET again: should NOT contain id (cleaned)
        HttpRequest get2 = HttpRequest.newBuilder(URI.create(BASE + "/weather.json"))
                .header("X-Lamport-TS", "202").GET().build();
        HttpResponse<String> g2 = c.send(get2, HttpResponse.BodyHandlers.ofString());
        if (g2.statusCode() != 200) fail("GET expected 200", g2);
        if (g2.body().contains("\"" + sid + "\"")) {
            fail("After TTL, aggregate should NOT contain " + sid, g2);
        }
        System.out.println("PASS: TTL expiration cleaned expired source data.");
    }

    static void fail(String msg, HttpResponse<String> res) {
        System.out.println("FAIL: " + msg + " (status " + res.statusCode() + ") body=" + res.body());
        System.exit(1);
    }
}
