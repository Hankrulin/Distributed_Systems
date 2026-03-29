import java.net.URI;
import java.net.http.*;
import java.time.Duration;

public class TestBadMethod {
    static String BASE = System.getProperty("base", "http://localhost:4567");
    public static void main(String[] args) throws Exception {
        HttpClient c = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
        HttpRequest req = HttpRequest.newBuilder(URI.create(BASE + "/weather.json"))
                .method("POST", HttpRequest.BodyPublishers.noBody())
                .header("X-Lamport-TS", "2")
                .build();
        HttpResponse<String> res = c.send(req, HttpResponse.BodyHandlers.ofString());
        System.out.println("Status: " + res.statusCode());
        if (res.statusCode() == 400) System.out.println("PASS: non-GET/PUT returned 400.");
        else {
            System.out.println("FAIL: expected 400, got " + res.statusCode());
            System.exit(1);
        }
    }
}
