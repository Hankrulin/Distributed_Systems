import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.UUID;

public class TestPutGetFlow {
    static String BASE = System.getProperty("base", "http://localhost:4567");

    public static void main(String[] args) throws Exception {
        HttpClient c = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
        String sid = "test-" + UUID.randomUUID().toString().substring(0,8);

        String body1 = "{\"id\":\"" + sid + "\",\"temp\":18.5}";
        HttpRequest put1 = HttpRequest.newBuilder(URI.create(BASE + "/weather.json"))
                .header("Content-Type", "application/json")
                .header("X-Lamport-TS", "10")
                .header("X-Sender-ID", "cs-test")
                .PUT(HttpRequest.BodyPublishers.ofString(body1))
                .build();
        HttpResponse<String> r1 = c.send(put1, HttpResponse.BodyHandlers.ofString());
        System.out.println("PUT#1 status: " + r1.statusCode());
        if (r1.statusCode() != 200 && r1.statusCode() != 201) fail("PUT#1 expected 200 or 201", r1);

        // GET all, should contain sid
        HttpRequest get = HttpRequest.newBuilder(URI.create(BASE + "/weather.json"))
                .header("X-Lamport-TS", "11").GET().build();
        HttpResponse<String> g = c.send(get, HttpResponse.BodyHandlers.ofString());
        System.out.println("GET status: " + g.statusCode() + ", body: " + g.body());
        if (g.statusCode() != 200) fail("GET expected 200", g);
        if (!g.body().contains("\"" + sid + "\"")) fail("GET body should contain id " + sid, g);

        // PUT update -> 200
        String body2 = "{\"id\":\"" + sid + "\",\"temp\":19.0}";
        HttpRequest put2 = HttpRequest.newBuilder(URI.create(BASE + "/weather.json"))
                .header("Content-Type", "application/json")
                .header("X-Lamport-TS", "12")
                .header("X-Sender-ID", "cs-test")
                .PUT(HttpRequest.BodyPublishers.ofString(body2))
                .build();
        HttpResponse<String> r2 = c.send(put2, HttpResponse.BodyHandlers.ofString());
        System.out.println("PUT#2 status: " + r2.statusCode());
        if (r2.statusCode() != 200) fail("PUT#2 expected 200", r2);

        System.out.println("PASS: PUT/GET flow works.");
    }

    static void fail(String msg, HttpResponse<String> res) {
        System.out.println("FAIL: " + msg + " (got " + res.statusCode() + ") body=" + res.body());
        System.exit(1);
    }
}
