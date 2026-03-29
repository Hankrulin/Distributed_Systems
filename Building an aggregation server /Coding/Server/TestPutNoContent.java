import java.net.URI;
import java.net.http.*;
import java.time.Duration;

public class TestPutNoContent {
    static String BASE = System.getProperty("base", "http://localhost:4567");

    public static void main(String[] args) throws Exception {
        HttpClient c = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();

        HttpRequest req = HttpRequest.newBuilder(URI.create(BASE + "/weather.json"))
                .header("X-Lamport-TS", "1")
                .header("Content-Type", "application/json")
                .PUT(HttpRequest.BodyPublishers.ofString("")) // empty body
                .build();

        HttpResponse<String> res = c.send(req, HttpResponse.BodyHandlers.ofString());
        System.out.println("Status: " + res.statusCode());
        System.out.println("X-Lamport-TS: " + res.headers().firstValue("X-Lamport-TS").orElse("<missing>"));
        if (res.statusCode() == 204) {
            System.out.println("PASS: PUT with no content returned 204.");
        } else {
            System.out.println("FAIL: expected 204, got " + res.statusCode() + ". Body=" + res.body());
            System.exit(1);
        }
    }
}
