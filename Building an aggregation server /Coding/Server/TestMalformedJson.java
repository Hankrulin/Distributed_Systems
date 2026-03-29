import java.net.URI;
import java.net.http.*;
import java.time.Duration;

public class TestMalformedJson {
    static String BASE = System.getProperty("base", "http://localhost:4567");
    public static void main(String[] args) throws Exception {
        HttpClient c = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();

        // Not JSON
        HttpRequest r1 = HttpRequest.newBuilder(URI.create(BASE + "/weather.json"))
                .header("Content-Type", "application/json")
                .header("X-Lamport-TS", "300")
                .header("X-Sender-ID", "cs-bad")
                .PUT(HttpRequest.BodyPublishers.ofString("not json"))
                .build();
        HttpResponse<String> a = c.send(r1, HttpResponse.BodyHandlers.ofString());
        if (a.statusCode() != 500) fail("non-JSON should be 500", a);

        // Missing id
        HttpRequest r2 = HttpRequest.newBuilder(URI.create(BASE + "/weather.json"))
                .header("Content-Type", "application/json")
                .header("X-Lamport-TS", "301")
                .header("X-Sender-ID", "cs-bad")
                .PUT(HttpRequest.BodyPublishers.ofString("{\"sid\":\"X\"}"))
                .build();
        HttpResponse<String> b = c.send(r2, HttpResponse.BodyHandlers.ofString());
        if (b.statusCode() != 500) fail("missing id should be 500", b);

        System.out.println("PASS: malformed JSON and missing id correctly return 500.");
    }
    static void fail(String msg, HttpResponse<String> res) {
        System.out.println("FAIL: " + msg + " (got " + res.statusCode() + ") body=" + res.body());
        System.exit(1);
    }
}
